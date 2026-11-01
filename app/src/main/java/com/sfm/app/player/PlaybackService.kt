package com.sfm.app.player

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** STUB: implemented by the player agent (media session for background playback). */
class PlaybackService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = null
}
