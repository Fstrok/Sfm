@file:OptIn(ExperimentalLayoutApi::class)

package com.sfm.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sfm.app.Graph
import com.sfm.app.data.CatalogData
import com.sfm.app.data.FeedModel
import com.sfm.app.data.LibraryData
import com.sfm.app.data.Term
import com.sfm.app.data.TermType
import com.sfm.app.data.timeAgo
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.common.CollapsingHeader
import com.sfm.app.ui.common.EmptyState
import com.sfm.app.ui.common.ErrorState
import com.sfm.app.ui.common.OnTabReselect
import com.sfm.app.ui.common.SearchField
import com.sfm.app.ui.common.SubscribeBell
import com.sfm.app.ui.common.TabsPager
import com.sfm.app.ui.common.TagChip
import com.sfm.app.ui.common.TermRow
import com.sfm.app.ui.common.TopTitle
import com.sfm.app.ui.common.expand
import com.sfm.app.ui.common.initials
import com.sfm.app.ui.common.pluralRu
import com.sfm.app.ui.common.scrollToTop
import com.sfm.app.ui.common.shimmer
import com.sfm.app.ui.common.spaced
import com.sfm.app.ui.common.termColor
import com.sfm.app.ui.common.videosLabel
import com.sfm.app.ui.theme.Sfm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class CatalogSort(val title: String) { POPULAR("Популярные"), ALPHA("А–Я") }

/** Full-catalog loading shared by Catalog and Search; state survives navigation. */
object CatalogLoader {
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var job: Job? = null

    fun load(graph: Graph, force: Boolean = false, onError: ((String) -> Unit)? = null) {
        if (job?.isActive == true) return
        job = graph.scope.launch(Dispatchers.Default) {
            loading.value = true
            error.value = null
            try {
                graph.catalog.ensureAll(force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val m = FeedModel.errorText(e)
                error.value = m
                onError?.invoke(m)
            } finally {
                loading.value = false
            }
        }
    }
}

private val TABS = listOf("Игры", "Персонажи", "Авторы", "Теги")
private val PLACEHOLDERS = listOf("Поиск игр", "Поиск персонажей", "Поиск авторов", "Поиск тегов")

/** Catalog lists after search filter and sort. */
data class CatalogLists(val games: List<Term>, val characters: List<Term>, val artists: List<Term>, val tags: List<Term>) {
    operator fun get(tab: Int) = when (tab) { 0 -> games; 1 -> characters; 2 -> artists; else -> tags }

    companion object {
        /** [ranked] = fuzzy search result for the query (null when there is no query): keeps its relevance order. */
        fun build(d: CatalogData, ranked: List<Term>?, sort: CatalogSort): CatalogLists {
            val rank = ranked?.withIndex()?.associate { (i, t) -> (t.type to t.id) to i }
            fun prep(l: List<Term>): List<Term> {
                val f = if (rank == null) l else l.filter { (it.type to it.id) in rank }
                return when {
                    sort == CatalogSort.ALPHA -> f.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.trim() })
                    rank != null -> f.sortedBy { rank[it.type to it.id] }
                    else -> f.sortedByDescending { it.count }
                }
            }
            return CatalogLists(prep(d.games), prep(d.characters), prep(d.artists), prep(d.plainTags))
        }
    }
}

@Composable
fun CatalogScreen() {
    val app = LocalApp.current
    val data by app.graph.catalog.data.collectAsState()
    val lib by app.library.data.collectAsState()
    val loading by CatalogLoader.loading.collectAsState()
    val error by CatalogLoader.error.collectAsState()
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var q by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(CatalogSort.POPULAR) }
    val pager = rememberPagerState { TABS.size }
    val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(Unit) { CatalogLoader.load(app.graph) }
    LaunchedEffect(query.text) { if (query.text.isNotBlank()) delay(200); q = query.text.trim() }
    OnTabReselect { behavior.expand() }
    val lists = remember(data, q, sort) { CatalogLists.build(data, q.takeIf { it.isNotEmpty() }?.let { app.graph.catalog.search(it, Int.MAX_VALUE) }, sort) }
    val at = listOf(data.categoriesAt, data.tagsAt).filter { it > 0 }.minOrNull()

    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
        Spacer(Modifier.statusBarsPadding())
        CollapsingHeader(behavior) {
            TopTitle(
                "Каталог",
                subtitle = when { loading -> "обновляется…"; at != null -> "обновлено ${timeAgo(at)}"; else -> null },
            ) {
                IconButton(onClick = {
                    CatalogLoader.load(app.graph, force = true) { m -> if (app.graph.catalog.data.value.categories.isNotEmpty()) app.toast("Не удалось обновить: $m") }
                }, enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Sfm.Accent)
                    else Icon(Icons.Outlined.Refresh, "Обновить каталог", tint = Sfm.Text)
                }
            }
        }
        SearchField(query, { query = it }, PLACEHOLDERS[pager.currentPage.coerceIn(0, 3)], Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp))
        TabsPager(TABS, pager) { i ->
            val items = lists[i]
            val needTags = i >= 2
            val empty = if (needTags) data.tags.isEmpty() else data.categories.isEmpty()
            when {
                empty && error != null && !loading -> ErrorState(error!!, Modifier.fillMaxSize()) { CatalogLoader.load(app.graph) }
                empty -> CatalogSkeleton(i)
                else -> CatalogPage(
                    tab = i, items = items, data = data, lib = lib, sort = sort, query = q,
                    onSort = { sort = it },
                    onOpen = app::openTerm,
                    onToggleSub = app::toggleSub,
                )
            }
        }
    }
}

/** One catalog tab with real data: games grid, character/artist lists or tag chips. */
@Composable
fun CatalogPage(
    tab: Int,
    items: List<Term>,
    data: CatalogData,
    lib: LibraryData,
    sort: CatalogSort,
    query: String,
    onSort: (CatalogSort) -> Unit,
    onOpen: (Term) -> Unit,
    onToggleSub: (Term) -> Unit,
) {
    val info: @Composable () -> Unit = {
        val n = items.size
        val label = if (query.isNotEmpty()) "Найдено: ${n.spaced()}" else "${n.spaced()} " + when (tab) {
            0 -> pluralRu(n, "игра", "игры", "игр")
            1 -> pluralRu(n, "персонаж", "персонажа", "персонажей")
            2 -> pluralRu(n, "автор", "автора", "авторов")
            else -> pluralRu(n, "тег", "тега", "тегов")
        }
        InfoRow(label, sort, onSort)
    }
    if (items.isEmpty() && query.isNotEmpty()) {
        Column(Modifier.fillMaxSize()) {
            info()
            EmptyState(Icons.Outlined.SearchOff, "Ничего не найдено", "Попробуйте другое слово")
        }
        return
    }
    val letters = sort == CatalogSort.ALPHA && query.isEmpty()
    when (tab) {
        0 -> {
            val chars = remember(data) { data.characters.groupBy { it.parent }.mapValues { (_, v) -> v.sortedByDescending { it.count } } }
            val grid = rememberLazyGridState()
            OnTabReselect { grid.scrollToTop() }
            LazyVerticalGrid(
                GridCells.Adaptive(140.dp), Modifier.fillMaxSize(), grid,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "info", contentType = "info") { Box(Modifier.offset(x = (-4).dp)) { info() } }
                items(items, key = { it.id }, contentType = { "game" }) { g ->
                    GameCard(g, chars[g.id].orEmpty(), lib.isSubscribed(g), { onOpen(g) }, { onToggleSub(g) }, Modifier.animateItem())
                }
            }
        }
        3 -> {
            val list = rememberLazyListState()
            OnTabReselect { list.scrollToTop() }
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "info", contentType = "info") { info() }
                val groups = if (letters) items.groupBy { letterOf(it.name) }.toList() else listOf("" to items)
                groups.forEach { (letter, group) ->
                    if (letter.isNotEmpty()) stickyHeader(key = "h:$letter", contentType = "letter") { LetterHeader(letter) }
                    group.chunked(48).forEachIndexed { ci, chunk ->
                        item(key = "c:$letter:$ci", contentType = "chips") {
                            FlowRow(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                chunk.forEach { t -> TagChip(t, lib.isSubscribed(t), { onOpen(t) }, { onToggleSub(t) }) }
                            }
                        }
                    }
                }
            }
        }
        else -> {
            val list = rememberLazyListState()
            OnTabReselect { list.scrollToTop() }
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "info", contentType = "info") { info() }
                termRows(items, letters) { t ->
                    val game = if (t.type == TermType.CHARACTER) data.byCat[t.parent]?.name else null
                    TermRow(
                        t, listOfNotNull(game, videosLabel(t.count)).joinToString(" · "), lib.isSubscribed(t),
                        onClick = { onOpen(t) }, onToggleSub = { onToggleSub(t) }, query = query,
                    )
                }
            }
        }
    }
}

private fun letterOf(name: String): String = name.trim().firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"

private fun LazyListScope.termRows(items: List<Term>, letters: Boolean, row: @Composable (Term) -> Unit) {
    if (!letters) {
        items(items, key = { "${it.type}:${it.id}" }, contentType = { "row" }) { row(it) }
        return
    }
    items.groupBy { letterOf(it.name) }.forEach { (letter, group) ->
        stickyHeader(key = "h:$letter", contentType = "letter") { LetterHeader(letter) }
        items(group, key = { "${it.type}:${it.id}" }, contentType = { "row" }) { row(it) }
    }
}

@Composable
private fun LetterHeader(letter: String) {
    Text(
        letter,
        Modifier.fillMaxWidth().background(Sfm.Bg).padding(horizontal = 18.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleSmall, color = Sfm.Accent,
    )
}

@Composable
private fun InfoRow(label: String, sort: CatalogSort, onSort: (CatalogSort) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Sfm.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        SortToggle(sort, onSort)
    }
}

/** Compact two-segment switch: popular / alphabetical. */
@Composable
fun SortToggle(sort: CatalogSort, onSort: (CatalogSort) -> Unit) {
    Row(Modifier.height(32.dp).clip(CircleShape).background(Sfm.Surface2).padding(3.dp)) {
        CatalogSort.entries.forEach { s ->
            val sel = s == sort
            Box(
                Modifier.fillMaxHeight().clip(CircleShape).background(if (sel) Sfm.Surface3 else Sfm.Surface2)
                    .clickable { onSort(s) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.title, style = MaterialTheme.typography.labelMedium, color = if (sel) Sfm.Accent else Sfm.TextDim, maxLines = 1)
            }
        }
    }
}

/** Game tile: tinted gradient, monogram watermark, name, video count and top characters. */
@Composable
fun GameCard(g: Term, chars: List<Term>, subscribed: Boolean, onClick: () -> Unit, onToggleSub: () -> Unit, modifier: Modifier = Modifier) {
    val c = termColor(g)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.fillMaxWidth().height(136.dp).clip(shape).background(Sfm.Surface)
            .background(Brush.linearGradient(listOf(c.copy(alpha = 0.30f), c.copy(alpha = 0.06f), Sfm.Surface.copy(alpha = 0f))))
            .border(1.dp, c.copy(alpha = 0.16f), shape)
            .clickable(onClick = onClick),
    ) {
        Text(
            initials(g.name), Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 22.dp),
            fontSize = 84.sp, fontWeight = FontWeight.Black, color = c.copy(alpha = 0.09f), maxLines = 1,
        )
        Column(Modifier.fillMaxSize().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 10.dp)) {
            Text(g.name, Modifier.padding(end = 30.dp), style = MaterialTheme.typography.titleSmall, color = Sfm.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(videosLabel(g.count), style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (chars.isNotEmpty()) {
                val more = chars.size - 3
                Text(
                    chars.take(3).joinToString(", ") { it.name } + if (more > 0) " +$more" else "",
                    style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SubscribeBell(subscribed, g.name, onToggleSub, Modifier.align(Alignment.TopEnd).padding(2.dp), iconSize = 20.dp)
    }
}

@Composable
private fun CatalogSkeleton(tab: Int) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(120.dp).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        when (tab) {
            0 -> repeat(4) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) { Box(Modifier.weight(1f).height(136.dp).clip(RoundedCornerShape(16.dp)).shimmer()) }
                }
            }
            3 -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(84, 120, 64, 96, 140, 72, 110, 90, 60, 130, 100, 76, 116, 88, 68, 124).forEach { w ->
                    Box(Modifier.width(w.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).shimmer())
                }
            }
            else -> repeat(9) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).shimmer())
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(150.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                        Box(Modifier.width(96.dp).height(10.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                    }
                }
            }
        }
    }
}
