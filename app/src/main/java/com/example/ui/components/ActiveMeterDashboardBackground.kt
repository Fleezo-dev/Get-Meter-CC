package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import java.util.Locale

private const val TAG = "ActiveMeterVideo"

/**
 * Active Meter Screen Upper Dashboard & Real Looping Background Video Surface.
 *
 * Uses TextureView + MediaPlayer backed by AssetFileDescriptor (res/raw/active_meter_bg.mp4)
 * so the H.264 Baseline driving-taxi video:
 *  - Clips cleanly inside rounded corners in Jetpack Compose without SurfaceView z-order issues.
 *  - Center-crops dynamically to fill any screen aspect ratio cleanly without black bars.
 *  - Loops continuously, completely muted, and never requests audio focus.
 *  - Pauses on ON_PAUSE/ON_STOP or trip pause, resumes on ON_RESUME, and releases native
 *    MediaPlayer + Surface deterministically on ON_DESTROY / Composable disposal.
 *  - Displays a clean premium GET TAXI branded fallback ONLY if the asset or decoder fails
 *    (never substitutes a static road drawing over working video).
 */
@Composable
fun ActiveMeterDashboardBackground(
    distanceKm: Double,
    durationSeconds: Long,
    speedKmH: Float,
    isPaused: Boolean,
    isDirectMeter: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var videoFailed by remember { mutableStateOf(false) }
    var videoReady by remember { mutableStateOf(false) }
    val latestIsPaused by rememberUpdatedState(isPaused)

    // Hold mutable native player/surface state cleanly across Compose recompositions
    val playerHolder = remember { ActiveMeterVideoPlayerHolder() }

    // Manage playback with Android Lifecycle so battery & GPS are unaffected when backgrounded
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (!videoFailed && !latestIsPaused) {
                        playerHolder.resumeIfPrepared()
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    playerHolder.pauseIfPlaying()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    playerHolder.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerHolder.release()
        }
    }

    // Sync pause/resume immediately when the driver pauses or resumes the active meter
    LaunchedEffect(isPaused, videoFailed) {
        if (videoFailed) return@LaunchedEffect
        if (isPaused) {
            playerHolder.pauseIfPlaying()
        } else {
            playerHolder.resumeIfPrepared()
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0E1219),
        border = BorderStroke(1.dp, Color(0x33FFC107)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Layer 1: Actual bundled MP4 video rendered on TextureView with center-crop Matrix
            if (!videoFailed) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("active_meter_video_texture"),
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            isOpaque = true
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surfaceTexture: SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    playerHolder.attachAndPrepare(
                                        context = ctx.applicationContext,
                                        textureView = this@apply,
                                        surfaceTexture = surfaceTexture,
                                        viewWidth = width,
                                        viewHeight = height,
                                        shouldAutoPlay = { !latestIsPaused },
                                        onReady = { videoReady = true },
                                        onError = {
                                            videoFailed = true
                                            videoReady = false
                                        }
                                    )
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    surfaceTexture: SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    playerHolder.updateCenterCrop(this@apply, width, height)
                                }

                                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                    playerHolder.release()
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                            }
                        }
                    }
                )
            }

            // Layer 1B: Premium Branded Fallback — ONLY shown when video failed or before first frame
            if (videoFailed || !videoReady) {
                PremiumBrandedVideoFallback(
                    isPaused = isPaused,
                    isDirectMeter = isDirectMeter,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("active_meter_branded_fallback")
                )
            }

            // Layer 2: Lightweight edge scrims ONLY at top & bottom for readable HUD badges.
            // Center of the frame remains 100% clear so the driving yellow-and-black taxi is unobstructed.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xB8090C12), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC090C12))
                        )
                    )
            )

            // Layer 3: Crisp GET TAXI live header + bottom live distance & duration telemetry
            val textShadow = Shadow(
                color = Color(0xCC000000),
                offset = Offset(0f, 2f),
                blurRadius = 6f
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "GET TAXI",
                            style = TextStyle(
                                color = Color(0xFFFFC107),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.6.sp,
                                shadow = textShadow
                            )
                        )
                        Text(
                            text = if (isDirectMeter) "DIRECT METER LIVE" else "SMART TAXI METER LIVE",
                            style = TextStyle(
                                color = Color(0xFFE2E8F0),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                shadow = textShadow
                            )
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isPaused) Color(0xCC3B2504) else Color(0xCC092916),
                        border = BorderStroke(
                            1.dp,
                            if (isPaused) Color(0xFFFBBF24) else Color(0xFF4ADE80)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isPaused) Color(0xFFFBBF24) else Color(0xFF4ADE80))
                            )
                            Text(
                                text = if (isPaused) "PAUSED" else "${String.format(Locale.US, "%.0f", speedKmH)} km/h",
                                color = if (isPaused) Color(0xFFFDE68A) else Color(0xFF86EFAC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "LIVE DISTANCE",
                            style = TextStyle(
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                shadow = textShadow
                            )
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.2f", distanceKm)} km",
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                shadow = textShadow
                            )
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "DURATION",
                            style = TextStyle(
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                shadow = textShadow
                            )
                        )
                        val hrs = durationSeconds / 3600
                        val mins = (durationSeconds % 3600) / 60
                        val secs = durationSeconds % 60
                        val formatted = if (hrs > 0) {
                            String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
                        } else {
                            String.format(Locale.US, "%02d:%02d", mins, secs)
                        }
                        Text(
                            text = formatted,
                            style = TextStyle(
                                color = Color(0xFFFFC107),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                shadow = textShadow
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean, premium branded fallback shown ONLY when the bundled MP4 asset cannot be decoded
 * (or briefly while the hardware decoder primes the first frame). Never renders a road drawing.
 */
@Composable
private fun PremiumBrandedVideoFallback(
    isPaused: Boolean,
    isDirectMeter: Boolean,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "FallbackPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF141821),
                    Color(0xFF1E222D),
                    Color(0xFF0E1117)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFFFC107).copy(alpha = pulseAlpha * 0.35f),
                border = BorderStroke(1.5.dp, Color(0xFFFFC107).copy(alpha = pulseAlpha)),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocalTaxi,
                        contentDescription = "GET TAXI Live Meter",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = if (isPaused) "METER PAUSED" else if (isDirectMeter) "DIRECT TRIP ACTIVE" else "TRIP IN PROGRESS",
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp
            )
        }
    }
}

/**
 * Native MediaPlayer + TextureView Surface controller with center-crop scaling and safe teardown.
 */
private class ActiveMeterVideoPlayerHolder {
    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var isPrepared: Boolean = false
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    fun attachAndPrepare(
        context: android.content.Context,
        textureView: TextureView,
        surfaceTexture: SurfaceTexture,
        viewWidth: Int,
        viewHeight: Int,
        shouldAutoPlay: () -> Boolean,
        onReady: () -> Unit,
        onError: () -> Unit
    ) {
        release()
        try {
            val newSurface = Surface(surfaceTexture)
            surface = newSurface

            val player = MediaPlayer()
            mediaPlayer = player

            val afd = context.resources.openRawResourceFd(R.raw.active_meter_bg)
            if (afd == null) {
                Log.e(TAG, "Raw resource active_meter_bg.mp4 could not be opened")
                release()
                onError()
                return
            }

            try {
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } finally {
                try { afd.close() } catch (_: Exception) {}
            }

            player.setSurface(newSurface)
            player.isLooping = true
            player.setVolume(0f, 0f)
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
            } catch (_: Throwable) {}

            player.setOnVideoSizeChangedListener { _, vw, vh ->
                videoWidth = vw
                videoHeight = vh
                textureView.post {
                    updateCenterCrop(textureView, textureView.width.takeIf { it > 0 } ?: viewWidth, textureView.height.takeIf { it > 0 } ?: viewHeight)
                }
            }

            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    textureView.post { onReady() }
                }
                false
            }

            player.setOnPreparedListener { mp ->
                isPrepared = true
                videoWidth = mp.videoWidth
                videoHeight = mp.videoHeight
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                textureView.post {
                    updateCenterCrop(textureView, textureView.width.takeIf { it > 0 } ?: viewWidth, textureView.height.takeIf { it > 0 } ?: viewHeight)
                    try {
                        if (shouldAutoPlay()) {
                            mp.start()
                        } else {
                            // Seek to first frame so paused trip shows crisp still frame instead of blank
                            mp.seekTo(1)
                        }
                        onReady()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start prepared MediaPlayer", e)
                        onError()
                    }
                }
            }

            player.setOnCompletionListener { mp ->
                try {
                    mp.seekTo(0)
                    if (shouldAutoPlay()) {
                        mp.start()
                    }
                } catch (_: Exception) {}
            }

            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer decode/playback error what=$what extra=$extra")
                isPrepared = false
                textureView.post { onError() }
                true
            }

            player.prepareAsync()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed initializing active meter video player", t)
            release()
            onError()
        }
    }

    fun updateCenterCrop(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()

        var scaleX = 1f
        var scaleY = 1f
        if (videoRatio > viewRatio) {
            scaleX = videoRatio / viewRatio
        } else {
            scaleY = viewRatio / videoRatio
        }

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        textureView.setTransform(matrix)
    }

    fun pauseIfPlaying() {
        try {
            val mp = mediaPlayer
            if (isPrepared && mp != null && mp.isPlaying) {
                mp.pause()
            }
        } catch (_: Exception) {}
    }

    fun resumeIfPrepared() {
        try {
            val mp = mediaPlayer
            if (isPrepared && mp != null && !mp.isPlaying) {
                mp.setVolume(0f, 0f)
                mp.start()
            }
        } catch (_: Exception) {}
    }

    fun release() {
        isPrepared = false
        try {
            mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) mp.stop()
                } catch (_: Exception) {}
                try {
                    mp.setSurface(null)
                } catch (_: Exception) {}
                try {
                    mp.reset()
                } catch (_: Exception) {}
                try {
                    mp.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        try {
            surface?.release()
        } catch (_: Exception) {}
        surface = null
    }
}
