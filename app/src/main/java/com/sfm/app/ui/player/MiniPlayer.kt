package com.sfm.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sfm.app.data.Video
import com.sfm.app.ui.MiniPlayerHeight
import com.sfm.app.ui.theme.Sfm
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 64dp mini-player card: live 16:9 video, title + category, play/pause, next, close.
 * Tap or swipe up → expand; swipe down or fling sideways → close.
 */
@Composable
fun MiniPlayerBar(
    video: Video,
    playing: Boolean,
    hasNext: Boolean,
    progress: PlaybackProgress,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    surface: @Composable () -> Unit,
) {
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val expand by rememberUpdatedState(onExpand)
    val close by rememberUpdatedState(onClose)
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(MiniPlayerHeight)
            .testTag("miniPlayer")
            .graphicsLayer {
                translationX = dx.value
                translationY = dy.value.coerceAtLeast(-size.height * 0.6f)
                val f = maxOf(abs(dx.value) / size.width, dy.value.coerceAtLeast(0f) / size.height)
                alpha = (1f - f * 0.9f).coerceIn(0f, 1f)
            }
            .pointerInput(Unit) {
                val vt = VelocityTracker()
                var horizontal: Boolean? = null
                detectDragGestures(
                    onDragStart = { vt.resetTracking(); horizontal = null },
                    onDragCancel = { scope.launch { dx.animateTo(0f) }; scope.launch { dy.animateTo(0f) } },
                    onDragEnd = {
                        val v = vt.calculateVelocity()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val fling = 900.dp.toPx()
                        scope.launch {
                            when {
                                dy.value < -h * 0.5f || (v.y < -fling && abs(v.y) > abs(v.x)) -> {
                                    expand()
                                    launch { dx.snapTo(0f) }; dy.snapTo(0f)
                                }
                                dy.value > h * 0.6f || (v.y > fling && abs(v.y) > abs(v.x)) -> {
                                    dy.animateTo(h * 1.6f, tween(160)); close()
                                }
                                abs(dx.value) > w * 0.35f || abs(v.x) > fling -> {
                                    val dir = if (dx.value + v.x * 0.1f >= 0) 1 else -1
                                    dx.animateTo(dir * w, tween(180)); close()
                                }
                                else -> {
                                    launch { dx.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) }
                                    dy.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                }
                            }
                        }
                    },
                ) { ch, drag ->
                    ch.consume()
                    vt.addPosition(ch.uptimeMillis, ch.position)
                    // the first move picks the axis: sideways = dismiss, vertical = expand / close
                    val h = horizontal ?: (abs(drag.x) > abs(drag.y)).also { horizontal = it }
                    scope.launch { if (h) dx.snapTo(dx.value + drag.x) else dy.snapTo(dy.value + drag.y) }
                }
            }
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Sfm.Surface2)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .clickable(onClick = onExpand),
    ) {
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.height(48.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) { surface() }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = video.cats.lastOrNull()?.name ?: video.artist
                if (!sub.isNullOrEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CtrlIcon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Пауза" else "Играть", onPlayPause, tint = Sfm.Text, size = 28.dp)
            CtrlIcon(Icons.Rounded.SkipNext, "Следующее", onNext, Modifier.width(44.dp), tint = Sfm.Text, size = 26.dp, enabled = hasNext)
            CtrlIcon(Icons.Rounded.Close, "Закрыть", onClose, Modifier.width(44.dp), tint = Sfm.TextDim, size = 22.dp)
            Spacer(Modifier.width(2.dp))
        }
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).drawWithContent {
                val d = progress.durationMs
                val f = if (d > 0) (progress.positionMs.toFloat() / d).coerceIn(0f, 1f) else 0f
                drawRect(Color.White.copy(alpha = 0.08f))
                drawRect(Sfm.Accent, size = size.copy(width = size.width * f))
            },
        )
    }
}
