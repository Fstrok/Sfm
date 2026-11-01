package com.sfm.app.ui.player

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.sfm.app.data.FeedKind
import com.sfm.app.data.FeedSource
import com.sfm.app.data.LoadState
import com.sfm.app.data.Term
import com.sfm.app.data.TermType
import com.sfm.app.data.Video
import com.sfm.app.player.PlayerController
import com.sfm.app.player.PlayerHooks
import com.sfm.app.ui.AppState
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.PlayerMode
import com.sfm.app.ui.common.Thumb
import com.sfm.app.ui.theme.Sfm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Expanded player (full screen) and the mini-player card above [bottomInset]. */
@Composable
fun PlayerOverlay(bottomInset: Dp) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val ctrl = remember { PlayerController.get(ctx) }
    LaunchedEffect(Unit) { ctrl.attach(app) }
    val ui = app.player
    val activity = LocalActivity.current
    LaunchedEffect(ui.mode) {
        if (ui.mode != PlayerMode.EXPANDED) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val view = LocalView.current
    DisposableEffect(ctrl.isPlaying) {
        view.keepScreenOn = ctrl.isPlaying
        onDispose { view.keepScreenOn = false }
    }
    var lastVideo by remember { mutableStateOf<Video?>(null) }
    ui.current?.let { lastVideo = it }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            ui.mode == PlayerMode.EXPANDED,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 5 },
            exit = fadeOut(tween(180)),
        ) { ExpandedPlayer(app, ctrl) }
        AnimatedVisibility(
            ui.mode == PlayerMode.MINI,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = bottomInset + 8.dp),
            enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        ) { lastVideo?.let { MiniPlayerHost(app, ctrl, it) } }
    }
}

/** Content while the activity is in picture-in-picture: the video only. */
@Composable
fun PipPlayer() {
    val ctrl = PlayerController.get(LocalContext.current)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (ctrl.loadedId != null) VideoFrame(ctrl.aspect, 0) { VideoSurface(ctrl, Modifier.fillMaxSize()) }
    }
}

/** The single live video output (expanded page, mini card or PiP); the thumbnail covers it until a frame lands on it. */
@Composable
fun VideoSurface(ctrl: PlayerController, modifier: Modifier = Modifier) {
    DisposableEffect(Unit) { ctrl.surfaceChanged(); onDispose { } }
    PlayerSurface(player = ctrl.player, modifier = modifier, surfaceType = SURFACE_TYPE_TEXTURE_VIEW)
}

@Composable
private fun MiniPlayerHost(app: AppState, ctrl: PlayerController, v: Video) {
    val progress = remember { PlaybackProgress() }
    LaunchedEffect(Unit) {
        while (true) {
            progress.positionMs = ctrl.position; progress.durationMs = ctrl.durationMs
            delay(500)
        }
    }
    val ui = app.player
    MiniPlayerBar(
        v, ctrl.playWhenReady, ui.hasNext, progress,
        onExpand = ui::expand, onPlayPause = ctrl::togglePlay, onNext = ui::next, onClose = ui::close,
    ) {
        if (ctrl.loadedId == v.id && ui.mode == PlayerMode.MINI) VideoFrame(ctrl.aspect, 0) { VideoSurface(ctrl, Modifier.fillMaxSize()) }
        if (ctrl.firstFrameId != v.id) Thumb(v, Modifier.fillMaxSize())
    }
}

private enum class Sheet { INFO, MENU }

@Composable
private fun ExpandedPlayer(app: AppState, ctrl: PlayerController) {
    val ui = app.player
    val ctx = LocalContext.current
    val activity = LocalActivity.current
    val view = LocalView.current
    val lib by app.library.data.collectAsState()
    val dl by app.dlProgress.collectAsState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboard.current

    var controls by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var seekFb by remember { mutableStateOf<SeekFeedback?>(null) }
    var level by remember { mutableStateOf<Pair<LevelZone, Float>?>(null) }
    var levelTick by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf<String?>(null) }
    var stage by remember { mutableStateOf(IntSize.Zero) }
    val votes = remember { mutableStateMapOf<Long, Int>() }
    val progress = remember { PlaybackProgress() }
    val volume = remember { floatArrayOf(-1f) }

    LaunchedEffect(Unit) {
        while (true) {
            progress.positionMs = ctrl.position; progress.bufferedMs = ctrl.buffered; progress.durationMs = ctrl.durationMs
            delay(250)
        }
    }
    LaunchedEffect(controls, ctrl.playWhenReady, scrubbing, sheet, poke) {
        if (controls && ctrl.playWhenReady && !scrubbing && sheet == null) { delay(3000); controls = false }
    }
    // immersive: system bars hide and show together with the controls
    val window = activity?.window
    DisposableEffect(controls, window) {
        window?.let { w ->
            WindowCompat.getInsetsController(w, view).run {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (controls) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
    DisposableEffect(window) {
        onDispose {
            window?.let { w ->
                WindowCompat.getInsetsController(w, view).show(WindowInsetsCompat.Type.systemBars())
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }
    LaunchedEffect(seekFb) { if (seekFb != null) { delay(800); seekFb = null } }
    LaunchedEffect(flash) { if (flash != null) { delay(900); flash = null } }
    LaunchedEffect(levelTick) { if (level != null) { delay(700); level = null } }

    val v = ui.current ?: return
    LaunchedEffect(v.id) { zoom = 1f; pan = Offset.Zero }

    fun openSite(x: Video) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(x.link.ifBlank { com.sfm.app.data.SITE }))) } }
    fun share(x: Video) { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, x.link), null)) }
    fun act(block: () -> Unit): () -> Unit = { poke++; block() }

    val resize = lib.settings.resizeMode.coerceIn(0, 2)
    val loading = ctrl.resolving || ctrl.loadedId != v.id || (ctrl.buffering && ctrl.firstFrameId != v.id)
    val error = ctrl.error.takeIf { ctrl.loadedId == v.id }

    val callbacks = StageCallbacks(
        onTap = { controls = !controls },
        onSeekTap = { side ->
            val prev = seekFb
            seekFb = SeekFeedback(side, if (prev != null && prev.side == side) prev.seconds + 10 else 10, (prev?.tick ?: 0) + 1)
            ctrl.seekBy(side * PlayerController.SEEK_MS)
            controls = false
        },
        onLongPress = { pressed ->
            if (pressed) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); controls = false }
            ctrl.boost(pressed)
        },
        onPullUp = { sheet = Sheet.INFO },
        onMinimize = { ui.minimize() },
        onLevel = { zone, d ->
            when (zone) {
                LevelZone.BRIGHTNESS -> window?.let { w ->
                    val cur = w.attributes.screenBrightness.takeIf { it >= 0f } ?: systemBrightness(ctx)
                    val b = (cur + d).coerceIn(0.01f, 1f)
                    w.attributes = w.attributes.apply { screenBrightness = b }
                    level = zone to b
                }
                LevelZone.VOLUME -> {
                    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    if (volume[0] < 0f) volume[0] = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    volume[0] = (volume[0] + d * max * 1.2f).coerceIn(0f, max.toFloat())
                    runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, volume[0].roundToInt(), 0) }
                    level = zone to volume[0] / max
                }
            }
        },
        onLevelEnd = { volume[0] = -1f; levelTick++ },
        zoom = { zoom },
        onTransform = { centroid, panDelta, zc ->
            val nz = (zoom * zc).coerceIn(1f, 4f)
            val c = centroid - Offset(stage.width / 2f, stage.height / 2f)
            val raw = c - (c - pan) * (nz / zoom) + panDelta
            val (fw, fh) = frameSize(ctrl.aspect, resize, stage.width.toFloat(), stage.height.toFloat())
            zoom = nz
            pan = clampPan(raw, nz, fw, fh, stage.width.toFloat(), stage.height.toFloat())
            if (zc != 1f) flash = "%.1f×".format(nz).replace('.', ',')
        },
        onTransformEnd = { if (zoom < 1.05f) { zoom = 1f; pan = Offset.Zero } },
    )

    PlayerStage(landscape, callbacks, Modifier.onSizeChanged { stage = it }) {
        key(ui.session) {
            VideoPager(ui.queue, ui.index, { ui.index = it }, userScrollEnabled = zoom <= 1.01f) { _, item, active ->
                VideoPageContent(
                    item, active, ctrl.aspect, resize, zoom, pan,
                    showFrame = ctrl.firstFrameId == item.id,
                    loading = loading && !controls,
                    error = error,
                    onRetry = ctrl::retry,
                    onOpenSite = { openSite(item) },
                    onNext = if (ui.hasNext) ui::next else null,
                ) {
                    if (ui.mode == PlayerMode.EXPANDED) {
                        VideoSurface(ctrl, Modifier.fillMaxSize().onGloballyPositioned { ctrl.surfaceBounds = it.boundsInWindow().toAndroidRect() })
                    }
                }
            }
        }
        SeekRipple(seekFb)
        SpeedBadge(ctrl.boosted, Modifier.padding(top = 64.dp))
        level?.let { (zone, value) ->
            LevelIndicator(zone, value, Modifier.align(if (zone == LevelZone.BRIGHTNESS) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 56.dp))
        }
        FlashBadge(flash)
        AnimatedVisibility(controls, enter = fadeIn(tween(150)), exit = fadeOut(tween(220))) {
            val fav = v.id in lib.favIds
            PlayerControls(
                ControlsState(
                    title = v.title,
                    subtitle = listOfNotNull(v.cats.lastOrNull()?.name, v.artist).distinct().joinToString(" · "),
                    playing = ctrl.playWhenReady,
                    hasPrev = ui.hasPrev,
                    hasNext = ui.hasNext,
                    favorite = fav,
                    download = dl[v.id],
                    downloaded = lib.downloadsById[v.id] != null,
                    score = v.score,
                    vote = votes[v.id] ?: 0,
                    speed = lib.settings.speed,
                    resizeMode = resize,
                    landscape = landscape,
                    showCenter = error == null,
                    loading = loading,
                ),
                progress,
                ControlsActions(
                    onMinimize = ui::minimize,
                    onPip = act { if (activity == null || !PlayerHooks.enterPip(activity, app)) app.toast("Телефон не поддерживает мини-плеер") },
                    onMore = act { sheet = Sheet.MENU },
                    onPlayPause = act { ctrl.togglePlay() },
                    onPrev = act { ui.prev() },
                    onNext = act { ui.next() },
                    onSeek = { ms, final -> poke++; ctrl.seekTo(ms, exact = final) },
                    onScrubbing = { scrubbing = it },
                    onFavorite = act { app.toggleFavorite(v) },
                    onDownload = act { if (lib.downloadsById[v.id] == null || dl[v.id]?.failed == true) app.download(v) else app.toast("Уже скачано") },
                    onVote = { up ->
                        poke++
                        if (votes[v.id] != null) app.toast("Вы уже голосовали за это видео")
                        else scope.launch {
                            votes[v.id] = if (up) 1 else -1
                            val ok = runCatching { app.repo.vote(v, up) }.getOrDefault(false)
                            if (ok) ui.update(v.copy(score = (v.score ?: 0) + if (up) 1 else -1))
                            else { votes.remove(v.id); app.toast("Не удалось проголосовать") }
                        }
                    },
                    onShare = act { share(v) },
                    onResize = act {
                        val m = (resize + 1) % 3
                        app.library.settings { it.copy(resizeMode = m) }
                        flash = ResizeTitles[m]
                    },
                    onSpeed = act { sheet = Sheet.MENU },
                    onRotate = act {
                        activity?.requestedOrientation =
                            if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    },
                    onInfo = act { sheet = Sheet.INFO },
                ),
            )
        }
    }

    when (sheet) {
        Sheet.INFO -> InfoSheet(app, v) { sheet = null }
        Sheet.MENU -> ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Sfm.Surface,
            contentColor = Sfm.Text,
        ) {
            PlayerMenuContent(
                speed = lib.settings.speed,
                resizeMode = resize,
                onSpeed = { s -> app.library.settings { it.copy(speed = s) } },
                onResize = { m -> app.library.settings { it.copy(resizeMode = m) } },
                onOpenSite = { sheet = null; openSite(v) },
                onShare = { sheet = null; share(v) },
                onCopy = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("link", v.link))) }
                    app.toast("Ссылка скопирована"); sheet = null
                },
                onHide = { sheet = null; app.hide(v); ui.remove(v.id) },
                modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()),
            )
        }
        null -> Unit
    }
}

private fun systemBrightness(ctx: Context): Float =
    runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f).coerceIn(0.01f, 1f)

@Composable
private fun InfoSheet(app: AppState, v: Video, onDismiss: () -> Unit) {
    val lib by app.library.data.collectAsState()
    val catalog by app.repo.catalog.data.collectAsState()
    val ctx = LocalContext.current
    val artistTerm = remember(v.artist, catalog) {
        v.artist?.takeIf { it.isNotBlank() }?.let { name ->
            catalog.tags.firstOrNull { it.type == TermType.ARTIST && it.name.equals(name, true) } ?: catalog.tags.firstOrNull { it.name.equals(name, true) }
        }
    }
    val cats = remember(v.cats, catalog) { v.cats.map { c -> catalog.byCat[c.id] ?: Term(c.id, c.name, link = c.link, type = TermType.CHARACTER) } }
    val terms = buildList {
        v.artist?.takeIf { it.isNotBlank() }?.let { add(InfoTerm(it, TermType.ARTIST, artistTerm, artistTerm != null && lib.isSubscribed(artistTerm))) }
        cats.forEach { add(InfoTerm(it.name, it.type, it, lib.isSubscribed(it))) }
    }
    val tags by produceState<List<Term>?>(null, v.id) {
        value = runCatching { app.repo.tagsOf(v) }.getOrDefault(emptyList()).filter { t -> t.id != artistTerm?.id && !t.name.equals(v.artist, true) }
    }
    val feed = remember(cats.lastOrNull()?.id) { cats.lastOrNull()?.let { app.graph.feeds.get(FeedSource(FeedKind.TERM_NEW, term = it)) } }
    LaunchedEffect(feed) { feed?.ensure() }
    val items = feed?.items?.collectAsState()?.value.orEmpty()
    val state = feed?.state?.collectAsState()?.value
    val related = when {
        feed == null -> emptyList()
        items.isEmpty() && (state == LoadState.Loading || state == LoadState.Idle) -> null
        else -> lib.visible(items).filter { it.id != v.id }.take(20)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sfm.Surface, contentColor = Sfm.Text, sheetState = rememberModalBottomSheetState()) {
        InfoSheetContent(
            v, terms, tags, related,
            onTerm = { t -> onDismiss(); app.openTerm(t) },
            onToggleSub = app::toggleSub,
            onOpenLink = { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
            onRelated = { i -> related?.let { onDismiss(); app.player.open(it, i, feed) } },
            modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()),
        )
    }
}
