package com.sfm.app.ui.common

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.Screen
import com.sfm.app.ui.theme.Sfm

/**
 * Header that slides away while the list scrolls up and comes back on any scroll down (enterAlways).
 * Attach `behavior.nestedScrollConnection` to a parent of both the header and the list.
 */
@Composable
fun CollapsingHeader(behavior: TopAppBarScrollBehavior, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val st = behavior.state
    Layout(content, modifier.fillMaxWidth().clipToBounds()) { ms, c ->
        val ps = ms.map { it.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity)) }
        val h = ps.maxOfOrNull { it.height } ?: 0
        if (st.heightOffsetLimit != -h.toFloat()) st.heightOffsetLimit = -h.toFloat()
        val off = st.heightOffset.toInt().coerceIn(-h, 0)
        layout(c.maxWidth, h + off) { ps.forEach { it.placeRelative(0, off) } }
    }
}

/** Smoothly brings a collapsed header back. */
suspend fun TopAppBarScrollBehavior.expand() {
    if (state.heightOffset != 0f) animate(state.heightOffset, 0f) { v, _ -> state.heightOffset = v }
}

/**
 * Runs [action] when the already-selected bottom tab is tapped again while the main screen is shown
 * (screens scroll their list to top). Not triggered on first composition.
 */
@Composable
fun OnTabReselect(action: suspend () -> Unit) {
    val app = LocalApp.current
    val signal = app.tabReselect
    val start = remember { signal }
    val current by rememberUpdatedState(action)
    LaunchedEffect(signal) { if (signal != start && app.top == Screen.Main) current() }
}

suspend fun LazyGridState.scrollToTop() {
    if (firstVisibleItemIndex > 12) scrollToItem(6)
    animateScrollToItem(0)
}

suspend fun LazyListState.scrollToTop() {
    if (firstVisibleItemIndex > 12) scrollToItem(6)
    animateScrollToItem(0)
}

/** Large screen title with an optional subtitle and trailing actions. */
@Composable
fun TopTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}
