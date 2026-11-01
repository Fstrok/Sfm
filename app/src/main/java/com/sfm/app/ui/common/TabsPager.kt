package com.sfm.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sfm.app.ui.theme.Sfm
import kotlinx.coroutines.launch

/**
 * Tab row + pager. Swipe with the finger to the left opens the next tab (it slides in from the right),
 * to the right — the previous one. Tapping a tab animates to it.
 */
@Composable
fun TabsPager(
    titles: List<String>,
    state: PagerState,
    modifier: Modifier = Modifier,
    beyondViewport: Int = 0,
    page: @Composable (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(modifier) {
        PrimaryScrollableTabRow(
            selectedTabIndex = state.currentPage.coerceIn(0, (titles.size - 1).coerceAtLeast(0)),
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Sfm.Text,
            divider = {},
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffset(state.currentPage.coerceIn(0, (titles.size - 1).coerceAtLeast(0)), matchContentSize = true),
                    width = androidx.compose.ui.unit.Dp.Unspecified,
                    color = Sfm.Accent,
                )
            },
        ) {
            titles.forEachIndexed { i, t ->
                val sel = state.currentPage == i
                Tab(
                    selected = sel,
                    onClick = { scope.launch { state.animateScrollToPage(i) } },
                    text = { Text(t, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1) },
                    selectedContentColor = Sfm.Text,
                    unselectedContentColor = Sfm.TextDim,
                )
            }
        }
        HorizontalPager(state, Modifier.fillMaxSize(), beyondViewportPageCount = beyondViewport, key = { titles.getOrElse(it) { "$it" } }) { page(it) }
    }
}
