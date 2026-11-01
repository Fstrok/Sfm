package com.sfm.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sfm.app.data.DlProgress
import com.sfm.app.data.formatCount
import com.sfm.app.data.formatDuration
import com.sfm.app.ui.common.DownloadButton
import com.sfm.app.ui.theme.Sfm
import kotlin.math.roundToInt

/** Playback clock read only by the seek bar and time label (cheap 4 Hz updates). */
@Stable
class PlaybackProgress {
    var positionMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
}

@Immutable
data class ControlsState(
    val title: String,
    val subtitle: String,
    val playing: Boolean,
    val hasPrev: Boolean,
    val hasNext: Boolean,
    val favorite: Boolean,
    val download: DlProgress?,
    val downloaded: Boolean,
    val score: Int?,
    /** 1 up, -1 down, 0 none (this session). */
    val vote: Int,
    val speed: Float,
    val resizeMode: Int,
    val landscape: Boolean,
    /** Center play/prev/next hidden while the error card is shown. */
    val showCenter: Boolean = true,
    /** Spinner in place of the play icon. */
    val loading: Boolean = false,
)

class ControlsActions(
    val onMinimize: () -> Unit = {},
    val onPip: () -> Unit = {},
    val onMore: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onPrev: () -> Unit = {},
    val onNext: () -> Unit = {},
    /** Seek while scrubbing ([final] = false) and on release/tap ([final] = true). */
    val onSeek: (ms: Long, final: Boolean) -> Unit = { _, _ -> },
    val onScrubbing: (Boolean) -> Unit = {},
    val onFavorite: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onVote: (up: Boolean) -> Unit = {},
    val onShare: () -> Unit = {},
    val onResize: () -> Unit = {},
    val onSpeed: () -> Unit = {},
    val onRotate: () -> Unit = {},
    val onInfo: () -> Unit = {},
)

private val Scrim = Color(0xFF000000)
private val Dim = Color.White.copy(alpha = 0.72f)

fun speedLabel(s: Float): String = (if (s % 1f == 0f) "${s.toInt()}" else "$s".trimEnd('0')).replace('.', ',') + "×"

/** Overlay controls: top bar, center transport, bottom seek bar and action buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerControls(state: ControlsState, progress: PlaybackProgress, actions: ControlsActions, modifier: Modifier = Modifier) {
    val insets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    Box(modifier.fillMaxSize().testTag("playerControls")) {
        Box(Modifier.fillMaxWidth().height(if (state.landscape) 120.dp else 168.dp).background(Brush.verticalGradient(listOf(Scrim.copy(alpha = 0.78f), Scrim.copy(alpha = 0.35f), Color.Transparent))))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (state.landscape) 150.dp else 250.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Scrim.copy(alpha = 0.55f), Scrim.copy(alpha = 0.88f)))),
        )
        Column(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
            TopBar(state, actions)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.showCenter) CenterTransport(state, actions)
            }
            if (state.landscape) BottomLandscape(state, progress, actions) else BottomPortrait(state, progress, actions)
        }
    }
}

@Composable
private fun TopBar(state: ControlsState, actions: ControlsActions) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.Top) {
        CtrlIcon(Icons.Rounded.KeyboardArrowDown, "Свернуть", actions.onMinimize, size = 30.dp)
        Column(Modifier.weight(1f).padding(top = 6.dp, start = 2.dp, end = 4.dp)) {
            Text(
                state.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 21.sp),
            )
            if (state.subtitle.isNotEmpty()) {
                Text(state.subtitle, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
        CtrlIcon(Icons.Rounded.PictureInPictureAlt, "Мини-плеер", actions.onPip)
        CtrlIcon(Icons.Rounded.MoreVert, "Ещё", actions.onMore)
    }
}

@Composable
private fun CenterTransport(state: ControlsState, actions: ControlsActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (state.landscape) 48.dp else 28.dp)) {
        RoundIcon(Icons.Rounded.SkipPrevious, "Предыдущее", actions.onPrev, enabled = state.hasPrev)
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.38f))
                .clickable(onClick = actions.onPlayPause).semantics { contentDescription = if (state.playing) "Пауза" else "Играть" },
            contentAlignment = Alignment.Center,
        ) {
            if (state.loading) CircularProgressIndicator(Modifier.size(52.dp), color = Sfm.Accent, strokeWidth = 3.dp, trackColor = Color.White.copy(alpha = 0.15f))
            else Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(48.dp), tint = Color.White)
        }
        RoundIcon(Icons.Rounded.SkipNext, "Следующее", actions.onNext, enabled = state.hasNext)
    }
}

@Composable
private fun BottomPortrait(state: ControlsState, progress: PlaybackProgress, actions: ControlsActions) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TimeLabel(progress, Modifier.weight(1f))
            UtilityButtons(state, actions)
        }
        SeekBar(progress, actions.onSeek, actions.onScrubbing, Modifier.padding(horizontal = 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabeledAction(
                if (state.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, "Избранное",
                actions.onFavorite, tint = if (state.favorite) Gold else Color.White,
            )
            LabeledSlot(downloadLabel(state), actions.onDownload) {
                DownloadButton(state.download, state.downloaded, actions.onDownload, tint = Color.White)
            }
            LabeledAction(
                if (state.vote > 0) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, state.score?.let { formatCount(it.toLong()) } ?: "Лайк",
                { actions.onVote(true) }, tint = if (state.vote > 0) Sfm.Accent else Color.White,
            )
            LabeledAction(
                if (state.vote < 0) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown, "Дизлайк",
                { actions.onVote(false) }, tint = if (state.vote < 0) Sfm.Hot else Color.White,
            )
            LabeledAction(Icons.Rounded.Share, "Поделиться", actions.onShare)
        }
    }
}

@Composable
private fun BottomLandscape(state: ControlsState, progress: PlaybackProgress, actions: ControlsActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 2.dp)) {
        SeekBar(progress, actions.onSeek, actions.onScrubbing)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TimeLabel(progress, Modifier.padding(start = 8.dp).weight(1f))
            CtrlIcon(if (state.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, "Избранное", actions.onFavorite, tint = if (state.favorite) Gold else Color.White)
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { DownloadButton(state.download, state.downloaded, actions.onDownload, tint = Color.White) }
            Row(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).clickable { actions.onVote(true) }.padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (state.vote > 0) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, "Нравится", Modifier.size(22.dp), tint = if (state.vote > 0) Sfm.Accent else Color.White)
                state.score?.let { Text(formatCount(it.toLong()), color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp)) }
            }
            CtrlIcon(if (state.vote < 0) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown, "Не нравится", { actions.onVote(false) }, tint = if (state.vote < 0) Sfm.Hot else Color.White, size = 22.dp)
            CtrlIcon(Icons.Rounded.Share, "Поделиться", actions.onShare, size = 22.dp)
            Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(22.dp).background(Color.White.copy(alpha = 0.25f)))
            UtilityButtons(state, actions)
        }
    }
}

@Composable
private fun UtilityButtons(state: ControlsState, actions: ControlsActions) {
    CtrlIcon(resizeIcon(state.resizeMode), "Масштаб: ${ResizeTitles[state.resizeMode.coerceIn(0, 2)]}", actions.onResize, size = 24.dp)
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = actions.onSpeed).semantics { contentDescription = "Скорость" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            speedLabel(state.speed), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (state.speed != 1f) Sfm.Accent else Color.White,
        )
    }
    CtrlIcon(Icons.Rounded.ScreenRotation, "Повернуть", actions.onRotate, size = 22.dp)
    CtrlIcon(Icons.Outlined.Info, "Описание", actions.onInfo, size = 23.dp)
}

fun resizeIcon(mode: Int): ImageVector = when (mode) {
    1 -> Icons.Rounded.ZoomOutMap
    2 -> Icons.Rounded.Fullscreen
    else -> Icons.Rounded.FitScreen
}

private fun downloadLabel(s: ControlsState): String = when {
    s.download?.running == true -> if (s.download.total > 0) "${(s.download.fraction * 100).roundToInt()} %" else "Загрузка"
    s.downloaded && s.download?.failed != true -> "Скачано"
    else -> "Скачать"
}

val Gold = Color(0xFFFFC94D)

@Composable
private fun TimeLabel(progress: PlaybackProgress, modifier: Modifier = Modifier) {
    val dur = progress.durationMs
    Text(
        buildString {
            append(formatDuration(progress.positionMs.coerceAtLeast(0)))
            append(" / ")
            append(if (dur > 0) formatDuration(dur) else "–:––")
        },
        modifier,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
        maxLines = 1,
    )
}

@Composable
fun CtrlIcon(icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.White, size: androidx.compose.ui.unit.Dp = 24.dp, enabled: Boolean = true) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp), enabled = enabled) {
        Icon(icon, desc, Modifier.size(size), tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.22f)).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(34.dp), tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f))
    }
}

@Composable
private fun LabeledAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.White) =
    LabeledSlot(label, onClick, modifier) { Icon(icon, null, Modifier.size(24.dp), tint = tint) }

@Composable
private fun LabeledSlot(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    Column(
        modifier.widthIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp).semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.sp),
        )
    }
}

/** Seek bar: tap or drag to seek; while scrubbing it thickens and shows a time bubble. */
@Composable
fun SeekBar(progress: PlaybackProgress, onSeek: (Long, Boolean) -> Unit, onScrubbing: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val seek by rememberUpdatedState(onSeek)
    val scrubbing by rememberUpdatedState(onScrubbing)
    val dur by rememberUpdatedState(progress.durationMs)
    val density = LocalDensity.current
    val pad = with(density) { 10.dp.toPx() }
    fun frac(x: Float) = ((x - pad) / (widthPx - 2 * pad)).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(36.dp).testTag("seekBar")
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { o -> if (dur > 0) seek((frac(o.x) * dur).toLong(), true) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> scrub = frac(o.x); scrubbing(true) },
                    onDragEnd = { scrub?.let { if (dur > 0) seek((it * dur).toLong(), true) }; scrub = null; scrubbing(false) },
                    onDragCancel = { scrub = null; scrubbing(false) },
                ) { ch, _ ->
                    ch.consume()
                    scrub = frac(ch.position.x)
                    if (dur > 0) seek((scrub!! * dur).toLong(), false)
                }
            },
    ) {
        val d = progress.durationMs
        val played = scrub ?: if (d > 0) (progress.positionMs.toFloat() / d).coerceIn(0f, 1f) else 0f
        val buffered = if (d > 0) (progress.bufferedMs.toFloat() / d).coerceIn(0f, 1f) else 0f
        val active = scrub != null
        Canvas(Modifier.fillMaxSize()) {
            val track = (if (active) 5.dp else 3.dp).toPx()
            val y = size.height / 2
            val w = size.width - 2 * pad
            val r = CornerRadius(track / 2)
            drawRoundRect(Color.White.copy(alpha = 0.24f), Offset(pad, y - track / 2), Size(w, track), r)
            drawRoundRect(Color.White.copy(alpha = 0.42f), Offset(pad, y - track / 2), Size(w * buffered, track), r)
            drawRoundRect(Sfm.Accent, Offset(pad, y - track / 2), Size(w * played, track), r)
            drawCircle(Sfm.Accent, (if (active) 9.dp else 6.5.dp).toPx(), Offset(pad + w * played, y))
        }
        if (active && d > 0) {
            val bubbleW = 64.dp
            val x = with(density) { (pad + (widthPx - 2 * pad) * played).toDp() } - bubbleW / 2
            Box(
                Modifier.offset { IntOffset(x.coerceIn(0.dp, maxWidth - bubbleW).roundToPx(), (-34).dp.roundToPx()) }
                    .width(bubbleW).clip(RoundedCornerShape(8.dp)).background(Color(0xF01C1C29)).padding(vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(formatDuration((played * d).toLong()), color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
            }
        }
    }
}
