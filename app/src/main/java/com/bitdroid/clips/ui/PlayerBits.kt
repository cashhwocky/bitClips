package com.bitdroid.clips.ui

import android.view.HapticFeedbackConstants
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import com.bitdroid.clips.data.ChannelRef
import com.bitdroid.clips.data.Library
import com.bitdroid.clips.data.Prefs
import com.bitdroid.clips.data.VideoRef
import com.bitdroid.clips.data.clock
import com.bitdroid.clips.data.compact
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** The video picture. A TextureView so it clips to Bloom's rounded corners. */
@Composable
fun VideoSurface(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
        update = { player.setVideoTextureView(it) },
        onRelease = { player.clearVideoTextureView(it) },
        modifier = modifier,
    )
}

/** Live position of a player, polled a few times a second. */
class Progress {
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
}

@Composable
fun rememberProgress(player: Player): Progress {
    val p = remember { Progress() }
    LaunchedEffect(player) {
        while (true) {
            p.position = player.currentPosition
            p.duration = player.duration.coerceAtLeast(0)
            p.buffered = player.bufferedPosition
            delay(200)
        }
    }
    return p
}

/**
 * Material You's squiggly progress line: the played part waves while the video plays and flattens
 * when it's paused. Tap or drag anywhere to seek.
 */
@Composable
fun WavyProgress(progress: Progress, playing: Boolean, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, color: Color = Bit.accent, onScrub: (Long) -> Unit = {}) {
    val amp by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.7f), label = "amp")
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "phase")
    var drag by remember { mutableFloatStateOf(-1f) }
    val dur = progress.duration
    val frac = if (drag >= 0) drag else if (dur > 0) progress.position.toFloat() / dur else 0f
    val buf = if (dur > 0) progress.buffered.toFloat() / dur else 0f
    Canvas(
        // Keep system back/home gestures from stealing drags near the screen edge.
        modifier.fillMaxWidth().height(36.dp).systemGestureExclusion()
            .pointerInput(dur) {
                detectTapGestures { o -> if (dur > 0) onSeek((o.x / size.width * dur).toLong()) }
            }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f); onScrub((drag * dur).toLong()) },
                    onDragEnd = { if (dur > 0 && drag >= 0) onSeek((drag * dur).toLong()); drag = -1f; onScrub(-1) },
                    onDragCancel = { drag = -1f; onScrub(-1) },
                ) { change, _ ->
                    change.consume()
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    onScrub((drag * dur).toLong())
                }
            },
    ) {
        val cy = size.height / 2
        val stroke = 4.dp.toPx()
        val x = (size.width * frac.coerceIn(0f, 1f))
        // rest of the track
        drawLine(Color.White.copy(alpha = 0.22f), Offset(x, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.38f), Offset(x, cy), Offset(size.width * buf.coerceIn(0f, 1f), cy), stroke, StrokeCap.Round)
        // the squiggle
        val a = 3.2.dp.toPx() * amp * (if (drag >= 0) 0.4f else 1f)
        val wl = 22.dp.toPx()
        val path = Path()
        var px = 0f
        path.moveTo(0f, cy)
        while (px < x) {
            path.lineTo(px, cy + a * sin(px / wl * 2 * PI.toFloat() - phase))
            px += 2f
        }
        path.lineTo(x, cy + a * sin(x / wl * 2 * PI.toFloat() - phase))
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
        // thumb: a soft vertical pill
        val tw = 5.dp.toPx()
        val th = if (drag >= 0) 24.dp.toPx() else 18.dp.toPx()
        drawRoundRect(color, Offset(x - tw / 2, cy - th / 2), Size(tw, th), CornerRadius(tw / 2))
    }
}

/** Play/pause that morphs between a clover (paused) and a squircle (playing). */
@Composable
fun MorphPlayButton(playing: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, bg: Color = Bit.accent, fg: Color = Bit.onAccent) {
    val m by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 400f), label = "morph")
    Box(
        Modifier.size(size).squish(onClick = onClick).graphicsLayer { rotationZ = m * 45f }
            .clip(Shapes.morph(Shapes.clover, Shapes.squircle, m)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = fg,
            modifier = Modifier.size(size * 0.46f).graphicsLayer { rotationZ = -m * 45f },
        )
    }
}

