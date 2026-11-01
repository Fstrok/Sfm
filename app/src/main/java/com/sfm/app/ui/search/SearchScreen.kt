@file:OptIn(ExperimentalLayoutApi::class)

package com.sfm.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sfm.app.data.CatalogData
import com.sfm.app.data.FeedKind
import com.sfm.app.data.FeedSource
import com.sfm.app.data.Term
import com.sfm.app.data.TermType
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.catalog.CatalogLoader
import com.sfm.app.ui.common.FeedGrid
import com.sfm.app.ui.common.SearchField
import com.sfm.app.ui.common.SectionTitle
import com.sfm.app.ui.common.TermAvatar
import com.sfm.app.ui.common.TermChip
import com.sfm.app.ui.common.highlight
import com.sfm.app.ui.common.icon
import com.sfm.app.ui.common.shimmer
import com.sfm.app.ui.common.termColor
import com.sfm.app.ui.common.videosLabel
import com.sfm.app.ui.theme.Sfm
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(initial: String) {
    val app = LocalApp.current
    val lib by app.library.data.collectAsState()
    val cat by app.graph.catalog.data.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var submitted by rememberSaveable { mutableStateOf(initial.trim().ifEmpty { null }) }
    var autoFocused by rememberSaveable { mutableStateOf(false) }
    var typed by remember { mutableStateOf(field.text) }

    fun submit(q: String) {
        val s = q.trim()
        if (s.isEmpty()) return
        app.library.addSearch(s)
        field = TextFieldValue(s, TextRange(s.length))
        submitted = s
        keyboard?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(Unit) {
        CatalogLoader.load(app.graph)
        submitted?.let { if (lib.searches.firstOrNull() != it) app.library.addSearch(it) }
        // The user explicitly opened search: focus once, not on every return to the screen.
        if (submitted == null && !autoFocused) { autoFocused = true; runCatching { focus.requestFocus() } }
    }
    LaunchedEffect(field.text) { if (field.text.isNotBlank()) delay(250); typed = field.text.trim() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().height(64.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { app.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад", tint = Sfm.Text) }
            SearchField(
                field,
                { v -> field = v; if (v.text.trim() != submitted) submitted = null },
                "Видео, персонажи, игры",
                Modifier.weight(1f),
                focusRequester = focus,
                onSearch = { submit(field.text) },
                onClear = { field = TextFieldValue(""); submitted = null; runCatching { focus.requestFocus() }; keyboard?.show() },
            )
        }
        val q = submitted
        when {
            q != null -> SearchResults(q, cat)
            typed.isEmpty() -> SearchIdle(
                recent = lib.searches, data = cat,
                onRecent = ::submit, onClearRecent = app.library::clearSearches, onOpen = app::openTerm,
            )
            else -> SearchSuggestions(
                query = typed,
                terms = remember(typed, cat) { app.graph.catalog.search(typed, 20) },
                recent = lib.searches.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }.take(3),
                data = cat,
                onSubmit = ::submit,
                onFill = { s -> field = TextFieldValue("$s ", TextRange(s.length + 1)) },
                onOpen = app::openTerm,
            )
        }
    }
}

@Composable
private fun SearchResults(q: String, cat: CatalogData) {
    val app = LocalApp.current
    val lib by app.library.data.collectAsState()
    val terms = remember(q, cat) { app.graph.catalog.search(q, 12) }
    val src = FeedSource(FeedKind.SEARCH, query = q, duration = lib.settings.duration)
    key(src.key) {
        FeedGrid(
            app.graph.feeds.get(src),
            emptyTitle = "Ничего не найдено",
            emptyText = "Попробуйте другое слово",
            header = if (terms.isEmpty()) null else {
                {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "terms", contentType = "terms") {
                        LazyRow(
                            Modifier.padding(bottom = 2.dp), contentPadding = PaddingValues(horizontal = 0.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(terms, key = { "${it.type}:${it.id}" }) { t -> TermChip(t.name, { app.openTerm(t) }, icon = t.type.icon, count = t.count, tint = termColor(t)) }
                        }
                    }
                }
            },
        )
    }
}

/** Before typing: recent searches and popular games/characters. */
@Composable
fun SearchIdle(recent: List<String>, data: CatalogData, onRecent: (String) -> Unit, onClearRecent: () -> Unit, onOpen: (Term) -> Unit) {
    val games = remember(data) { data.games.sortedByDescending { it.count }.take(10) }
    val chars = remember(data) { data.characters.sortedByDescending { it.count }.take(16) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(bottom = 16.dp)) {
        if (recent.isNotEmpty()) {
            SectionTitle("Недавние запросы", action = "Очистить", onAction = onClearRecent)
            ChipFlow { recent.take(12).forEach { s -> TermChip(s, { onRecent(s) }, icon = Icons.Outlined.History, tint = Sfm.TextDim) } }
            Spacer(Modifier.height(8.dp))
        }
        SectionTitle("Популярные игры")
        if (games.isEmpty()) ChipSkeleton() else ChipFlow { games.forEach { t -> TermChip(t.name, { onOpen(t) }, icon = t.type.icon, tint = termColor(t)) } }
        Spacer(Modifier.height(8.dp))
        SectionTitle("Популярные персонажи")
        if (chars.isEmpty()) ChipSkeleton() else ChipFlow { chars.forEach { t -> TermChip(t.name, { onOpen(t) }, icon = Icons.AutoMirrored.Outlined.TrendingUp, tint = termColor(t)) } }
    }
}

/** While typing: "search for …", matching recent queries and catalog terms. */
@Composable
fun SearchSuggestions(
    query: String,
    terms: List<Term>,
    recent: List<String>,
    data: CatalogData,
    onSubmit: (String) -> Unit,
    onFill: (String) -> Unit,
    onOpen: (Term) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "go") { SuggestRow(Icons.Outlined.Search, "Искать «$query»", null, onClick = { onSubmit(query) }) }
        items(recent, key = { "r:$it" }) { s -> SuggestRow(Icons.Outlined.History, s, null, onClick = { onSubmit(s) }, onFill = { onFill(s) }, query = query) }
        items(terms, key = { "${it.type}:${it.id}" }) { t ->
            val game = if (t.type == TermType.CHARACTER) data.byCat[t.parent]?.name else null
            Row(Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TermAvatar(t, size = 38.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(highlight(t.name, query), style = MaterialTheme.typography.titleSmall, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(t.type.title, game, videosLabel(t.count)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(t.type.icon, null, Modifier.size(18.dp), tint = Sfm.TextDim)
            }
        }
    }
}

@Composable
private fun SuggestRow(icon: ImageVector, text: String, sub: String?, onClick: () -> Unit, onFill: (() -> Unit)? = null, query: String? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = Sfm.TextDim) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(highlight(text, query), style = MaterialTheme.typography.bodyLarge, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, maxLines = 1)
        }
        if (onFill != null) IconButton(onClick = onFill) { Icon(Icons.Outlined.NorthWest, "Подставить в поиск", Modifier.size(18.dp), tint = Sfm.TextDim) }
    }
}

@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ChipSkeleton() = ChipFlow {
    listOf(96, 132, 80, 110, 70, 124, 90).forEach { w -> Box(Modifier.width(w.dp).height(34.dp).clip(RoundedCornerShape(17.dp)).shimmer()) }
}
