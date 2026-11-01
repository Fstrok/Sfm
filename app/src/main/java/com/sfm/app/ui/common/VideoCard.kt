package com.sfm.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.sfm.app.data.DlProgress
import com.sfm.app.data.HistoryEntry
import com.sfm.app.data.Video
import com.sfm.app.data.formatCount
import com.sfm.app.data.timeAgo
import com.sfm.app.ui.theme.Sfm

/** Thumbnail with graceful fallback: source url → placeholder icon (never an empty hole). */
@Composable
fun Thumb(v: Video, modifier: Modifier = Modifier) {
    Box(modifier.background(Sfm.Surface2)) {
        if (v.thumb != null) {
            SubcomposeAsyncImage(
                model = v.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize().shimmer()) },
                error = { Icon(Icons.Outlined.Movie, null, Modifier.align(Alignment.Center).size(32.dp), tint = Sfm.TextDim) },
            )
        } else {
            Icon(Icons.Outlined.Movie, null, Modifier.align(Alignment.Center).size(32.dp), tint = Sfm.TextDim)
        }
    }
}

/**
 * Video card: whole card opens the video, long press opens the action sheet.
 * Star (favorite) and download buttons are visible right on the card.
 */
@Composable
fun VideoCard(
    v: Video,
    isFavorite: Boolean,
    history: HistoryEntry?,
    download: DlProgress?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onMenu() })
            .padding(bottom = 6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium)) {
            Thumb(v, Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().height(44.dp).align(Alignment.BottomCenter).background(Sfm.scrimBottom))
            v.durationLabel?.let { Pill(it, Modifier.align(Alignment.BottomEnd).padding(6.dp)) }
            v.score?.takeIf { it > 0 }?.let { s ->
                Row(
                    Modifier.align(Alignment.BottomStart).padding(6.dp).clip(MaterialTheme.shapes.extraSmall).background(Color(0xB3000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ThumbUp, null, Modifier.size(11.dp), tint = Sfm.Accent)
                    Spacer(Modifier.width(3.dp))
                    Text(formatCount(s.toLong()), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).size(40.dp)) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x80000000)), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        if (isFavorite) "Убрать из избранного" else "В избранное",
                        Modifier.size(18.dp),
                        tint = if (isFavorite) Color(0xFFFFC94D) else Color.White,
                    )
                }
            }
            val p = history?.progress ?: 0f
            if (p > 0.02f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66FFFFFF)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(p).height(3.dp).background(Sfm.Hot))
            }
        }
        Row(Modifier.padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Sfm.Text)
                val meta = listOfNotNull(
                    v.cats.lastOrNull()?.name ?: v.artist,
                    v.views?.let { formatCount(it) + " просм." },
                    timeAgo(v.date).ifEmpty { null },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DownloadButton(download, isDownloaded, onDownload)
        }
    }
}

@Composable
fun DownloadButton(p: DlProgress?, isDownloaded: Boolean, onClick: () -> Unit, tint: Color = Sfm.TextDim) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        when {
            p?.running == true -> Box(contentAlignment = Alignment.Center) {
                if (p.total > 0) CircularProgressIndicator(progress = { p.fraction }, Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Sfm.Accent, trackColor = Sfm.Surface3)
                else CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Sfm.Accent)
            }
            isDownloaded && p?.failed != true -> Icon(Icons.Filled.CheckCircle, "Скачано", Modifier.size(20.dp), tint = Sfm.Accent)
            else -> Icon(Icons.Outlined.Download, "Скачать", Modifier.size(20.dp), tint = tint)
        }
    }
}

/** Skeleton card shown while the first page loads. */
@Composable
fun VideoCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).shimmer())
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.9f).height(12.dp).clip(MaterialTheme.shapes.extraSmall).shimmer())
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.55f).height(10.dp).clip(MaterialTheme.shapes.extraSmall).shimmer())
    }
}

@Suppress("unused")
private val keepOptIn = ExperimentalFoundationApi::class
