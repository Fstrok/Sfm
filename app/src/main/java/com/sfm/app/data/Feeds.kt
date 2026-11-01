package com.sfm.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data class Error(val message: String) : LoadState
    data object End : LoadState
}

/** Paged list for one [FeedSource]. Lives in [Feeds] so lists and scroll survive navigation. */
class FeedModel(val source: FeedSource, private val repo: Repo, private val scope: CoroutineScope) {
    private val _items = MutableStateFlow<List<Video>>(emptyList())
    val items: StateFlow<List<Video>> = _items
    private val _state = MutableStateFlow<LoadState>(LoadState.Idle)
    val state: StateFlow<LoadState> = _state
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing
    var loadedAt = 0L; private set
    private var page = 0
    private var job: Job? = null

    /** Grid scroll position, kept here so it survives leaving the screen. */
    var scrollIndex = 0
    var scrollOffset = 0

    val canLoadMore get() = _state.value == LoadState.Idle

    /** Loads the first page only if nothing is loaded yet. */
    fun ensure() { if (page == 0 && job?.isActive != true) loadMore() }

    fun loadMore() {
        if (job?.isActive == true || _state.value == LoadState.End) return
        job = scope.launch {
            _state.value = LoadState.Loading
            try {
                val p = repo.feed(source, page + 1)
                page++
                val known = _items.value.mapTo(HashSet()) { it.id }
                _items.value = _items.value + p.items.filter { it.id !in known }
                loadedAt = System.currentTimeMillis()
                _state.value = if (p.hasMore) LoadState.Idle else LoadState.End
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = LoadState.Error(errorText(e))
            }
        }
    }

    fun retry() { if (_state.value is LoadState.Error) { _state.value = LoadState.Idle; loadMore() } }

    /** Pull-to-refresh: reloads page 1 bypassing nothing cached; keeps old items on failure. */
    fun refresh() {
        job?.cancel()
        job = scope.launch {
            _refreshing.value = true
            try {
                val p = repo.feed(source, 1)
                page = 1
                _items.value = p.items
                scrollIndex = 0; scrollOffset = 0
                loadedAt = System.currentTimeMillis()
                _state.value = if (p.hasMore) LoadState.Idle else LoadState.End
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = LoadState.Error(errorText(e))
            } finally {
                _refreshing.value = false
            }
        }
    }

    companion object {
        fun errorText(e: Throwable): String = when (e) {
            is SiteException -> e.message ?: "Сайт не ответил"
            is java.net.UnknownHostException, is java.net.ConnectException -> "Нет сети или сайт не отвечает"
            is java.net.SocketTimeoutException -> "Сайт долго не отвечает"
            else -> e.message ?: "Ошибка загрузки"
        }
    }
}

class Feeds(private val repo: Repo, private val scope: CoroutineScope) {
    private val map = object : LinkedHashMap<String, FeedModel>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FeedModel>) = size > 40
    }

    @Synchronized
    fun get(source: FeedSource): FeedModel = map.getOrPut(source.key) { FeedModel(source, repo, scope) }
}
