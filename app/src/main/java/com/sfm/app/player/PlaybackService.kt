package com.sfm.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sfm.app.MainActivity

/** Background playback: a media session (notification, lock screen, headset buttons) over the app's single player. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, PlayerController.get(this).sessionPlayer).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}

/** Single-item player that still offers next/previous: they walk the app queue (videos always loop). */
class QueuePlayer(player: Player, private val ctrl: PlayerController) : ForwardingPlayer(player) {
    private val extra = intArrayOf(
        COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    )

    override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon().addAll(*extra).build()
    override fun isCommandAvailable(command: Int) = command in extra || super.isCommandAvailable(command)
    override fun hasNextMediaItem() = ctrl.hasNext()
    override fun hasPreviousMediaItem() = ctrl.hasPrev()
    override fun seekToNext() = ctrl.next()
    override fun seekToNextMediaItem() = ctrl.next()
    override fun seekToPrevious() = ctrl.prev()
    override fun seekToPreviousMediaItem() = ctrl.prev()
}
