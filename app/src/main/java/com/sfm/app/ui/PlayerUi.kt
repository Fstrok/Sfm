package com.sfm.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sfm.app.data.FeedModel
import com.sfm.app.data.Video

enum class PlayerMode { CLOSED, EXPANDED, MINI }

/**
 * Player UI contract used by every screen: open a queue, minimize, expand, close.
 * The player implementation (package com.sfm.app.player / ui.player) observes this state.
 */
class PlayerUi(private val app: AppState) {
    var queue by mutableStateOf<List<Video>>(emptyList())
    var index by mutableIntStateOf(0)
    /** Feed the queue came from: the player asks it for more items near the end. */
    var feed: FeedModel? by mutableStateOf(null)
    var mode by mutableStateOf(PlayerMode.CLOSED)
    /** Bumped on every [open]: a brand-new queue (the pager is rebuilt instead of scrolled). */
    var session by mutableIntStateOf(0)
        private set

    val current: Video? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index < queue.lastIndex
    val hasPrev: Boolean get() = index > 0

    fun open(list: List<Video>, start: Int, feed: FeedModel? = null) {
        if (list.isEmpty()) return
        this.feed = feed
        queue = list.distinctBy { it.id }
        index = queue.indexOfFirst { it.id == list[start.coerceIn(0, list.lastIndex)].id }.coerceAtLeast(0)
        session++
        mode = PlayerMode.EXPANDED
    }

    fun openOne(v: Video) = open(listOf(v), 0, null)

    fun minimize() { if (mode == PlayerMode.EXPANDED) mode = PlayerMode.MINI }
    fun expand() { if (mode != PlayerMode.CLOSED) mode = PlayerMode.EXPANDED }
    fun close() { mode = PlayerMode.CLOSED; queue = emptyList(); index = 0; feed = null }

    fun next() { if (hasNext) index++ }
    fun prev() { if (hasPrev) index-- }

    /** Appends videos that are not in the queue yet; the current index stays where it is. */
    fun append(items: List<Video>) {
        if (items.isEmpty() || queue.isEmpty()) return
        val known = queue.mapTo(HashSet()) { it.id }
        val fresh = items.filter { known.add(it.id) }
        if (fresh.isNotEmpty()) queue = queue + fresh
    }

    /** Replaces a queue item with fresher data (resolved url, verified duration, score). */
    fun update(v: Video) {
        val i = queue.indexOfFirst { it.id == v.id }
        if (i >= 0 && queue[i] != v) queue = queue.toMutableList().also { it[i] = v }
    }

    /** Drops a video from the queue (e.g. hidden by the user); closes the player when nothing is left. */
    fun remove(id: Long) {
        val i = queue.indexOfFirst { it.id == id }
        if (i < 0) return
        if (queue.size == 1) { close(); return }
        queue = queue.toMutableList().also { it.removeAt(i) }
        if (i < index || index > queue.lastIndex) index = (index - 1).coerceAtLeast(0)
    }
}
