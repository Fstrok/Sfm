package com.sfm.app.ui.library

import android.app.DownloadManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sfm.app.data.DlProgress
import com.sfm.app.data.DownloadEntry
import com.sfm.app.data.HistoryEntry
import com.sfm.app.data.LibraryData
import com.sfm.app.data.Video
import com.sfm.app.data.formatBytes
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.Screen
import com.sfm.app.ui.Tab
import com.sfm.app.ui.common.Pill
import com.sfm.app.ui.common.TabsPager
import com.sfm.app.ui.common.VideoGridContent
import com.sfm.app.ui.common.VideoMenuSheet
import com.sfm.app.ui.settings.ConfirmDialog
import com.sfm.app.ui.theme.Sfm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LibraryScreen() {
    val dl by LocalApp.current.dlProgress.collectAsState()
    LibraryContent(dl)
}

private fun titled(name: String, n: Int) = if (n > 0) "$name $n" else name

@Composable
internal fun LibraryContent(dl: Map<Long, DlProgress>, initialPage: Int = 0) {
    val app = LocalApp.current
    val data by app.library.data.collectAsState()
    val pager = rememberPagerState(initialPage) { 4 }
    // Hoisted: TabsPager keys pages by title, so a page is re-created when its count changes.
    val favGrid = rememberLazyGridState()
    val histList = rememberLazyListState()
    val dlList = rememberLazyListState()
    val hiddenList = rememberLazyListState()
    var filter by rememberSaveable { mutableStateOf("") }
    var seenReselect by rememberSaveable { mutableIntStateOf(app.tabReselect.second) }
    LaunchedEffect(app.tabReselect) {
        val (tab, n) = app.tabReselect
        if (n == seenReselect) return@LaunchedEffect
        seenReselect = n
        if (tab == Tab.LIBRARY.ordinal) when (pager.currentPage) {
            0 -> favGrid.animateScrollToItem(0)
            1 -> histList.animateScrollToItem(0)
            2 -> dlList.animateScrollToItem(0)
            else -> hiddenList.animateScrollToItem(0)
        }
    }

    Surface(Modifier.fillMaxSize(), color = Sfm.Bg, contentColor = Sfm.Text) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Библиотека", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = Sfm.Text)
                IconButton(onClick = { app.push(Screen.Settings) }) { Icon(Icons.Outlined.Settings, "Настройки", tint = Sfm.Text) }
            }
            TabsPager(
                titles = listOf(
                    titled("Избранное", data.favorites.size), titled("История", data.history.size),
                    titled("Скачанное", data.downloads.size), titled("Скрытые", data.hidden.size),
                ),
                state = pager,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> FavoritesPage(data, favGrid, filter) { filter = it }
                    1 -> HistoryPage(data, histList)
                    2 -> DownloadsPage(data, dl, dlList)
                    else -> HiddenPage(data, hiddenList)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- favorites

@Composable
private fun FavoritesPage(data: LibraryData, grid: LazyGridState, filter: String, onFilter: (String) -> Unit) {
    val app = LocalApp.current
    val favs = data.favorites
    if (favs.isEmpty()) {
        PageEmpty(Icons.Outlined.StarOutline, "Избранного пока нет", "Нажмите ☆ на превью видео или в плеере — видео появится здесь", "На главную") { app.openTab(Tab.HOME) }
        return
    }
    val shown = remember(favs, filter) { filterVideos(favs, filter) }
    VideoGridContent(
        videos = shown,
        onOpen = { app.player.open(shown, it) },
        grid = grid,
        header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "fav-actions", contentType = "header") {
                Column(Modifier.padding(bottom = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { app.player.open(shown, 0) }, enabled = shown.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Sfm.Accent, contentColor = Color(0xFF03140F)),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Смотреть все", maxLines = 1)
                        }
                        Button(
                            onClick = { app.player.open(shown.shuffled(), 0) }, enabled = shown.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Sfm.Surface2, contentColor = Sfm.Text),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(Icons.Outlined.Shuffle, null, Modifier.size(19.dp)); Spacer(Modifier.width(6.dp)); Text("Перемешать", maxLines = 1)
                        }
                    }
                    if (favs.size > 12) {
                        Spacer(Modifier.height(10.dp))
                        FilterField(filter, onFilter, "Найти в избранном")
                    }
                }
            }
            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "fav-none") {
                Text("В избранном нет «$filter»", Modifier.padding(vertical = 32.dp), color = Sfm.TextDim, style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}

// ---------------------------------------------------------------- history

private val ru = Locale.forLanguageTag("ru")
private val dayFmt = DateTimeFormatter.ofPattern("d MMMM", ru)
private val dayYearFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", ru)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", ru)

private class Day(val label: String, val entries: List<IndexedValue<HistoryEntry>>)

internal fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> (if (day.year == today.year) dayFmt else dayYearFmt).format(day)
}

private fun groupByDay(history: List<HistoryEntry>, zone: ZoneId = ZoneId.systemDefault()): List<Day> {
    val today = LocalDate.now(zone)
    return history.withIndex()
        .groupBy { Instant.ofEpochMilli(it.value.at).atZone(zone).toLocalDate() }
        .map { (d, list) -> Day(dayLabel(d, today), list) }
}

@Composable
private fun HistoryPage(data: LibraryData, state: LazyListState) {
    val app = LocalApp.current
    val history = data.history
    var confirm by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Pair<Int, Video>?>(null) }
    if (history.isEmpty()) {
        PageEmpty(Icons.Outlined.History, "История пуста", "Здесь появятся видео, которые вы открывали")
        return
    }
    val days = remember(history) { groupByDay(history) }
    fun play(i: Int) = app.player.open(history.drop(i).map { it.video }, 0)
    fun remove(e: HistoryEntry) {
        app.library.removeHistory(setOf(e.video.id))
        app.toast("Удалено из истории", "Вернуть") {
            app.library.update { d -> if (e.video.id in d.historyById) d else d.copy(history = (d.history + e).sortedByDescending { it.at }) }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "hist-head", contentType = "head") {
            PageHeader("${history.size} видео · смахните, чтобы удалить", "Очистить", Icons.Outlined.DeleteSweep) { confirm = true }
        }
        days.forEach { day ->
            stickyHeader(key = "day-${day.label}", contentType = "day") {
                Text(
                    day.label, Modifier.fillMaxWidth().background(Sfm.Bg).padding(start = 20.dp, top = 10.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.titleSmall, color = Sfm.Text,
                )
            }
            items(day.entries, key = { it.value.video.id }, contentType = { "row" }) { (i, e) ->
                SwipeToRemove(onRemove = { remove(e) }, Modifier.animateItem()) {
                    HistoryRow(e, onClick = { play(i) }, onLongClick = { menuFor = i to e.video })
                }
            }
        }
    }
    if (confirm) ConfirmDialog(
        "Очистить историю?", "Просмотренные видео и места остановки будут забыты", "Очистить",
        onConfirm = { app.library.clearHistory(); app.toast("История очищена") }, onDismiss = { confirm = false },
    )
    menuFor?.let { (i, v) -> VideoMenuSheet(v, onDismiss = { menuFor = null }, onPlay = { play(i) }) }
}

@Composable
private fun HistoryRow(e: HistoryEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val v = e.video
    VideoRow(
        v, onClick, onLongClick = onLongClick,
        overlay = {
            v.durationLabel?.let { Pill(it, Modifier.align(Alignment.BottomEnd).padding(5.dp)) }
            if (e.progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(e.progress).height(3.dp).background(Sfm.Hot))
            }
        },
    ) {
        RowTitle(v)
        v.subtitle?.let { Spacer(Modifier.height(2.dp)); RowMeta(it) }
        Spacer(Modifier.height(2.dp))
        val time = timeFmt.format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()))
        RowMeta("просмотрено ${(e.progress * 100).roundToInt()}% · $time")
    }
}

// ---------------------------------------------------------------- downloads

@Composable
private fun DownloadsPage(data: LibraryData, dl: Map<Long, DlProgress>, state: LazyListState) {
    val app = LocalApp.current
    val items = data.downloads
    var confirmOne by remember { mutableStateOf<DownloadEntry?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Pair<Int, Video>?>(null) }
    if (items.isEmpty()) {
        PageEmpty(Icons.Outlined.DownloadForOffline, "Скачанных видео нет", "Нажмите «Скачать» на превью или в плеере — файл сохранится в телефоне и будет играть без интернета")
        return
    }
    val total = items.sumOf { e -> dl[e.video.id]?.total?.takeIf { it > 0 } ?: e.size }
    fun play(i: Int) = app.player.open(items.map { it.video }, i)
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "dl-head", contentType = "head") {
            Column {
                PageHeader(if (total > 0) "${items.size} видео · ${formatBytes(total)}" else "${items.size} видео", "Удалить всё", Icons.Outlined.DeleteSweep) { confirmAll = true }
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Sfm.Accent.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.OfflinePin, null, Modifier.size(20.dp), tint = Sfm.Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("Файлы лежат в Download/SFM и играют без интернета", style = MaterialTheme.typography.bodySmall, color = Sfm.Text)
                }
            }
        }
        itemsIndexed(items, key = { _, e -> e.video.id }, contentType = { _, _ -> "row" }) { i, e ->
            DownloadRow(
                e, dl[e.video.id], data.settings.wifiOnly,
                onClick = { play(i) }, onLongClick = { menuFor = i to e.video },
                onRetry = { app.download(e.video) }, onDelete = { confirmOne = e },
                modifier = Modifier.animateItem(),
            )
        }
    }
    confirmOne?.let { e ->
        val running = dl[e.video.id]?.running == true
        ConfirmDialog(
            if (running) "Отменить скачивание?" else "Удалить видео?",
            if (running) "Загрузка «${e.video.title}» остановится, недокачанный файл будет удалён" else "Файл «${e.video.title}» будет удалён с телефона",
            if (running) "Отменить загрузку" else "Удалить",
            onConfirm = { app.graph.downloads.remove(e.video.id); app.toast(if (running) "Скачивание отменено" else "Файл удалён") },
            onDismiss = { confirmOne = null },
        )
    }
    if (confirmAll) ConfirmDialog(
        "Удалить все скачанные?",
        "${items.size} видео" + (if (total > 0) " (${formatBytes(total)})" else "") + " будут удалены с телефона, незавершённые загрузки отменены",
        "Удалить всё",
        onConfirm = { items.forEach { app.graph.downloads.remove(it.video.id) }; app.toast("Скачанные видео удалены") },
        onDismiss = { confirmAll = false },
    )
    menuFor?.let { (i, v) -> VideoMenuSheet(v, onDismiss = { menuFor = null }, onPlay = { play(i) }) }
}

@Composable
private fun DownloadRow(
    e: DownloadEntry, p: DlProgress?, wifiOnly: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = p?.running == true
    VideoRow(
        e.video, onClick, modifier, onLongClick = onLongClick,
        overlay = {
            if (p?.ok == true) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp), tint = Sfm.Accent)
            }
            e.video.durationLabel?.let { Pill(it, Modifier.align(Alignment.BottomEnd).padding(5.dp)) }
        },
        trailing = {
            IconButton(onClick = onDelete) {
                Icon(if (running) Icons.Outlined.Close else Icons.Outlined.DeleteOutline, if (running) "Отменить скачивание" else "Удалить файл", tint = Sfm.TextDim)
            }
        },
    ) {
        RowTitle(e.video)
        Spacer(Modifier.height(6.dp))
        DownloadStatus(e, p, wifiOnly, onRetry)
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = color)
        Spacer(Modifier.width(5.dp))
        RowMeta(text, color)
    }
}

private val Amber = Color(0xFFFFB547)

@Composable
private fun DownloadStatus(e: DownloadEntry, p: DlProgress?, wifiOnly: Boolean, onRetry: () -> Unit) {
    when {
        p == null -> RowMeta(if (e.size > 0) "${formatBytes(e.size)} · Download/SFM" else "Файл в Download/SFM")
        p.failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            StatusLine(Icons.Outlined.ErrorOutline, "Ошибка", MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.heightIn(min = 36.dp)) { Text("Повторить") }
        }
        p.ok -> StatusLine(Icons.Filled.CheckCircle, "Скачано · ${formatBytes(p.total.takeIf { it > 0 } ?: p.done)}", Sfm.Accent)
        p.status == DownloadManager.STATUS_PAUSED -> Row {
            Icon(Icons.Outlined.WifiOff, null, Modifier.padding(top = 1.dp).size(15.dp), tint = Amber)
            Spacer(Modifier.width(5.dp))
            Text(
                if (wifiOnly) "ждёт Wi-Fi (включено «Только по Wi-Fi»)" else "Пауза — ждёт сеть",
                style = MaterialTheme.typography.bodySmall, color = Amber, maxLines = 2,
            )
        }
        p.status == DownloadManager.STATUS_PENDING || p.total <= 0 -> Column {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Sfm.Accent, trackColor = Sfm.Surface3)
            Spacer(Modifier.height(5.dp))
            RowMeta("Ожидание…")
        }
        else -> Column {
            LinearProgressIndicator(
                progress = { p.fraction }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Sfm.Accent, trackColor = Sfm.Surface3, gapSize = 0.dp, drawStopIndicator = {},
            )
            Spacer(Modifier.height(5.dp))
            RowMeta("${(p.fraction * 100).roundToInt()}% · ${formatBytes(p.done)} из ${formatBytes(p.total)}")
        }
    }
}

// ---------------------------------------------------------------- hidden

@Composable
private fun HiddenPage(data: LibraryData, state: LazyListState) {
    val app = LocalApp.current
    val hidden = data.hidden
    if (hidden.isEmpty()) {
        PageEmpty(Icons.Outlined.VisibilityOff, "Скрытых видео нет", "Скрыть видео можно долгим нажатием на превью")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "hidden-head", contentType = "head") {
            PageHeader("${hidden.size} видео не показываются в лентах", "Вернуть все", Icons.Outlined.Restore) {
                val ids = hidden.mapTo(HashSet()) { it.id }
                app.library.unhide(ids)
                app.toast("Все видео возвращены в ленты")
            }
        }
        items(hidden, key = { it.id }, contentType = { "row" }) { v ->
            VideoRow(
                v, onClick = { app.player.open(hidden, hidden.indexOf(v)) }, modifier = Modifier.animateItem(),
                trailing = {
                    TextButton(onClick = {
                        app.library.unhide(setOf(v.id))
                        app.toast("Видео возвращено", "Скрыть") { app.library.hide(v) }
                    }) { Text("Вернуть") }
                },
            ) {
                RowTitle(v)
                v.subtitle?.let { Spacer(Modifier.height(2.dp)); RowMeta(it) }
            }
        }
    }
}
