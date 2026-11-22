package com.example.ui.screens

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "SplashLaunchVideo"
internal const val LAUNCH_INTRO_RAW_NAME = "launch_intro"
private val SplashBrandGold = Color(0xFFFFC107)

/**
 * Resolves `app/src/main/res/raw/launch_intro.mp4` dynamically so the app compiles cleanly
 * before the genuine MP4 is supplied and automatically plays it as soon as the file is added.
 */
internal fun resolveLaunchIntroRawResId(context: Context): Int {
    return try {
        context.resources.getIdentifier(LAUNCH_INTRO_RAW_NAME, "raw", context.packageName)
    } catch (_: Throwable) {
        0
    }
}

@Composable
fun SplashScreen(onNavigateToHome: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val launchIntroResId = remember(context) { resolveLaunchIntroRawResId(context) }
    var videoFailed by remember(launchIntroResId) { mutableStateOf(launchIntroResId == 0) }
    var videoReady by remember { mutableStateOf(false) }
    var hasNavigated by remember { mutableStateOf(false) }

    val screenAlpha = remember { Animatable(1f) }
    val playerHolder = remember { LaunchIntroVideoPlayerHolder() }

    val latestOnNavigateToHome by rememberUpdatedState(onNavigateToHome)

    fun finishAndNavigateHome() {
        if (hasNavigated) return
        hasNavigated = true
        scope.launch {
            screenAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220)
            )
            playerHolder.release()
            latestOnNavigateToHome()
        }
    }

    // Lifecycle handling and deterministic native MediaPlayer/Surface cleanup
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (!videoFailed && !hasNavigated) {
                        playerHolder.resumeIfPrepared()
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    playerHolder.pauseIfPlaying()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    playerHolder.release()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerHolder.release()
        }
    }

    // Watchdog prevents launch screen from ever stalling if a supplied video fails to signal completion
    LaunchedEffect(launchIntroResId, videoFailed) {
        if (launchIntroResId != 0 && !videoFailed) {
            delay(10_000L)
            finishAndNavigateHome()
        }
    }

    // Full-screen pure-black container (no white card, no white background, no white flash)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        if (launchIntroResId != 0 && !videoFailed) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (videoReady) screenAlpha.value else 0f)
                    .testTag("splash_launch_video_texture"),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        isOpaque = false
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                playerHolder.attachAndPlayOnce(
                                    context = ctx.applicationContext,
                                    rawResId = launchIntroResId,
                                    textureView = this@apply,
                                    surfaceTexture = surfaceTexture,
                                    viewWidth = width,
                                    viewHeight = height,
                                    onFirstFrameReady = { videoReady = true },
                                    onCompleted = { finishAndNavigateHome() },
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
                                playerHolder.updateFitCenter(this@apply, width, height)
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
        } else {
            // Temporary logo animation fallback on pure black while launch_intro.mp4 is not yet supplied
            TemporaryLogoFallback(
                screenAlpha = screenAlpha.value,
                onFinished = { finishAndNavigateHome() }
            )
        }
    }
}

@Composable
private fun TemporaryLogoFallback(
    screenAlpha: Float,
    onFinished: () -> Unit
) {
    val scale = remember { Animatable(0.82f) }
    val logoAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val latestOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 550)
        )
        scale.animateTo(
            targetValue = 1.04f,
            animationSpec = tween(durationMillis = 500)
        )
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 250)
        )
        subtitleAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400)
        )
        delay(1100L)
        latestOnFinished()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .alpha(screenAlpha)
            .padding(horizontal = 32.dp)
            .testTag("splash_fallback_logo")
    ) {
        Image(
            painter = painterResource(id = R.drawable.get_taxi_header_logo),
            contentDescription = "GET TAXI Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .scale(scale.value)
                .alpha(logoAlpha.value)
                .width(220.dp)
                .height(100.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "GET TAXI – SMART TAXI METER",
            color = SplashBrandGold,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.8.sp,
            modifier = Modifier.alpha(subtitleAlpha.value)
        )
    }
}

/**
 * Single-play native MediaPlayer + TextureView controller for `res/raw/launch_intro.mp4`.
 * Never loops, scales cleanly via fit-center on pure black without distortion, and releases
 * all native decoder resources deterministically on completion, error, or lifecycle teardown.
 */
private class LaunchIntroVideoPlayerHolder {
    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var isPrepared: Boolean = false
    private var isCompleted: Boolean = false
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    fun attachAndPlayOnce(
        context: Context,
        rawResId: Int,
        textureView: TextureView,
        surfaceTexture: SurfaceTexture,
        viewWidth: Int,
        viewHeight: Int,
        onFirstFrameReady: () -> Unit,
        onCompleted: () -> Unit,
        onError: () -> Unit
    ) {
        release()
        isCompleted = false
        try {
            val newSurface = Surface(surfaceTexture)
            surface = newSurface

            val player = MediaPlayer()
            mediaPlayer = player

            val afd = context.resources.openRawResourceFd(rawResId)
            if (afd == null) {
                Log.e(TAG, "Raw launch intro resource could not be opened")
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
            player.isLooping = false
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
            } catch (_: Throwable) {}

            player.setOnVideoSizeChangedListener { _, vw, vh ->
                videoWidth = vw
                videoHeight = vh
                textureView.post {
                    updateFitCenter(
                        textureView,
                        textureView.width.takeIf { it > 0 } ?: viewWidth,
                        textureView.height.takeIf { it > 0 } ?: viewHeight
                    )
                }
            }

            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    textureView.post { onFirstFrameReady() }
                }
                false
            }

            player.setOnPreparedListener { mp ->
                isPrepared = true
                videoWidth = mp.videoWidth
                videoHeight = mp.videoHeight
                mp.isLooping = false
                textureView.post {
                    updateFitCenter(
                        textureView,
                        textureView.width.takeIf { it > 0 } ?: viewWidth,
                        textureView.height.takeIf { it > 0 } ?: viewHeight
                    )
                    try {
                        mp.start()
                        onFirstFrameReady()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed starting launch intro MediaPlayer", e)
                        release()
                        onError()
                    }
                }
            }

            player.setOnCompletionListener {
                isCompleted = true
                textureView.post { onCompleted() }
            }

            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Launch intro MediaPlayer error what=$what extra=$extra")
                isPrepared = false
                release()
                textureView.post { onError() }
                true
            }

            player.prepareAsync()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed initializing launch intro video player", t)
            release()
            onError()
        }
    }

    /**
     * Fit-center matrix preserves the entire sedan & GET TAXI brand reveal on any screen ratio
     * while seamlessly blending with the pure-black full-screen background.
     */
    fun updateFitCenter(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()

        var scaleX = 1f
        var scaleY = 1f
        if (videoRatio > viewRatio) {
            scaleY = viewRatio / videoRatio
        } else {
            scaleX = videoRatio / viewRatio
        }

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        textureView.setTransform(matrix)
    }

    fun pauseIfPlaying() {
        try {
            val mp = mediaPlayer
            if (isPrepared && !isCompleted && mp != null && mp.isPlaying) {
                mp.pause()
            }
        } catch (_: Exception) {}
    }

    fun resumeIfPrepared() {
        try {
            val mp = mediaPlayer
            if (isPrepared && !isCompleted && mp != null && !mp.isPlaying) {
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
