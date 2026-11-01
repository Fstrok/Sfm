package com.sfm.app.player

import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.sfm.app.data.Video
import com.sfm.app.graph
import com.sfm.app.ui.AppState
import com.sfm.app.ui.PlayerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The single ExoPlayer of the app and everything around it: disk cache, next-item prefetch,
 * url resolving, resume, history, settings. Follows [com.sfm.app.ui.PlayerUi] (queue/index/mode)
 * on its own, so notification and PiP buttons work while no UI is drawn.
 */
class PlayerController private constructor(private val ctx: Context) {
    private val graph = ctx.graph
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val cache by lazy {
        SimpleCache(File(ctx.cacheDir, "video"), LeastRecentlyUsedCacheEvictor(CACHE_BYTES), StandaloneDatabaseProvider(ctx))
    }
    private val cacheFactory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(graph.net.client))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
    private val localFactory by lazy { DefaultDataSource.Factory(ctx) }

    private var _player: ExoPlayer? = null
    val player: ExoPlayer get() = _player ?: build().also { _player = it }
    /** Player exposed to the media session: adds next/previous that walk the queue. */
    val sessionPlayer by lazy { QueuePlayer(player, this) }

    // ---- observable state (Compose) ----
    var isPlaying by mutableStateOf(false); private set
    /** User intent (play/pause icon): true while buffering towards playback too. */
    var playWhenReady by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set
    /** Video whose media is set on the player. */
    var loadedId by mutableStateOf<Long?>(null); private set
    /** Video whose first frame is on the surface (thumbnail overlay hides then). */
    var firstFrameId by mutableStateOf<Long?>(null); private set
    var resolving by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var aspect by mutableFloatStateOf(16f / 9f); private set
    var durationMs by mutableLongStateOf(0L); private set
    var boosted by mutableStateOf(false); private set
    /** Window bounds of the expanded video (PiP enter animation). */
    var surfaceBounds: Rect? = null

    private var app: AppState? = null
    private var loaded: Video? = null
    private var readyOnce = false
    private var loadJob: Job? = null
    private var service: ListenableFuture<MediaController>? = null

    val hasMedia get() = _player?.currentMediaItem != null

    private fun build(): ExoPlayer = ExoPlayer.Builder(ctx)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setSeekBackIncrementMs(SEEK_MS)
        .setSeekForwardIncrementMs(SEEK_MS)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_ONE
            val s = graph.library.settings
            setPlaybackSpeed(s.speed)
            volume = if (s.muted) 0f else 1f
            addListener(listener)
        }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        override fun onPlayWhenReadyChanged(play: Boolean, reason: Int) { playWhenReady = play }
        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                readyOnce = true
                error = null
                durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            }
        }
        override fun onVideoSizeChanged(size: VideoSize) {
            if (size.width > 0 && size.height > 0) aspect = size.width * size.pixelWidthHeightRatio / size.height
        }
        override fun onRenderedFirstFrame() { firstFrameId = loadedId }
        override fun onPlayerError(e: PlaybackException) {
            error = when (e.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Нет сети или сайт не отвечает"
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Сайт не отдал файл"
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Файл не найден"
                in 3000..3999 -> "Файл повреждён или не видео"
                in 4000..4999 -> "Формат не поддерживается телефоном"
                else -> "Ошибка воспроизведения (${e.errorCode})"
            }
        }
    }

    /** Starts following the app's player state. Idempotent. */
    fun attach(app: AppState) {
        if (this.app != null) return
        this.app = app
        val ui = app.player
        scope.launch {
            snapshotFlow { ui.mode to ui.current }.collect { (mode, v) ->
                if (mode == PlayerMode.CLOSED || v == null) stop() else load(v)
                if (mode == PlayerMode.MINI) saveHistory()
                syncService()
            }
        }
        // endless queue: ask the source feed for more near the end, append what is new
        scope.launch {
            snapshotFlow { ui.feed }.collectLatest { feed ->
                feed?.items?.collect { items -> ui.append(graph.library.value.visible(items.filter { n -> ui.queue.none { it.id == n.id } })) }
            }
        }
        scope.launch {
            snapshotFlow { Triple(ui.feed, ui.queue.size - 1 - ui.index, ui.mode) }.collect { (feed, left, mode) ->
                if (feed != null && mode != PlayerMode.CLOSED && left <= 3) feed.loadMore()
            }
        }
        // warm the cache with the start of the next video while the current one plays
        scope.launch {
            snapshotFlow { if (ui.mode == PlayerMode.CLOSED) null else ui.queue.getOrNull(ui.index + 1)?.id }.collectLatest { id ->
                id ?: return@collectLatest
                delay(1200)
                prefetch(ui.queue.firstOrNull { it.id == id } ?: return@collectLatest)
            }
        }
        scope.launch {
            graph.library.data.map { it.settings }.distinctUntilChanged().collect { s ->
                _player?.let { p ->
                    if (!boosted) p.setPlaybackSpeed(s.speed)
                    p.volume = if (s.muted) 0f else 1f
                }
                syncService()
            }
        }
        scope.launch {
            while (isActive) {
                delay(5000)
                if (isPlaying) saveHistory()
            }
        }
    }

    private fun load(v: Video) {
        if (loadedId == v.id) {
            loaded = loaded?.merge(v) ?: v
            return
        }
        saveHistory()
        loadJob?.cancel()
        loaded = v
        loadedId = v.id
        firstFrameId = null
        error = null
        resolving = false
        readyOnce = false
        durationMs = v.durationMs ?: 0L
        aspect = 16f / 9f
        val p = player
        p.stop()
        p.clearMediaItems()
        loadJob = scope.launch {
            val local = withContext(Dispatchers.IO) { runCatching { graph.downloads.localUri(v.id) }.getOrNull() }
            var video = v
            if (local == null && v.videoUrl == null) {
                resolving = true
                video = runCatching { graph.repo.post(v.id) }.getOrNull() ?: v
                resolving = false
                if (video !== v) { loaded = video; app?.player?.update(video) }
            }
            val uri = local ?: video.videoUrl?.let(Uri::parse)
            if (uri == null) { error = "Не удалось получить ссылку на видео"; return@launch }
            val item = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(video.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(video.title)
                        .setArtist(video.cats.lastOrNull()?.name ?: video.artist)
                        .setArtworkUri(video.thumb?.let(Uri::parse))
                        .build(),
                )
                .build()
            val factory = if (local != null) localFactory else cacheFactory
            p.setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(item))
            val lib = graph.library.value
            val h = lib.historyById[video.id]
            if (lib.settings.resume && h != null && h.positionMs > 1500 && h.progress < 0.95f) p.seekTo(h.positionMs)
            p.prepare()
            p.playWhenReady = true
        }
    }

    private fun stop() {
        if (loadedId == null) return
        saveHistory()
        loadJob?.cancel()
        loaded = null
        loadedId = null
        firstFrameId = null
        error = null
        resolving = false
        boosted = false
        _player?.run { stop(); clearMediaItems() }
    }

    /** Records position + verified duration of the loaded video. */
    fun saveHistory() {
        val v = loaded ?: return
        val p = _player ?: return
        if (!readyOnce || p.currentMediaItem == null) return
        val dur = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        graph.library.recordHistory(v, p.currentPosition.coerceIn(0, dur), dur)
    }

    private suspend fun prefetch(next: Video) {
        if (graph.library.value.downloadsById[next.id] != null) return
        val v = if (next.videoUrl == null) runCatching { graph.repo.post(next.id) }.getOrNull()?.also { app?.player?.update(it) } ?: return else next
        val url = v.videoUrl ?: return
        val writer = CacheWriter(cacheFactory.createDataSourceForDownloading(), DataSpec.Builder().setUri(url).setLength(PREFETCH_BYTES).build(), null, null)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { writer.cancel() }
        try {
            runInterruptible(Dispatchers.IO) { writer.cache() }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        } finally {
            handle?.dispose()
        }
    }

    /** A new surface took the video: its first frame is still to come (paused video renders nothing until played). */
    fun surfaceChanged() { firstFrameId = null }

    // ---- commands ----
    fun play() { _player?.let { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } }
    fun pause() { _player?.pause(); saveHistory() }
    fun togglePlay() { if (_player?.playWhenReady == true) pause() else play() }
    fun next() { app?.player?.next() }
    fun prev() { app?.player?.prev() }
    fun hasNext() = app?.player?.hasNext == true
    fun hasPrev() = app?.player?.hasPrev == true

    fun seekTo(ms: Long, exact: Boolean = true) {
        val p = _player ?: return
        p.setSeekParameters(if (exact) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
        p.seekTo(ms.coerceIn(0, (p.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE)))
    }
    fun seekBy(ms: Long) = seekTo((_player?.currentPosition ?: 0) + ms)

    /** Long-press 2× speed while held. */
    fun boost(on: Boolean) {
        boosted = on
        _player?.setPlaybackSpeed(if (on) 2f else graph.library.settings.speed)
    }

    fun retry() {
        val v = loaded ?: return
        loadedId = null
        load(v)
    }

    val position get() = _player?.currentPosition ?: 0L
    val buffered get() = _player?.bufferedPosition ?: 0L

    /** Keeps a MediaController bound to [PlaybackService] while background playback is wanted (notification + session). */
    private fun syncService() {
        val want = graph.library.settings.background && app?.player?.mode != PlayerMode.CLOSED && loadedId != null
        if (want && service == null) {
            service = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
        } else if (!want && service != null) {
            service?.let { MediaController.releaseFuture(it) }
            service = null
        }
    }

    companion object {
        const val SEEK_MS = 10_000L
        private const val CACHE_BYTES = 1L shl 30
        private const val PREFETCH_BYTES = 3L shl 20

        @Volatile private var instance: PlayerController? = null
        fun get(ctx: Context): PlayerController =
            instance ?: synchronized(this) { instance ?: PlayerController(ctx.applicationContext).also { instance = it } }
        /** Instance if it was ever created (lifecycle hooks must not build the player just to pause it). */
        fun peek(): PlayerController? = instance
    }
}
