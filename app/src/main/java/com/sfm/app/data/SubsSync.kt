package com.sfm.app.data

import android.content.Context
import com.sfm.app.ui.AppState

/** Subscriptions sync. STUB: implemented by the browse agent (WorkManager check + notification + badge). */
object SubsSync {
    const val EXTRA_TAB = "open_tab"

    /** (Re)schedules the periodic check; hours = 0 cancels it. */
    fun schedule(ctx: Context, hours: Int) {}

    /** Updates app.subsBadge with the number of new videos in subscriptions. */
    suspend fun refreshBadge(app: AppState) {}
}
