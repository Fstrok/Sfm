package com.sfm.app.ui.player

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sfm.app.data.Video
import kotlin.math.abs

/**
 * Gallery-style queue pager: finger moves left → next video slides in from the right,
 * finger moves right → previous. Two-way synced with [index]: the settled page reports
 * [onIndexChange], external index changes (buttons, notification) scroll the pager.
 * [page] gets `active = true` only for the settled current page (it hosts the video surface).
 */
@Composable
fun VideoPager(
    items: List<Video>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    page: @Composable (index: Int, video: Video, active: Boolean) -> Unit,
) {
    val state = rememberPagerState(initialPage = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val currentIndex by rememberUpdatedState(index)
    val report by rememberUpdatedState(onIndexChange)
    val count by rememberUpdatedState(items.size)

    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.collect { p -> if (p != currentIndex && p < count) report(p) }
    }
    LaunchedEffect(index, items.size) {
        if (index !in items.indices || state.isScrollInProgress) return@LaunchedEffect
        if (state.currentPage != index) {
            if (abs(state.currentPage - index) == 1) state.animateScrollToPage(index) else state.scrollToPage(index)
        }
    }
    HorizontalPager(
        state = state,
        modifier = modifier.testTag("videoPager"),
        beyondViewportPageCount = 1,
        userScrollEnabled = userScrollEnabled,
        key = { items.getOrNull(it)?.id ?: it.toLong() },
    ) { i ->
        val v = items.getOrNull(i) ?: return@HorizontalPager
        page(i, v, i == index && state.settledPage == i)
    }
}
