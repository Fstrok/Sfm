package com.sfm.app.player

import androidx.activity.ComponentActivity
import com.sfm.app.ui.AppState

/** Activity lifecycle hooks for the player (PiP, background playback). STUB: implemented by the player agent. */
object PlayerHooks {
    fun onUserLeaveHint(activity: ComponentActivity, app: AppState) {}
    fun onPipChanged(activity: ComponentActivity, app: AppState, inPip: Boolean) { app.inPip = inPip }
    fun onStart(activity: ComponentActivity, app: AppState) {}
    fun onStop(activity: ComponentActivity, app: AppState) {}
    fun onDestroy(activity: ComponentActivity, app: AppState) {}
}
