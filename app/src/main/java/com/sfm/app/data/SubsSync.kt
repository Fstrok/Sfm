package com.sfm.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sfm.app.MainActivity
import com.sfm.app.R
import com.sfm.app.graph
import com.sfm.app.ui.AppState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Subscriptions sync: periodic background check with a notification, and the bottom-bar badge. */
object SubsSync {
    /** Intent extra with a [com.sfm.app.ui.Tab] name to open; the notification sends "SUBS". */
    const val EXTRA_TAB = "open_tab"
    internal const val WORK = "subs-check"
    private const val CHANNEL = "subs"
    private const val NOTIFICATION_ID = 4201

    /** (Re)schedules the periodic check; hours = 0 cancels it. */
    fun schedule(ctx: Context, hours: Int) {
        val wm = WorkManager.getInstance(ctx)
        if (hours <= 0) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<SubsWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** Updates app.subsBadge with the number of new videos in subscriptions. Never throws. */
    suspend fun refreshBadge(app: AppState) {
        try {
            val lib = app.library
            lib.loaded.first { it }
            val subs = lib.value.subs
            if (subs.isEmpty()) { app.subsBadge = 0; return }
            val ids = app.repo.feed(FeedSource(FeedKind.SUBS, terms = subs), 1).items.map { it.id }
            app.subsBadge = ids.count { it > lib.value.subsSeenMaxId }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
    }

    /** Page 1 of the subscriptions feed → notification about posts newer than the last notified/seen one. */
    internal suspend fun check(ctx: Context): Boolean {
        val g = ctx.applicationContext.graph
        g.library.loaded.first { it }
        val d = g.library.value
        if (d.subs.isEmpty()) return true
        if (g.catalog.data.value.categories.isEmpty()) g.catalog.loadCache()
        val items = g.repo.feed(FeedSource(FeedKind.SUBS, terms = d.subs), 1).items
        val max = items.maxOfOrNull { it.id } ?: return true
        val base = maxOf(d.subsNotifiedMaxId, d.subsSeenMaxId)
        val fresh = items.filter { it.id > base }
        // First check after subscribing only sets the baseline: no notification about old posts.
        if (base > 0 && fresh.isNotEmpty()) notify(ctx, fresh)
        if (max > d.subsNotifiedMaxId) {
            g.library.update { it.copy(subsNotifiedMaxId = maxOf(it.subsNotifiedMaxId, max)) }
            g.library.saveNow()
        }
        return true
    }

    internal fun notify(ctx: Context, fresh: List<Video>) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Подписки", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Новые видео по вашим подпискам" })
        }
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_TAB, "SUBS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val titles = fresh.map { it.title }.filter { it.isNotBlank() }
        val n = fresh.size
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sfm)
            .setColor(0xFF00DBA3.toInt())
            .setContentTitle("Новые видео в подписках: ${if (n >= 30) "30+" else n}")
            .setContentText(titles.take(3).joinToString(" · "))
            .setStyle(NotificationCompat.InboxStyle().also { s -> titles.take(5).forEach(s::addLine) })
            .setNumber(n)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }
}

/** Periodic WorkManager job behind [SubsSync.schedule]. */
class SubsWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        SubsSync.check(applicationContext)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        if (runAttemptCount < 2) Result.retry() else Result.success()
    }
}
