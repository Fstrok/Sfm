@file:OptIn(ExperimentalLayoutApi::class)

package com.sfm.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sfm.app.data.Duration
import com.sfm.app.data.FeedKind
import com.sfm.app.data.FeedSource
import com.sfm.app.data.Settings
import com.sfm.app.data.SortMode
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.Screen
import com.sfm.app.ui.common.CollapsingHeader
import com.sfm.app.ui.common.FeedGrid
import com.sfm.app.ui.common.OnTabReselect
import com.sfm.app.ui.common.TabsPager
import com.sfm.app.ui.common.expand
import com.sfm.app.ui.theme.Sfm

val HOME_TABS = listOf("Новые" to FeedKind.LATEST, "Тренды" to FeedKind.TRENDING, "Горячее" to FeedKind.HOT, "Топ" to FeedKind.TOP, "Длинные" to FeedKind.LONG)

/** Filters that change which videos are shown (the column count is just layout). */
val Settings.filtersActive get() = duration != Duration.ANY || sort != SortMode.SITE || hideWatched

fun Settings.filterSummary(): String = listOfNotNull(
    duration.takeIf { it != Duration.ANY }?.title,
    sort.takeIf { it != SortMode.SITE }?.title,
    "без просмотренных".takeIf { hideWatched },
).joinToString(" · ")

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val lib by app.library.data.collectAsState()
    val s = lib.settings
    var sheet by rememberSaveable { mutableStateOf(false) }
    HomeLayout(filtersActive = s.filtersActive, onFilter = { sheet = true }) { i ->
        val src = FeedSource(HOME_TABS[i].second, duration = s.duration)
        key(src.key) {
            FeedGrid(
                app.graph.feeds.get(src),
                header = if (!s.filtersActive) null else {
                    { item(span = { GridItemSpan(maxLineSpan) }, key = "filters", contentType = "filters") {
                        ActiveFiltersBar(s.filterSummary(), onOpen = { sheet = true }, onReset = { app.library.settings { it.resetFilters() } })
                    } }
                },
            )
        }
    }
    if (sheet) FilterSheet(onDismiss = { sheet = false })
}

/** Collapsing brand bar + swipeable feed tabs; [page] draws the content of tab i. */
@Composable
fun HomeLayout(filtersActive: Boolean, onFilter: () -> Unit, page: @Composable (Int) -> Unit) {
    val app = LocalApp.current
    val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pager = rememberPagerState { HOME_TABS.size }
    OnTabReselect { behavior.expand() }
    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
        Spacer(Modifier.statusBarsPadding())
        CollapsingHeader(behavior) {
            HomeTopBar(
                filtersActive = filtersActive,
                onSearch = { app.openSearch() },
                onRandom = { app.playRandom() },
                onFilter = onFilter,
                onSettings = { app.push(Screen.Settings) },
            )
        }
        TabsPager(HOME_TABS.map { it.first }, pager, page = page)
    }
}

@Composable
fun HomeTopBar(filtersActive: Boolean, onSearch: () -> Unit, onRandom: () -> Unit, onFilter: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Wordmark()
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Поиск", tint = Sfm.Text) }
        IconButton(onClick = onRandom) { Icon(Icons.Outlined.Casino, "Случайное видео", tint = Sfm.Text) }
        IconButton(onClick = onFilter) {
            Box {
                Icon(Icons.Outlined.Tune, if (filtersActive) "Вид и фильтры (включены)" else "Вид и фильтры", tint = Sfm.Text)
                if (filtersActive) Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(10.dp)
                        .border(2.dp, Sfm.Bg, CircleShape).padding(1.dp).background(Sfm.Accent, CircleShape),
                )
            }
        }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Настройки", tint = Sfm.Text) }
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        "SFM",
        modifier,
        style = TextStyle(brush = Sfm.brand, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp),
        maxLines = 1,
    )
}

@Composable
internal fun ActiveFiltersBar(summary: String, onOpen: () -> Unit, onReset: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sfm.Accent.copy(alpha = 0.09f)).clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp), tint = Sfm.Accent)
        Spacer(Modifier.width(10.dp))
        Text(summary, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "Сбросить",
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onReset).padding(horizontal = 10.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge, color = Sfm.Accent,
        )
    }
}

fun Settings.resetFilters() = copy(duration = Duration.ANY, sort = SortMode.SITE, hideWatched = false, columns = 0)

@Composable
fun FilterSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val lib by app.library.data.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Sfm.Surface) {
        FilterSheetContent(lib.settings, onChange = { n -> app.library.settings { n } })
    }
}

/** "Вид и фильтры": duration, sort, columns, hide watched; each change is saved right away. */
@Composable
fun FilterSheetContent(s: Settings, onChange: (Settings) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
        Text("Вид и фильтры", style = MaterialTheme.typography.titleLarge, color = Sfm.Text)
        Spacer(Modifier.height(14.dp))
        SheetLabel("Длительность")
        ChoiceChips(Duration.entries, s.duration, { it.title }) { onChange(s.copy(duration = it)) }
        Spacer(Modifier.height(14.dp))
        SheetLabel("Сортировка", "применяется к загруженным видео")
        ChoiceChips(SortMode.entries, s.sort, { it.title }) { onChange(s.copy(sort = it)) }
        Spacer(Modifier.height(14.dp))
        SheetLabel("Колонки")
        val cols = listOf(0 to "Авто", 1 to "1", 2 to "2", 3 to "3")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            cols.forEachIndexed { i, (n, label) ->
                SegmentedButton(
                    selected = s.columns == n,
                    onClick = { onChange(s.copy(columns = n)) },
                    shape = SegmentedButtonDefaults.itemShape(i, cols.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Sfm.Accent.copy(alpha = 0.16f), activeContentColor = Sfm.Accent, activeBorderColor = Sfm.Accent.copy(alpha = 0.6f),
                        inactiveContainerColor = Sfm.Surface, inactiveContentColor = Sfm.Text, inactiveBorderColor = Sfm.Outline,
                    ),
                    label = { Text(label, maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(s.copy(hideWatched = !s.hideWatched)) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Скрывать просмотренные", style = MaterialTheme.typography.bodyLarge, color = Sfm.Text)
                Text("Досмотренные почти до конца не попадут в ленты", style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = s.hideWatched,
                onCheckedChange = { onChange(s.copy(hideWatched = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = Sfm.Accent, checkedThumbColor = Sfm.Bg, uncheckedTrackColor = Sfm.Surface3, uncheckedBorderColor = Sfm.Outline, uncheckedThumbColor = Sfm.TextDim),
            )
        }
        Spacer(Modifier.height(12.dp))
        val dirty = s.filtersActive || s.columns != 0
        OutlinedButton(onClick = { onChange(s.resetFilters()) }, enabled = dirty, modifier = Modifier.fillMaxWidth().height(46.dp)) {
            Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Сбросить фильтры")
        }
    }
}

@Composable
private fun SheetLabel(text: String, hint: String? = null) {
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Sfm.Text)
        if (hint != null) {
            Spacer(Modifier.width(8.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        options.forEach { o ->
            val sel = o == selected
            FilterChip(
                selected = sel,
                onClick = { onSelect(o) },
                label = { Text(label(o), maxLines = 1) },
                leadingIcon = if (sel) { { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) } } else null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Sfm.Surface2, labelColor = Sfm.Text,
                    selectedContainerColor = Sfm.Accent.copy(alpha = 0.16f), selectedLabelColor = Sfm.Accent, selectedLeadingIconColor = Sfm.Accent,
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = sel, borderColor = Sfm.Outline, selectedBorderColor = Sfm.Accent.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(10.dp),
            )
        }
    }
}
