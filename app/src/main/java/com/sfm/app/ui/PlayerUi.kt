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

    val current: Video? get() = queue.getOrNull(index)

    fun open(list: List<Video>, start: Int, feed: FeedModel? = null) {
        if (list.isEmpty()) return
        this.feed = feed
        queue = list
        index = start.coerceIn(0, list.lastIndex)
        mode = PlayerMode.EXPANDED
    }

    fun openOne(v: Video) = open(listOf(v), 0, null)

    fun minimize() { if (mode == PlayerMode.EXPANDED) mode = PlayerMode.MINI }
    fun expand() { if (mode != PlayerMode.CLOSED) mode = PlayerMode.EXPANDED }
    fun close() { mode = PlayerMode.CLOSED; queue = emptyList(); index = 0; feed = null }
}
