package com.sfm.app.ui.common

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sfm.app.data.Video
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.theme.Sfm
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.rememberCoroutineScope

/** Long-press action sheet for a video. */
@Composable
fun VideoMenuSheet(v: Video, onDismiss: () -> Unit, onPlay: (() -> Unit)? = null) {
    val app = LocalApp.current
    val lib by app.library.data.collectAsState()
    val ctx = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val fav = v.id in lib.favIds
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Sfm.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(v, Modifier.width(112.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    v.artist?.let { Text("Автор: $it", style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim) }
                }
            }
            if (v.cats.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    v.cats.forEach { c ->
                        AssistChip(onClick = { onDismiss(); app.repo.catalog.term(c)?.let(app::openTerm) ?: app.openTerm(com.sfm.app.data.Term(c.id, c.name, link = c.link, type = com.sfm.app.data.TermType.CHARACTER)) }, label = { Text(c.name) })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (onPlay != null) MenuRow(Icons.Outlined.PlayArrow, "Смотреть") { onDismiss(); onPlay() }
            MenuRow(if (fav) Icons.Outlined.Star else Icons.Outlined.StarOutline, if (fav) "Убрать из избранного" else "В избранное") { app.toggleFavorite(v); onDismiss() }
            MenuRow(Icons.Outlined.Download, if (lib.downloadsById[v.id] != null) "Уже скачано" else "Скачать") { if (lib.downloadsById[v.id] == null) app.download(v); onDismiss() }
            MenuRow(Icons.Outlined.Share, "Поделиться ссылкой") {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, v.link), null))
                onDismiss()
            }
            MenuRow(Icons.Outlined.ContentCopy, "Копировать ссылку") {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("link", v.link))) }
                app.toast("Скопировано"); onDismiss()
            }
            MenuRow(Icons.Outlined.OpenInBrowser, "Открыть на сайте") {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.link)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(null)) }
                onDismiss()
            }
            MenuRow(Icons.Outlined.VisibilityOff, "Скрыть видео") { app.hide(v); onDismiss() }
            v.cats.lastOrNull()?.let { c ->
                MenuRow(Icons.Outlined.Block, "Не показывать «${c.name}»") {
                    val t = app.repo.catalog.term(c) ?: com.sfm.app.data.Term(c.id, c.name, link = c.link, type = com.sfm.app.data.TermType.CHARACTER)
                    if (!lib.isBlacklisted(t)) app.library.toggleBlacklist(t)
                    app.toast("«${c.name}» в чёрном списке", "Вернуть") { app.library.toggleBlacklist(t) }
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = Sfm.TextDim)
        Spacer(Modifier.width(18.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
