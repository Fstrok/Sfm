package com.sfm.app.ui

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sfm.app.CrashLog
import com.sfm.app.ui.catalog.CatalogScreen
import com.sfm.app.ui.home.HomeScreen
import com.sfm.app.ui.library.LibraryScreen
import com.sfm.app.ui.lock.LockScreen
import com.sfm.app.ui.player.PipPlayer
import com.sfm.app.ui.player.PlayerOverlay
import com.sfm.app.ui.search.SearchScreen
import com.sfm.app.ui.settings.BlacklistScreen
import com.sfm.app.ui.settings.SettingsScreen
import com.sfm.app.ui.subs.SubsScreen
import com.sfm.app.ui.term.TermScreen
import com.sfm.app.ui.theme.Sfm
import kotlinx.coroutines.launch

val BottomBarHeight = 72.dp
val MiniPlayerHeight = 64.dp

@Composable
fun AppRoot(app: AppState) {
    val loaded by app.library.loaded.collectAsState()
    CompositionLocalProvider(LocalApp provides app) {
        Box(Modifier.fillMaxSize().background(Sfm.Bg)) {
            when {
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                app.inPip -> PipPlayer()
                else -> {
                    BackHandler(enabled = !app.locked && (app.player.mode == PlayerMode.EXPANDED || app.stack.size > 1 || app.tab != Tab.HOME)) { app.back() }
                    val showBar = app.top !is Screen.Settings && app.top !is Screen.Blacklist
                    val miniPad = if (app.player.mode == PlayerMode.MINI) MiniPlayerHeight + 8.dp else 0.dp
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).padding(bottom = miniPad)) { Screens(app) }
                        if (showBar) BottomBar(app)
                    }
                    PlayerOverlay(bottomInset = if (showBar) BottomBarHeight else 0.dp)
                    SnackbarHost(
                        app.snackbar,
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                            .padding(bottom = (if (showBar) BottomBarHeight else 0.dp) + miniPad + 8.dp),
                    ) { Snackbar(it, containerColor = Sfm.Surface3, contentColor = Sfm.Text, actionColor = Sfm.Accent, shape = MaterialTheme.shapes.medium) }
                    if (app.locked) LockScreen(onUnlocked = { app.locked = false }) else CrashDialog()
                }
            }
        }
    }
}

@Composable
private fun Screens(app: AppState) {
    val holder = rememberSaveableStateHolder()
    AnimatedContent(app.top, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screens") { s ->
        when (s) {
            Screen.Main -> AnimatedContent(app.tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tabs") { t ->
                holder.SaveableStateProvider(t.name) {
                    when (t) {
                        Tab.HOME -> HomeScreen()
                        Tab.CATALOG -> CatalogScreen()
                        Tab.SUBS -> SubsScreen()
                        Tab.LIBRARY -> LibraryScreen()
                    }
                }
            }
            is Screen.TermFeed -> holder.SaveableStateProvider("term:${s.term.type}:${s.term.id}") { TermScreen(s.term) }
            is Screen.Search -> holder.SaveableStateProvider("search") { SearchScreen(s.query) }
            Screen.Settings -> SettingsScreen()
            Screen.Blacklist -> BlacklistScreen()
        }
    }
}

@Composable
private fun BottomBar(app: AppState) {
    NavigationBar(containerColor = Sfm.Surface, tonalElevation = 0.dp, modifier = Modifier) {
        Tab.entries.forEach { t ->
            val sel = app.top == Screen.Main && app.tab == t
            NavigationBarItem(
                selected = sel,
                onClick = { if (sel) app.tabReselect = t.ordinal to (app.tabReselect.second + 1) else app.openTab(t) },
                icon = {
                    BadgedBox(badge = { if (t == Tab.SUBS && app.subsBadge > 0) Badge { Text(if (app.subsBadge > 99) "99+" else "${app.subsBadge}") } }) {
                        Icon(if (sel) t.iconSelected else t.icon, t.title)
                    }
                },
                label = { Text(t.title, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Sfm.Accent, selectedTextColor = Sfm.Text, indicatorColor = Sfm.Accent.copy(alpha = 0.14f),
                    unselectedIconColor = Sfm.TextDim, unselectedTextColor = Sfm.TextDim,
                ),
            )
        }
    }
}

@Composable
private fun CrashDialog() {
    val ctx = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(CrashLog.take(ctx)) }
    val t = text ?: return
    AlertDialog(
        onDismissRequest = { text = null },
        title = { Text("Приложение завершилось с ошибкой") },
        text = {
            Column {
                Text("Нажмите «Скопировать» и пришлите текст разработчику", color = Sfm.TextDim)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(width = 400.dp, height = 220.dp).background(Sfm.Surface2, MaterialTheme.shapes.small).padding(8.dp)) {
                    Text(t, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        },
        confirmButton = { TextButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("crash", t))) }; text = null }) { Text("Скопировать") } },
        dismissButton = { TextButton(onClick = { text = null }) { Text("Закрыть") } },
    )
}
