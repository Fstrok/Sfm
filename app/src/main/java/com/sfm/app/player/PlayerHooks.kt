package com.sfm.app.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.sfm.app.graph
import com.sfm.app.ui.AppState
import com.sfm.app.ui.PlayerMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Activity lifecycle hooks for the player: picture-in-picture, pause in background, background session. */
object PlayerHooks {
    private const val ACTION_PREV = "com.sfm.app.pip.PREV"
    private const val ACTION_TOGGLE = "com.sfm.app.pip.TOGGLE"
    private const val ACTION_NEXT = "com.sfm.app.pip.NEXT"
    private var receiverRegistered = false
    private var paramsJob: Job? = null

    fun supportsPip(ctx: Context) = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun autoPipWanted(app: AppState, ctrl: PlayerController) =
        app.graph.library.settings.autoPip && ctrl.isPlaying && app.player.mode == PlayerMode.EXPANDED && !app.locked

    fun onUserLeaveHint(activity: ComponentActivity, app: AppState) {
        val ctrl = PlayerController.peek() ?: return
        // API 31+ enters by itself via setAutoEnterEnabled (kept up to date in onStart)
        if (Build.VERSION.SDK_INT < 31 && autoPipWanted(app, ctrl)) enterPip(activity, app)
    }

    /** Enters PiP from the player button; false when the phone can't. */
    fun enterPip(activity: Activity, app: AppState): Boolean {
        if (!supportsPip(activity)) return false
        val ctrl = PlayerController.get(activity)
        return runCatching { activity.enterPictureInPictureMode(params(activity, app, ctrl)) }.getOrDefault(false)
    }

    fun onPipChanged(activity: ComponentActivity, app: AppState, inPip: Boolean) {
        app.inPip = inPip
        // PiP window closed with ✕: the activity is stopped right after leaving PiP
        if (!inPip && activity.lifecycle.currentState == Lifecycle.State.CREATED) {
            PlayerController.peek()?.pause()
            app.player.minimize()
        }
    }

    fun onStart(activity: ComponentActivity, app: AppState) {
        val ctrl = PlayerController.get(activity)
        ctrl.attach(app)
        registerReceiver(activity.applicationContext)
        if (!supportsPip(activity)) return
        paramsJob?.cancel()
        paramsJob = activity.lifecycleScope.launch {
            combine(
                snapshotFlow { listOf(app.player.mode, ctrl.isPlaying, ctrl.aspect, app.player.hasNext, app.player.hasPrev, app.locked, ctrl.loadedId) },
                activity.graph.library.data.map { it.settings.autoPip }.distinctUntilChanged(),
            ) { a, b -> a to b }.collect {
                runCatching { activity.setPictureInPictureParams(params(activity, app, ctrl)) }
            }
        }
    }

    fun onStop(activity: ComponentActivity, app: AppState) {
        paramsJob?.cancel()
        val ctrl = PlayerController.peek() ?: return
        if (activity.isChangingConfigurations) return
        ctrl.saveHistory()
        if (!activity.graph.library.settings.background && !activity.isInPictureInPictureMode) ctrl.pause()
    }

    fun onDestroy(activity: ComponentActivity, app: AppState) {
        if (activity.isFinishing && !activity.graph.library.settings.background) PlayerController.peek()?.pause()
    }

    private fun params(ctx: Context, app: AppState, ctrl: PlayerController): PictureInPictureParams {
        val a = ctrl.aspect.coerceIn(1f / 2.39f, 2.39f)
        val b = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((a * 1000).toInt(), 1000))
            .setActions(
                listOf(
                    action(ctx, ACTION_PREV, android.R.drawable.ic_media_previous, "Предыдущее", app.player.hasPrev),
                    if (ctrl.isPlaying) action(ctx, ACTION_TOGGLE, android.R.drawable.ic_media_pause, "Пауза", true)
                    else action(ctx, ACTION_TOGGLE, android.R.drawable.ic_media_play, "Играть", true),
                    action(ctx, ACTION_NEXT, android.R.drawable.ic_media_next, "Следующее", app.player.hasNext),
                ),
            )
        ctrl.surfaceBounds?.let { b.setSourceRectHint(it) }
        if (Build.VERSION.SDK_INT >= 31) {
            b.setAutoEnterEnabled(autoPipWanted(app, ctrl))
            b.setSeamlessResizeEnabled(false)
        }
        return b.build()
    }

    private fun action(ctx: Context, act: String, icon: Int, title: String, enabled: Boolean): RemoteAction {
        val pi = PendingIntent.getBroadcast(
            ctx, act.hashCode(), Intent(act).setPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(ctx, icon), title, title, pi).apply { isEnabled = enabled }
    }

    private fun registerReceiver(ctx: Context) {
        if (receiverRegistered) return
        receiverRegistered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val ctrl = PlayerController.peek() ?: return
                when (i.action) {
                    ACTION_PREV -> ctrl.prev()
                    ACTION_TOGGLE -> ctrl.togglePlay()
                    ACTION_NEXT -> ctrl.next()
                }
            }
        }
        val filter = IntentFilter().apply { addAction(ACTION_PREV); addAction(ACTION_TOGGLE); addAction(ACTION_NEXT) }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
