package com.sfm.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sfm.app.data.Video
import com.sfm.app.ui.theme.Sfm
import kotlin.math.max
import kotlin.math.min

/** Resize modes from settings: 0 fit, 1 fill (crop), 2 stretch. */
val ResizeTitles = listOf("Вписать", "Заполнить", "Растянуть")

/**
 * Lays out [content] (video surface or thumbnail) by the video [aspect] and [resizeMode],
 * then applies pinch [zoom]/[pan]. Clipped to the page.
 */
@Composable
fun VideoFrame(
    aspect: Float,
    resizeMode: Int,
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    pan: Offset = Offset.Zero,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val (w, h) = frameSize(aspect, resizeMode, maxWidth.value, maxHeight.value)
        Box(
            Modifier.requiredSize(w.dp, h.dp).graphicsLayer {
                scaleX = zoom; scaleY = zoom
                translationX = pan.x; translationY = pan.y
            },
        ) { content() }
    }
}

/** Size of the video frame inside a [cw]×[ch] container (same units). */
fun frameSize(aspect: Float, resizeMode: Int, cw: Float, ch: Float): Pair<Float, Float> {
    val a = aspect.takeIf { it > 0f } ?: (16f / 9f)
    return when (resizeMode) {
        2 -> cw to ch
        1 -> max(cw, ch * a).let { it to it / a }
        else -> min(cw, ch * a).let { it to it / a }
    }
}

/** Pan limits for a zoomed frame: only the part that overflows the screen can be panned into view. */
fun clampPan(pan: Offset, zoom: Float, frameW: Float, frameH: Float, cw: Float, ch: Float): Offset {
    val mx = ((frameW * zoom - cw) / 2f).coerceAtLeast(0f)
    val my = ((frameH * zoom - ch) / 2f).coerceAtLeast(0f)
    return Offset(pan.x.coerceIn(-mx, mx), pan.y.coerceIn(-my, my))
}

/**
 * One pager page: thumbnail until the first frame arrives, the [surface] when active,
 * spinner while resolving/buffering and the error card on failure.
 */
@Composable
fun VideoPageContent(
    v: Video,
    active: Boolean,
    aspect: Float,
    resizeMode: Int,
    zoom: Float,
    pan: Offset,
    showFrame: Boolean,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenSite: () -> Unit,
    onNext: (() -> Unit)?,
    surface: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoFrame(if (active) aspect else 16f / 9f, if (active) resizeMode else 0, zoom = if (active) zoom else 1f, pan = if (active) pan else Offset.Zero) {
            if (active) surface()
            AnimatedVisibility(!active || !showFrame, enter = fadeIn(), exit = fadeOut()) {
                if (v.thumb != null) AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Box(Modifier.fillMaxSize().background(Sfm.Surface))
            }
        }
        if (active && loading && error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(52.dp), color = Sfm.Accent, strokeWidth = 3.dp, trackColor = Color.White.copy(alpha = 0.15f))
        }
        if (active && error != null) {
            PlayerErrorCard(error, onRetry, onOpenSite, onNext, Modifier.align(Alignment.Center))
        }
    }
}
