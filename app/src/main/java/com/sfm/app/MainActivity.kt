package com.sfm.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sfm.app.data.SITE
import com.sfm.app.data.Term
import com.sfm.app.data.TermType
import com.sfm.app.player.PlayerHooks
import com.sfm.app.ui.AppRoot
import com.sfm.app.ui.AppState
import com.sfm.app.ui.theme.SfmTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** App-scoped UI state: survives activity re-creation, PiP and orientation changes. */
object UiHolder {
    @Volatile private var state: AppState? = null
    fun get(graph: Graph): AppState = state ?: synchronized(this) { state ?: AppState(graph).also { state = it } }
}

class MainActivity : FragmentActivity() {
    private val app by lazy { UiHolder.get(graph) }
    private var backgroundAt = 0L

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { backgroundAt = System.currentTimeMillis() }
        override fun onStart(owner: LifecycleOwner) {
            val lib = graph.library
            if (backgroundAt > 0 && lib.hasPassword && !app.inPip &&
                System.currentTimeMillis() - backgroundAt >= lib.settings.lockDelaySec * 1000L
            ) app.locked = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        setContent { SfmTheme { AppRoot(app) } }
        lifecycleScope.launch {
            graph.library.data.map { it.settings.hideInRecents }.distinctUntilChanged().collect { hide ->
                if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!hide)
                else if (hide) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Site links (VIEW) and shared text (SEND): post → player, /category|/tag → feed. */
    private fun handleIntent(intent: Intent?) {
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Regex("""https?://(?:www\.)?sfmcompile\.club/\S*""").find(it)?.value }
            else -> null
        } ?: return
        lifecycleScope.launch {
            graph.library.loaded.first { it }
            val path = url.substringAfter("sfmcompile.club/").substringBefore('?').trim('/')
            val parts = path.split('/')
            when (parts.firstOrNull()) {
                "category", "tag" -> {
                    val slug = parts.last()
                    val cat = parts.first() == "category"
                    val d = graph.catalog.data.value
                    val t = (if (cat) d.categories else d.tags).firstOrNull { it.slug == slug }
                        ?: Term(0, slug.replace('-', ' ').replaceFirstChar { it.uppercase() }, slug, link = "$SITE$path/", type = if (cat) TermType.CHARACTER else TermType.TAG)
                    if (t.id != 0) app.openTerm(t) else app.toast("Раздел откроется после загрузки каталога")
                }
                "", null -> Unit
                else -> {
                    app.toast("Открываю видео…")
                    val v = runCatching { graph.repo.byUrl(url) }.getOrNull()
                    if (v != null) app.player.openOne(v) else app.toast("Не удалось открыть ссылку")
                }
            }
        }
        setIntent(Intent(this, MainActivity::class.java))
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PlayerHooks.onUserLeaveHint(this, app)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerHooks.onPipChanged(this, app, isInPictureInPictureMode)
    }

    override fun onStart() { super.onStart(); PlayerHooks.onStart(this, app) }
    override fun onStop() { super.onStop(); PlayerHooks.onStop(this, app) }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        PlayerHooks.onDestroy(this, app)
        super.onDestroy()
    }
}
