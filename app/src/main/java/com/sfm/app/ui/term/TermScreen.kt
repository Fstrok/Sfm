package com.sfm.app.ui.term

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sfm.app.data.FeedKind
import com.sfm.app.data.FeedSource
import com.sfm.app.data.SITE
import com.sfm.app.data.Term
import com.sfm.app.data.TermType
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.common.CollapsingHeader
import com.sfm.app.ui.common.FeedGrid
import com.sfm.app.ui.common.TabsPager
import com.sfm.app.ui.common.TermAvatar
import com.sfm.app.ui.common.TermChip
import com.sfm.app.ui.common.icon
import com.sfm.app.ui.common.termColor
import com.sfm.app.ui.common.videosLabel
import com.sfm.app.ui.theme.Sfm

private fun Term.siteLink(): String = link.ifBlank { SITE + (if (isCategory) "category/" else "tag/") + slug + "/" }

@Composable
fun TermScreen(term: Term) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val lib by app.library.data.collectAsState()
    val cat by app.graph.catalog.data.collectAsState()
    val t = (if (term.isCategory) cat.byCat[term.id] else cat.byTag[term.id]) ?: term
    val related = remember(cat, t.id) {
        when (t.type) {
            TermType.GAME -> cat.charactersOf(t.id).sortedByDescending { it.count }
            TermType.CHARACTER -> listOfNotNull(cat.byCat[t.parent])
            else -> emptyList()
        }
    }
    val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pager = rememberPagerState { 2 }
    LaunchedEffect(t.id) { if (t.isCategory) runCatching { app.graph.catalog.ensureCategories() } }

    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }

    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
        Column(Modifier.background(Brush.verticalGradient(listOf(termColor(t).copy(alpha = 0.22f), Color.Transparent)))) {
            Spacer(Modifier.statusBarsPadding())
            TermTopBar(
                t,
                blacklisted = lib.isBlacklisted(t),
                onBack = { app.back() },
                onBlacklist = {
                    val added = app.library.toggleBlacklist(t)
                    app.toast(if (added) "«${t.name}» больше не показывается в лентах" else "«${t.name}» снова показывается", "Отменить") { app.library.toggleBlacklist(t) }
                },
                onShare = { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t.siteLink()), null)) },
                onOpenSite = { open(t.siteLink()) },
            )
            CollapsingHeader(behavior) {
                TermHeader(
                    t, related,
                    subscribed = lib.isSubscribed(t),
                    blacklisted = lib.isBlacklisted(t),
                    onSubscribe = { app.toggleSub(t) },
                    onUnblock = { app.library.toggleBlacklist(t) },
                    onOpen = app::openTerm,
                )
            }
        }
        TabsPager(listOf("Новые", "Популярные"), pager) { i ->
            val src = FeedSource(if (i == 0) FeedKind.TERM_NEW else FeedKind.TERM_TOP, term = t, duration = lib.settings.duration)
            key(src.key) { FeedGrid(app.graph.feeds.get(src), emptyText = "В этом разделе пока нет видео под текущий фильтр") }
        }
    }
}

@Composable
fun TermTopBar(t: Term, blacklisted: Boolean, onBack: () -> Unit, onBlacklist: () -> Unit, onShare: () -> Unit, onOpenSite: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(60.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад", tint = Sfm.Text) }
        TermAvatar(t, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.titleMedium, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(t.type.title, t.count.takeIf { it > 0 }?.let(::videosLabel)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Ещё", tint = Sfm.Text) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Sfm.Surface2) {
                DropdownMenuItem(
                    text = { Text(if (blacklisted) "Снова показывать" else "Не показывать") },
                    leadingIcon = { Icon(if (blacklisted) Icons.Outlined.Visibility else Icons.Outlined.Block, null) },
                    onClick = { menu = false; onBlacklist() },
                )
                DropdownMenuItem(text = { Text("Поделиться") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text("Открыть на сайте") }, leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, null) }, onClick = { menu = false; onOpenSite() })
            }
        }
    }
}

/** Subscribe button, "hidden" notice and related terms (characters of a game / game of a character). */
@Composable
fun TermHeader(
    t: Term,
    related: List<Term>,
    subscribed: Boolean,
    blacklisted: Boolean,
    onSubscribe: () -> Unit,
    onUnblock: () -> Unit,
    onOpen: (Term) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Button(
            onClick = onSubscribe,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(22.dp),
            colors = if (subscribed) ButtonDefaults.buttonColors(containerColor = Sfm.Surface3, contentColor = Sfm.Text)
            else ButtonDefaults.buttonColors(containerColor = Sfm.Accent, contentColor = Color(0xFF00241A)),
        ) {
            Icon(if (subscribed) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationAdd, null, Modifier.size(18.dp), tint = if (subscribed) Sfm.Accent else Color(0xFF00241A))
            Spacer(Modifier.width(8.dp))
            Text(if (subscribed) "Вы подписаны" else "Подписаться")
        }
        if (blacklisted) {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sfm.Hot.copy(alpha = 0.12f))
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Block, null, Modifier.size(18.dp), tint = Sfm.Hot)
                Spacer(Modifier.width(10.dp))
                Text("Раздел скрыт из лент", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Sfm.Text)
                TextButton(onClick = onUnblock) { Text("Показывать", color = Sfm.Accent) }
            }
        }
        if (related.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(related, key = { it.id }) { r -> TermChip(r.name, { onOpen(r) }, icon = r.type.icon, count = r.count, tint = termColor(r)) }
            }
        }
    }
}
