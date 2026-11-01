package com.sfm.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sfm.app.data.FeedModel
import com.sfm.app.data.LoadState
import com.sfm.app.data.Video
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.theme.Sfm
import kotlinx.coroutines.flow.distinctUntilChanged

fun gridCells(columns: Int): GridCells = if (columns <= 0) GridCells.Adaptive(160.dp) else GridCells.Fixed(columns)

/**
 * Paged feed grid: skeletons on first load, pull-to-refresh, endless loading, error with retry,
 * hidden/blacklisted videos filtered out, scroll position kept in the [FeedModel].
 */
@Composable
fun FeedGrid(
    feed: FeedModel,
    modifier: Modifier = Modifier,
    emptyTitle: String = "Ничего не найдено",
    emptyText: String? = "Попробуйте другой фильтр",
    contentPadding: PaddingValues = PaddingValues(12.dp),
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val app = LocalApp.current
    val items by feed.items.collectAsState()
    val state by feed.state.collectAsState()
    val refreshing by feed.refreshing.collectAsState()
    val lib by app.library.data.collectAsState()
    val visible = remember(items, lib) { lib.visible(items) }
    val grid = rememberLazyGridState(feed.scrollIndex, feed.scrollOffset)

    LaunchedEffect(feed) { feed.ensure() }
    DisposableEffect(feed) { onDispose { feed.scrollIndex = grid.firstVisibleItemIndex; feed.scrollOffset = grid.firstVisibleItemScrollOffset } }
    // Re-checked on every change of the raw list or load state: a page filtered out locally keeps paging going.
    LaunchedEffect(grid, visible.size, items.size, state) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= grid.layoutInfo.totalItemsCount - 8 && feed.canLoadMore) feed.loadMore() }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = feed::refresh, modifier = modifier.fillMaxSize()) {
        when {
            visible.isEmpty() && (state == LoadState.Loading || state == LoadState.Idle) && items.isEmpty() ->
                SkeletonGrid(lib.settings.columns, contentPadding, header)
            visible.isEmpty() && state is LoadState.Error ->
                LazyVerticalGrid(gridCells(lib.settings.columns), Modifier.fillMaxSize(), contentPadding = contentPadding) {
                    header?.invoke(this)
                    item(span = { GridItemSpan(maxLineSpan) }) { ErrorState((state as LoadState.Error).message) { feed.retry() } }
                }
            else -> VideoGridContent(
                videos = visible,
                grid = grid,
                columns = lib.settings.columns,
                contentPadding = contentPadding,
                header = header,
                onOpen = { i -> app.player.open(visible, i, feed) },
                footer = {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "footer", contentType = "footer") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            when (val s = state) {
                                LoadState.Loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                                is LoadState.Error -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(s.message, color = Sfm.TextDim, style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = feed::retry) { Text("Повторить") }
                                }
                                LoadState.End -> if (visible.isEmpty()) EmptyState(Icons.Outlined.SearchOff, emptyTitle, emptyText)
                                else Text("Больше видео нет", color = Sfm.TextDim, style = MaterialTheme.typography.bodySmall)
                                LoadState.Idle -> if (visible.isEmpty()) TextButton(onClick = feed::loadMore) { Text("Загрузить ещё") }
                            }
                        }
                    }
                },
            )
        }
    }
}

/** Grid of cards for any list (feeds, favorites, history…). */
@Composable
fun VideoGridContent(
    videos: List<Video>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    grid: LazyGridState = rememberLazyGridState(),
    columns: Int = LocalApp.current.library.settings.columns,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    header: (LazyGridScope.() -> Unit)? = null,
    footer: (LazyGridScope.() -> Unit)? = null,
    onMenu: ((Video) -> Unit)? = null,
) {
    val app = LocalApp.current
    val lib by app.library.data.collectAsState()
    val dl by app.dlProgress.collectAsState()
    var menuFor by remember { mutableStateOf<Video?>(null) }
    OnTabReselect { grid.scrollToTop() }
    LazyVerticalGrid(
        gridCells(columns),
        modifier.fillMaxSize(),
        state = grid,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        header?.invoke(this)
        itemsIndexed(videos, key = { _, v -> v.id }, contentType = { _, _ -> "video" }) { i, v ->
            VideoCard(
                v = v,
                isFavorite = v.id in lib.favIds,
                history = lib.historyById[v.id],
                download = dl[v.id],
                isDownloaded = lib.downloadsById[v.id] != null,
                onClick = { onOpen(i) },
                onFavorite = { app.toggleFavorite(v) },
                onDownload = { if (lib.downloadsById[v.id] == null || dl[v.id]?.failed == true) app.download(v) else app.toast("Уже скачано — вкладка «Библиотека → Скачанное»") },
                onMenu = { if (onMenu != null) onMenu(v) else menuFor = v },
                modifier = Modifier.animateItem(),
            )
        }
        footer?.invoke(this)
    }
    menuFor?.let { v -> VideoMenuSheet(v, onDismiss = { menuFor = null }) }
}

@Composable
private fun SkeletonGrid(columns: Int, contentPadding: PaddingValues, header: (LazyGridScope.() -> Unit)?) {
    LazyVerticalGrid(
        gridCells(columns), Modifier.fillMaxSize(), contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), userScrollEnabled = false,
    ) {
        header?.invoke(this)
        items(12) { VideoCardSkeleton() }
    }
}
