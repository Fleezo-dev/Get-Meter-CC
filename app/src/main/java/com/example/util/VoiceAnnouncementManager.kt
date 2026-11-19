package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.preferences.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Application-scoped TextToSpeech manager for GET TAXI.
 *
 * Lives independently of LocationTrackingService lifecycle so stopping the foreground service
 * on trip end never cuts off closing passenger voice announcements.
 */
object VoiceAnnouncementManager {

    private const val TAG = "VoiceAnnouncementMgr"

    const val LANG_EN = "en"
    const val LANG_TA = "ta"

    const val EN_START_MESSAGE =
        "Your trip has started. Please fasten your seatbelt and have a safe journey."
    const val TA_START_MESSAGE =
        "சீட் பெல்ட்டை அணிந்து பாதுகாப்பாகப் பயணம் செய்யுங்கள்."

    const val EN_END_MESSAGE =
        "Thank you for travelling with GET TAXI. Before leaving the vehicle, please check that you have all your belongings."
    const val TA_END_MESSAGE =
        "உங்கள் பயணத்திற்கு நன்றி. வாகனத்திலிருந்து இறங்குவதற்கு முன் உங்கள் உடைமைகளைச் சரிபார்த்துக் கொள்ளவும்."

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isInitializing = false

    @Volatile
    private var currentLangCode: String = LANG_EN

    @Volatile
    private var lastSpokenStartKey: String? = null

    @Volatile
    private var lastSpokenEndKey: String? = null

    private data class QueuedSpeech(
        val text: String,
        val langCode: String,
        val utteranceId: String
    )

    private var pendingSpeech: QueuedSpeech? = null

    fun ensureInitialized(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (tts != null || isInitializing) return
        synchronized(this) {
            if (tts != null || isInitializing) return
            isInitializing = true
            try {
                tts = TextToSpeech(ctx) { status ->
                    isInitializing = false
                    if (status == TextToSpeech.SUCCESS) {
                        isInitialized = true
                        tts?.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        Log.d(TAG, "Application TTS initialized successfully")
                        val queued = pendingSpeech
                        pendingSpeech = null
                        if (queued != null) {
                            speakNow(queued.text, queued.langCode, queued.utteranceId)
                        }
                    } else {
                        isInitialized = false
                        Log.e(TAG, "Application TTS initialization failed with status=$status")
                    }
                }
            } catch (t: Throwable) {
                isInitializing = false
                isInitialized = false
                Log.e(TAG, "Exception while initializing TextToSpeech", t)
            }
        }
    }

    fun setLanguageCode(languageCode: String) {
        currentLangCode = normalizeLanguageCode(languageCode)
    }

    fun normalizeLanguageCode(languageCode: String?): String {
        return if (languageCode?.trim()?.lowercase(Locale.US) == LANG_TA) LANG_TA else LANG_EN
    }

    fun phraseForTripStart(languageCode: String): String {
        return if (normalizeLanguageCode(languageCode) == LANG_TA) TA_START_MESSAGE else EN_START_MESSAGE
    }

    fun phraseForTripEnd(languageCode: String): String {
        return if (normalizeLanguageCode(languageCode) == LANG_TA) TA_END_MESSAGE else EN_END_MESSAGE
    }

    fun announceTripStart(context: Context, tripKey: String) {
        val ctx = context.applicationContext
        val key = tripKey.ifBlank { "active_start" }
        if (lastSpokenStartKey == key) return
        lastSpokenStartKey = key
        lastSpokenEndKey = null

        ensureInitialized(ctx)
        scope.launch {
            val repo = SettingsRepository(ctx)
            val audioOn = runCatching { repo.audioEnabled.first() }.getOrDefault(true)
            if (!audioOn) return@launch
            val lang = runCatching { repo.voiceAnnouncementLanguage.first() }.getOrDefault(currentLangCode)
            currentLangCode = normalizeLanguageCode(lang)
            val message = phraseForTripStart(currentLangCode)
            enqueueOrSpeak(message, currentLangCode, "gt_trip_start_$key")
        }
    }

    fun announceTripEnd(context: Context, tripKey: String) {
        val ctx = context.applicationContext
        val key = tripKey.ifBlank { "active_end" }
        if (lastSpokenEndKey == key) return
        lastSpokenEndKey = key

        ensureInitialized(ctx)
        scope.launch {
            val repo = SettingsRepository(ctx)
            val audioOn = runCatching { repo.audioEnabled.first() }.getOrDefault(true)
            if (!audioOn) return@launch
            val lang = runCatching { repo.voiceAnnouncementLanguage.first() }.getOrDefault(currentLangCode)
            currentLangCode = normalizeLanguageCode(lang)
            val message = phraseForTripEnd(currentLangCode)
            enqueueOrSpeak(message, currentLangCode, "gt_trip_end_$key")
        }
    }

    fun resetDeduplication() {
        lastSpokenStartKey = null
        lastSpokenEndKey = null
    }

    private fun enqueueOrSpeak(text: String, langCode: String, utteranceId: String) {
        if (!isInitialized || tts == null) {
            pendingSpeech = QueuedSpeech(text, langCode, utteranceId)
            appContext?.let { ensureInitialized(it) }
            return
        }
        speakNow(text, langCode, utteranceId)
    }

    private fun speakNow(text: String, langCode: String, utteranceId: String) {
        val engine = tts ?: return
        val normalized = normalizeLanguageCode(langCode)
        val candidates = if (normalized == LANG_TA) {
            listOf(Locale.forLanguageTag("ta-IN"), Locale("ta"))
        } else {
            listOf(Locale.forLanguageTag("en-IN"), Locale.US, Locale.ENGLISH)
        }

        var localeApplied = false
        for (loc in candidates) {
            val availability = runCatching { engine.setLanguage(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (availability != TextToSpeech.LANG_MISSING_DATA && availability != TextToSpeech.LANG_NOT_SUPPORTED) {
                localeApplied = true
                break
            }
        }
        if (!localeApplied) {
            Log.w(TAG, "Preferred TTS locale ($normalized) unavailable on device; using default engine locale")
        }

        engine.setPitch(1.0f)
        engine.setSpeechRate(0.95f)
        val res = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }.getOrElse {
            Log.e(TAG, "TTS speak failed for $utteranceId", it)
            TextToSpeech.ERROR
        }
        if (res == TextToSpeech.ERROR) {
            Log.w(TAG, "TTS speak returned ERROR for $utteranceId")
        }
    }
}
