package com.sfm.app.ui.subs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNotifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sfm.app.data.FeedKind
import com.sfm.app.data.FeedSource
import com.sfm.app.data.Term
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.Tab
import com.sfm.app.ui.common.CollapsingHeader
import com.sfm.app.ui.common.EmptyState
import com.sfm.app.ui.common.FeedGrid
import com.sfm.app.ui.common.OnTabReselect
import com.sfm.app.ui.common.TabsPager
import com.sfm.app.ui.common.TermAvatar
import com.sfm.app.ui.common.TopTitle
import com.sfm.app.ui.common.expand
import com.sfm.app.ui.common.pluralRu
import com.sfm.app.ui.common.videosLabel
import com.sfm.app.ui.theme.Sfm

private val Term.key get() = "${type}:${id}"

@Composable
fun SubsScreen() {
    val app = LocalApp.current
    val lib by app.library.data.collectAsState()
    val subs = lib.subs
    var manage by rememberSaveable { mutableStateOf(false) }
    val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pager = rememberPagerState { subs.size + 1 }
    OnTabReselect { behavior.expand() }

    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
        Spacer(Modifier.statusBarsPadding())
        CollapsingHeader(behavior) {
            TopTitle("Подписки", subtitle = if (subs.isEmpty()) null else "${subs.size} ${pluralRu(subs.size, "подписка", "подписки", "подписок")}") {
                if (subs.isNotEmpty()) IconButton(onClick = { manage = true }) { Icon(Icons.Outlined.EditNotifications, "Управление подписками", tint = Sfm.Text) }
            }
        }
        if (subs.isEmpty()) {
            SubsEmpty(onOpenCatalog = { app.openTab(Tab.CATALOG) })
            return@Column
        }
        TabsPager(listOf("Все") + subs.map { it.name }, pager, keys = listOf<Any>("all") + subs.map { it.key }) { i ->
            if (i == 0) AllSubsPage(subs)
            else {
                val src = FeedSource(FeedKind.TERM_NEW, term = subs[i - 1], duration = lib.settings.duration)
                key(src.key) { FeedGrid(app.graph.feeds.get(src)) }
            }
        }
    }
    if (manage && subs.isNotEmpty()) ManageSubsSheet(subs, onDismiss = { manage = false })
}

@Composable
fun SubsEmpty(onOpenCatalog: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            Icons.Outlined.NotificationsNone,
            "Подписок пока нет",
            "Подпишитесь на игры, персонажей, авторов или теги — их новые видео соберутся здесь, а о свежих мы напомним уведомлением",
            action = "Открыть каталог",
            onAction = onOpenCatalog,
        )
    }
}

/** Combined feed of all subscriptions; showing it marks everything as seen (clears the badge). */
@Composable
private fun AllSubsPage(subs: List<Term>) {
    val app = LocalApp.current
    val lib by app.library.data.collectAsState()
    val feed = app.graph.feeds.get(FeedSource(FeedKind.SUBS, terms = subs, duration = lib.settings.duration))
    val items by feed.items.collectAsState()
    val seenAtEntry = remember(feed) { lib.subsSeenMaxId }
    val fresh = if (seenAtEntry > 0) items.count { it.id > seenAtEntry } else 0
    LaunchedEffect(items) {
        val max = items.maxOfOrNull { it.id } ?: return@LaunchedEffect
        if (max > app.library.value.subsSeenMaxId) app.library.update { it.copy(subsSeenMaxId = max) }
        app.subsBadge = 0
    }
    key(feed.source.key) {
        FeedGrid(
            feed,
            emptyTitle = "Пока нет видео",
            emptyText = "Новые видео по вашим подпискам появятся здесь",
            header = if (fresh == 0) null else {
                {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "fresh", contentType = "fresh") {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sfm.Accent.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp), tint = Sfm.Accent)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "$fresh ${pluralRu(fresh, "новое видео", "новых видео", "новых видео")} с прошлого визита",
                                style = MaterialTheme.typography.bodyMedium, color = Sfm.Text,
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ManageSubsSheet(subs: List<Term>, onDismiss: () -> Unit) {
    val app = LocalApp.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Sfm.Surface) {
        Text(
            "Мои подписки · ${subs.size}",
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleLarge, color = Sfm.Text,
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding()) {
            items(subs, key = { it.key }) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { onDismiss(); app.openTerm(t) }.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).animateItem(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TermAvatar(t)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(t.type.title, t.count.takeIf { it > 0 }?.let(::videosLabel)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, maxLines = 1,
                        )
                    }
                    TextButton(onClick = {
                        app.library.toggleSub(t)
                        app.toast("Подписка на «${t.name}» отменена", "Вернуть") { if (!app.library.value.isSubscribed(t)) app.library.toggleSub(t) }
                    }) { Text("Отписаться", color = Sfm.Hot) }
                }
            }
        }
    }
}
