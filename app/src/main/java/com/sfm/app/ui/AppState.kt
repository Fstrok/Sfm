package com.sfm.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.sfm.app.Graph
import com.sfm.app.data.DlProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.sfm.app.data.Term
import com.sfm.app.data.Video
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Main : Screen
    data class TermFeed(val term: Term) : Screen
    data class Search(val query: String = "") : Screen
    data object Settings : Screen
    data object Blacklist : Screen
}

enum class Tab(val title: String, val icon: ImageVector, val iconSelected: ImageVector) {
    HOME("Главная", Icons.Outlined.Home, Icons.Filled.Home),
    CATALOG("Каталог", Icons.Outlined.Explore, Icons.Filled.Explore),
    SUBS("Подписки", Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions),
    LIBRARY("Библиотека", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
}

/** App-wide UI state: navigation stack, lock, player, messages. */
class AppState(val graph: Graph) {
    val stack = mutableStateListOf<Screen>(Screen.Main)
    var tab by mutableStateOf(Tab.HOME)
    /** Locked until the password is entered (also set again after background timeout). */
    var locked by mutableStateOf(true)
    var inPip by mutableStateOf(false)
    /** New videos in subscriptions (badge on the bottom bar). */
    var subsBadge by mutableStateOf(0)
    /** (tab ordinal, counter): bumped when the selected bottom tab is tapped again — screens scroll to top. */
    var tabReselect by mutableStateOf(-1 to 0)
    val player = PlayerUi(this)
    val snackbar = SnackbarHostState()
    /** Download progress, polled only while some screen collects it. */
    val dlProgress: StateFlow<Map<Long, DlProgress>> =
        graph.downloads.progress().stateIn(graph.scope, SharingStarted.WhileSubscribed(3000), emptyMap())

    val top: Screen get() = stack.last()
    val library get() = graph.library
    val repo get() = graph.repo

    fun push(s: Screen) { if (stack.last() != s) stack.add(s) }

    /** Returns false when there is nowhere to go back (activity should finish). */
    fun back(): Boolean {
        if (player.mode == PlayerMode.EXPANDED) { player.minimize(); return true }
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        if (tab != Tab.HOME) { tab = Tab.HOME; return true }
        return false
    }

    fun openTab(t: Tab) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        tab = t
    }

    fun openTerm(t: Term) { if (player.mode == PlayerMode.EXPANDED) player.minimize(); push(Screen.TermFeed(t)) }
    fun openSearch(q: String = "") = push(Screen.Search(q))

    fun toast(msg: String, action: String? = null, onAction: (() -> Unit)? = null) {
        graph.scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(msg, action, withDismissAction = false, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    fun playRandom() {
        graph.scope.launch {
            toast("Ищу случайное видео…")
            val v = runCatching { repo.random() }.getOrNull()
            if (v == null) toast("Сайт не ответил") else player.openOne(v)
        }
    }

    fun download(v: Video) {
        graph.scope.launch {
            val full = if (v.videoUrl == null) runCatching { repo.post(v.id) }.getOrNull() ?: v else v
            val err = graph.downloads.start(full)
            toast(err ?: "Скачивание: ${full.title}")
        }
    }

    fun toggleFavorite(v: Video) {
        val added = library.toggleFavorite(v)
        toast(if (added) "Добавлено в избранное" else "Убрано из избранного")
    }

    fun hide(v: Video) {
        library.hide(v)
        toast("Видео скрыто", "Вернуть") { library.unhide(setOf(v.id)) }
    }

    fun toggleSub(t: Term) {
        val added = library.toggleSub(t)
        toast(if (added) "Подписка на «${t.name}» оформлена" else "Подписка отменена")
    }
}

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState not provided") }
