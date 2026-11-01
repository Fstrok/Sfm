package com.sfm.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.sfm.app.ui.theme.Sfm
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

/**
 * Tab row + pager. Swipe with the finger to the left opens the next tab (it slides in from the right),
 * to the right — the previous one. Tapping a tab animates to it.
 * [keys] identify pages when titles may repeat or the list changes (defaults to index + title).
 */
@Composable
fun TabsPager(
    titles: List<String>,
    state: PagerState,
    modifier: Modifier = Modifier,
    beyondViewport: Int = 0,
    keys: List<Any>? = null,
    page: @Composable (Int) -> Unit,
) {
    Column(modifier) {
        PagerTabRow(titles, state)
        HorizontalPager(
            state, Modifier.fillMaxSize(), beyondViewportPageCount = beyondViewport,
            key = { i -> keys?.getOrNull(i) ?: "$i:${titles.getOrElse(i) { "" }}" },
        ) { page(it) }
    }
}

private class TabMetrics { var x = IntArray(0); var w = IntArray(0); var text = IntArray(0) }

/**
 * Scrollable tab row whose indicator follows the finger during a swipe.
 * Few tabs stretch to fill the width; many tabs scroll and the selected one is kept centered.
 */
@Composable
fun PagerTabRow(titles: List<String>, state: PagerState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val m = remember { TabMetrics() }
    val last = (titles.size - 1).coerceAtLeast(0)

    LaunchedEffect(state.targetPage, titles.size) {
        val i = state.targetPage.coerceIn(0, last)
        if (i >= m.x.size || scroll.viewportSize == 0) return@LaunchedEffect
        scroll.animateScrollTo((m.x[i] + m.w[i] / 2 - scroll.viewportSize / 2).coerceIn(0, scroll.maxValue))
    }

    Column(modifier.fillMaxWidth()) {
        Layout(
            content = {
                titles.forEachIndexed { i, t ->
                    Box(
                        Modifier
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .semantics { role = Role.Tab; selected = state.currentPage == i }
                            .clickable { scope.launch { state.animateScrollToPage(i) } }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            t,
                            Modifier.graphicsLayer {
                                alpha = 1f - 0.42f * abs(state.currentPage + state.currentPageOffsetFraction - i).coerceAtMost(1f)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = Sfm.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    val w = 32.dp.toPx()
                    if (scroll.canScrollForward) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Sfm.Bg), size.width - w, size.width), Offset(size.width - w, 0f), Size(w, size.height))
                    if (scroll.canScrollBackward) drawRect(Brush.horizontalGradient(listOf(Sfm.Bg, Color.Transparent), 0f, w), Offset.Zero, Size(w, size.height))
                }
                .horizontalScroll(scroll)
                .drawBehind {
                    if (m.x.size != titles.size || titles.isEmpty()) return@drawBehind
                    val p = (state.currentPage + state.currentPageOffsetFraction).coerceIn(0f, last.toFloat())
                    val i = floor(p).toInt().coerceAtMost(last)
                    val j = (i + 1).coerceAtMost(last)
                    val f = p - i
                    val w = lerp(m.text[i].toFloat(), m.text[j].toFloat(), f)
                    val cx = lerp(m.x[i] + m.w[i] / 2f, m.x[j] + m.w[j] / 2f, f)
                    val h = 3.dp.toPx()
                    drawRoundRect(Sfm.Accent, Offset(cx - w / 2, size.height - h), Size(w, h), CornerRadius(h, h))
                },
        ) { ms, c ->
            val edge = 6.dp.roundToPx()
            val pad = 28.dp.roundToPx()
            val natural = ms.map { it.maxIntrinsicWidth(Constraints.Infinity) }
            val total = natural.sum() + edge * 2
            val extra = if (ms.isNotEmpty() && total < c.minWidth) (c.minWidth - total) / ms.size else 0
            val ps = ms.mapIndexed { i, it -> it.measure(Constraints.fixedWidth(natural[i] + extra)) }
            val h = ps.maxOfOrNull { it.height } ?: 0
            val xs = IntArray(ps.size)
            var x = edge
            ps.forEachIndexed { i, p -> xs[i] = x; x += p.width }
            m.x = xs; m.w = IntArray(ps.size) { ps[it].width }; m.text = IntArray(ps.size) { (natural[it] - pad).coerceAtLeast(pad) }
            layout(maxOf(x + edge, c.minWidth), h) { ps.forEachIndexed { i, p -> p.placeRelative(xs[i], 0) } }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Sfm.Outline.copy(alpha = 0.55f)))
    }
}
