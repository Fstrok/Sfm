package com.sfm.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sfm.app.ui.common.MenuRow
import com.sfm.app.ui.theme.Sfm

val Speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** ⋮ sheet: speed, scale, site/share/copy, hide. */
@Composable
fun PlayerMenuContent(
    speed: Float,
    resizeMode: Int,
    onSpeed: (Float) -> Unit,
    onResize: (Int) -> Unit,
    onOpenSite: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalContentColor provides Sfm.Text) { Column(modifier.fillMaxWidth().testTag("playerMenu")) {
        Label("Скорость")
        Segmented(Speeds.map { speedLabel(it).removeSuffix("×") }, Speeds.indexOf(speed), { onSpeed(Speeds[it]) })
        Label("Масштаб")
        Segmented(ResizeTitles, resizeMode.coerceIn(0, 2), onResize)
        Spacer(Modifier.height(10.dp))
        MenuRow(Icons.Outlined.OpenInBrowser, "Открыть на сайте", onOpenSite)
        MenuRow(Icons.Outlined.Share, "Поделиться", onShare)
        MenuRow(Icons.Outlined.ContentCopy, "Копировать ссылку", onCopy)
        MenuRow(Icons.Outlined.VisibilityOff, "Скрыть видео", onHide)
        Spacer(Modifier.height(8.dp))
    } }
}

@Composable
private fun Label(text: String) {
    Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = Sfm.TextDim)
}

@Composable
private fun Segmented(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Sfm.Surface3).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items.forEachIndexed { i, t ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(if (sel) Sfm.Accent else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t, color = if (sel) Color(0xFF00241A) else Sfm.Text, maxLines = 1, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium),
                )
            }
        }
    }
}

