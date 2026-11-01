package com.sfm.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sfm.app.data.Video
import com.sfm.app.ui.common.EmptyState
import com.sfm.app.ui.common.Thumb
import com.sfm.app.ui.lock.FieldShape
import com.sfm.app.ui.lock.sfmFieldColors
import com.sfm.app.ui.theme.Sfm

/** List row: 16:9 thumbnail + text column + optional trailing action. The whole row opens the video. */
@Composable
internal fun VideoRow(
    v: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().background(Sfm.Bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            Thumb(v, Modifier.fillMaxSize())
            overlay()
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), content = content)
        trailing?.invoke()
    }
}

@Composable
internal fun RowTitle(v: Video) =
    Text(v.title.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleSmall, color = Sfm.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)

@Composable
internal fun RowMeta(text: String, color: Color = Sfm.TextDim) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)

internal val Video.subtitle: String? get() = cats.lastOrNull()?.name ?: artist

/** "N видео · …" line above a list with an action on the right. */
@Composable
internal fun PageHeader(text: String, action: String?, icon: ImageVector? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Sfm.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null) TextButton(onClick = onAction) {
            icon?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
            Text(action)
        }
    }
}

/** Empty state centred in the page, still scrollable in landscape. */
@Composable
internal fun PageEmpty(icon: ImageVector, title: String, text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().heightIn(min = h), contentAlignment = Alignment.Center) {
                EmptyState(icon, title, text, Modifier.padding(bottom = 24.dp), action, onAction)
            }
        }
    }
}

@Composable
internal fun FilterField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "Очистить") } },
        shape = FieldShape, colors = sfmFieldColors(),
    )
}

/** Swipe left or right removes the row. */
@Composable
internal fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state, modifier = modifier,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().background(Color(0xFF3A1422)).padding(horizontal = 24.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Delete, null, tint = Sfm.Hot)
                    Spacer(Modifier.width(8.dp))
                    Text("Удалить", color = Sfm.Hot, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        onDismiss = { onRemove() },
    ) { content() }
}

internal fun filterVideos(list: List<Video>, q: String): List<Video> {
    val words = q.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return list
    return list.filter { v ->
        val hay = (listOf(v.title, v.artist.orEmpty()) + v.cats.map { it.name } + v.tags).joinToString(" ").lowercase()
        words.all { it in hay }
    }
}
