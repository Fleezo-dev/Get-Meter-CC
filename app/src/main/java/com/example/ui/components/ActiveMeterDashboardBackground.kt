package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import java.util.Locale

/**
 * Active Meter Screen Dashboard & Looping Background Video Surface.
 *
 * Plays the bundled local looping MP4 (res/raw/active_meter_bg.mp4) muted, auto-looped,
 * and lifecycle-managed (pauses/releases cleanly on ON_PAUSE/ON_STOP/dispose).
 * Gracefully falls back to a crisp dark-charcoal Compose highway/route canvas if
 * VideoView/MediaPlayer is unavailable (e.g., low-memory OEM devices or JVM Robolectric tests).
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
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    val videoUri = remember(context.packageName) {
        Uri.parse("android.resource://${context.packageName}/${R.raw.active_meter_bg}")
    }

    // Properly pause/resume/release VideoView with Android lifecycle so battery & GPS are never impacted
    DisposableEffect(lifecycleOwner, isPaused) {
        val observer = LifecycleEventObserver { _, event ->
            val vv = videoViewRef
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (!videoFailed && !isPaused) {
                        try { vv?.start() } catch (_: Exception) {}
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    try { vv?.pause() } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_DESTROY -> {
                    try { vv?.stopPlayback() } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {}
            videoViewRef = null
        }
    }

    LaunchedEffect(isPaused, videoViewRef) {
        val vv = videoViewRef ?: return@LaunchedEffect
        try {
            if (isPaused) {
                if (vv.isPlaying) vv.pause()
            } else if (!videoFailed) {
                if (!vv.isPlaying) vv.start()
            }
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF101319))
    ) {
        // Layer 1: Bundled local looping H.264 background video (muted, low-resource)
        if (!videoFailed) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        try {
                            setAudioFocusRequest(android.media.AudioManager.AUDIOFOCUS_NONE)
                        } catch (_: Throwable) {}
                        setOnPreparedListener { mp: MediaPlayer ->
                            try {
                                mp.isLooping = true
                                mp.setVolume(0f, 0f)
                                if (!isPaused) start()
                            } catch (_: Exception) {
                                videoFailed = true
                            }
                        }
                        setOnErrorListener { _, _, _ ->
                            videoFailed = true
                            true
                        }
                        try {
                            setVideoURI(videoUri)
                        } catch (_: Throwable) {
                            videoFailed = true
                        }
                        videoViewRef = this
                    }
                }
            )
        }

        // Layer 2: Subtle dark-charcoal gradient + crisp faint highway/route dashboard vector overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Soft vignette for contrast & visual calm
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xCC0D1016),
                        Color(0x6612161E),
                        Color(0xE60B0D12)
                    )
                )
            )

            // Faint perspective road-route guidelines (also serves as seamless fallback)
            val horizonY = h * 0.28f
            val centerX = w * 0.5f

            val leftRoad = Path().apply {
                moveTo(centerX - w * 0.08f, horizonY)
                lineTo(w * 0.08f, h)
            }
            val rightRoad = Path().apply {
                moveTo(centerX + w * 0.08f, horizonY)
                lineTo(w * 0.92f, h)
            }
            drawPath(
                path = leftRoad,
                color = Color(0x33FFC107),
                style = Stroke(width = 2.dp.toPx())
            )
            drawPath(
                path = rightRoad,
                color = Color(0x33FFC107),
                style = Stroke(width = 2.dp.toPx())
            )
            drawLine(
                color = Color(0x44FFC107),
                start = Offset(centerX, horizonY + 8.dp.toPx()),
                end = Offset(centerX, h),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 16f), 0f)
            )
        }

        // Layer 3: Subtle GET TAXI branding + Live Distance & Duration readout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
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
                        color = Color(0xFFFFC107),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.6.sp
                    )
                    Text(
                        text = if (isDirectMeter) "DIRECT METER LIVE" else "SMART TAXI METER LIVE",
                        color = Color(0xFFB0BAC9),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPaused) Color(0x33F59E0B) else Color(0x3322C55E))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isPaused) "PAUSED" else "${String.format(Locale.US, "%.0f", speedKmH)} km/h",
                        color = if (isPaused) Color(0xFFFBBF24) else Color(0xFF4ADE80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
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
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", distanceKm)} km",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "DURATION",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
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
                        color = Color(0xFFFFC107),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
