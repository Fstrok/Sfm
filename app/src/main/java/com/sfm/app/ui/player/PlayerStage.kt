package com.sfm.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class LevelZone { BRIGHTNESS, VOLUME }

/** Everything the stage reports; all lambdas are read at event time. */
class StageCallbacks(
    val onTap: () -> Unit = {},
    /** Double tap (and quick follow-up taps) on a half: -1 left, +1 right. */
    val onSeekTap: (side: Int) -> Unit = {},
    val onLongPress: (pressed: Boolean) -> Unit = {},
    val onPullUp: () -> Unit = {},
    val onMinimize: () -> Unit = {},
    /** Vertical swipe in a landscape side zone; [delta] is a fraction of the height, up = positive. */
    val onLevel: (zone: LevelZone, delta: Float) -> Unit = { _, _ -> },
    val onLevelEnd: () -> Unit = {},
    /** Current zoom; pinch/pan only when [zoomEnabled]. */
    val zoom: () -> Float = { 1f },
    val onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit = { _, _, _ -> },
    val onTransformEnd: () -> Unit = {},
)

/**
 * Full-screen player container. Children (pager, controls) get events first; the stage only
 * claims a gesture after its own slop, and a horizontal drag the pager already took is left alone.
 *
 * Portrait: drag down anywhere follows the finger (shrinks, backdrop fades revealing the app) and
 * minimizes past 25 % height or on a fling; drag up opens the info sheet.
 * Landscape: left 35 % = brightness, right 35 % = volume, center = pull-down.
 */
@Composable
fun PlayerStage(
    landscape: Boolean,
    callbacks: StageCallbacks,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val cb by rememberUpdatedState(callbacks)
    val land by rememberUpdatedState(landscape)
    val pull = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = (1f - pull.value / size.height * 1.6f).coerceIn(0f, 1f)) }
            .testTag("playerStage")
            .pointerInput(Unit) { stageGestures(scope, pull, { cb }, { land }) },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val f = (pull.value / size.height).coerceIn(0f, 1f)
                translationY = pull.value
                val s = 1f - 0.22f * f
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0f)
                shape = RoundedCornerShape((56 * f.coerceAtMost(0.5f)).dp)
                clip = f > 0f
            },
            content = content,
        )
    }
}

private enum class Claim { NONE, CHILD, TAP, LONG_PRESS, TRANSFORM, PULL_DOWN, PULL_UP, LEVEL }

private suspend fun PointerInputScope.stageGestures(
    scope: CoroutineScope,
    pull: Animatable<Float, *>,
    cb: () -> StageCallbacks,
    landscape: () -> Boolean,
) {
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
    var streakUntil = 0L
    awaitPointerEventScope {
        while (true) {
            // ---- wait for the first finger (a button may have consumed the down: still ours to drag) ----
            var down: PointerInputChange? = null
            while (down == null) down = awaitPointerEvent(PointerEventPass.Main).changes.fastFirstOrNull { it.pressed && !it.previousPressed }
            val onButton = down.isConsumed
            val pointer = down.id
            val start = down.position
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val zone = when {
                !landscape() -> null
                start.x < w * 0.35f -> LevelZone.BRIGHTNESS
                start.x > w * 0.65f -> LevelZone.VOLUME
                else -> null
            }
            var claim = Claim.NONE
            var total = Offset.Zero
            var upPos = start
            var lastTime = down.uptimeMillis
            // ---- decide what this gesture is ----
            while (claim == Claim.NONE) {
                val wait = if (onButton) Long.MAX_VALUE else (longPressMs - (lastTime - down.uptimeMillis)).coerceAtLeast(1)
                val ev = withTimeoutOrNull(wait) { awaitPointerEvent(PointerEventPass.Main) }
                if (ev == null) { claim = Claim.LONG_PRESS; break }
                val ch = ev.changes.fastFirstOrNull { it.id == pointer }
                if (ch != null) lastTime = ch.uptimeMillis
                when {
                    ch == null -> claim = Claim.CHILD
                    ev.changes.count { it.pressed } >= 2 -> claim = Claim.TRANSFORM
                    ch.isConsumed -> claim = Claim.CHILD
                    !ch.pressed -> {
                        claim = if (total.getDistance() < slop * 1.5f) Claim.TAP else Claim.CHILD
                        upPos = ch.position
                    }
                    else -> {
                        total += ch.positionChange()
                        if (cb().zoom() > 1.01f) {
                            if (total.getDistance() > slop) claim = Claim.TRANSFORM
                        } else if (abs(total.y) > slop && abs(total.y) > abs(total.x) * 1.15f) {
                            claim = when {
                                zone != null -> Claim.LEVEL
                                total.y > 0 -> Claim.PULL_DOWN
                                !landscape() -> Claim.PULL_UP
                                else -> Claim.CHILD
                            }
                            if (claim != Claim.CHILD) ch.consume()
                        }
                    }
                }
            }
            when (claim) {
                Claim.TAP -> {
                    val side = if (upPos.x < w / 2) -1 else 1
                    if (lastTime < streakUntil) {
                        // quick follow-up tap after a double tap keeps seeking
                        streakUntil = lastTime + STREAK_MS
                        cb().onSeekTap(side)
                    } else {
                        val second = withTimeoutOrNull(doubleTapMs) {
                            var d: PointerInputChange? = null
                            while (d == null) d = awaitPointerEvent(PointerEventPass.Main).changes.fastFirstOrNull { it.pressed && !it.previousPressed }
                            d
                        }
                        if (second == null) cb().onTap()
                        else {
                            val up = awaitUp(second.id, slop)
                            if (up != null) {
                                streakUntil = up.uptimeMillis + STREAK_MS
                                cb().onSeekTap(if (up.position.x < w / 2) -1 else 1)
                            }
                        }
                    }
                }
                Claim.LONG_PRESS -> {
                    cb().onLongPress(true)
                    waitAllUp(PointerEventPass.Initial, consume = true)
                    cb().onLongPress(false)
                }
                Claim.TRANSFORM -> {
                    var event: PointerEvent
                    do {
                        event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.fastAny { it.isConsumed }) continue
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom != 1f || pan != Offset.Zero) cb().onTransform(event.calculateCentroid(useCurrent = true), pan, zoom)
                        event.changes.fastForEach { if (it.positionChange() != Offset.Zero) it.consume() }
                    } while (event.changes.fastAny { it.pressed })
                    cb().onTransformEnd()
                }
                Claim.LEVEL -> {
                    trackDrag(pointer) { dy -> cb().onLevel(zone!!, -dy / h) }
                    cb().onLevelEnd()
                }
                Claim.PULL_DOWN -> {
                    val vt = VelocityTracker()
                    vt.addPosition(down.uptimeMillis, down.position)
                    var target = pull.value
                    trackDrag(pointer, vt) { dy -> target = (target + dy).coerceAtLeast(0f); scope.launch { pull.snapTo(target) } }
                    val v = vt.calculateVelocity().y
                    val fling = 1100.dp.toPx()
                    scope.launch {
                        if (pull.value > h * 0.25f || (v > fling && pull.value > slop)) {
                            pull.animateTo(h, tween(180))
                            cb().onMinimize()
                        } else pull.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                    }
                }
                Claim.PULL_UP -> {
                    var dragged = 0f
                    val vt = VelocityTracker()
                    vt.addPosition(down.uptimeMillis, down.position)
                    trackDrag(pointer, vt) { dy -> dragged += dy; scope.launch { pull.snapTo((dragged * 0.25f).coerceAtMost(0f)) } }
                    scope.launch { pull.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                    if (dragged < -48.dp.toPx() || vt.calculateVelocity().y < -900.dp.toPx()) cb().onPullUp()
                }
                Claim.CHILD, Claim.NONE -> waitAllUp(PointerEventPass.Main, consume = false)
            }
        }
    }
}

private const val STREAK_MS = 750L

/** Follows one pointer, consuming its moves before children see them, until it lifts. */
private suspend fun AwaitPointerEventScope.trackDrag(pointer: PointerId, vt: VelocityTracker? = null, onDy: (Float) -> Unit) {
    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Initial)
        val ch = ev.changes.fastFirstOrNull { it.id == pointer } ?: break
        vt?.addPosition(ch.uptimeMillis, ch.position)
        if (!ch.pressed) { ch.consume(); break }
        val dy = ch.positionChange().y
        ch.consume()
        if (dy != 0f) onDy(dy)
    }
}

private suspend fun AwaitPointerEventScope.awaitUp(pointer: PointerId, slop: Float): PointerInputChange? {
    var moved = Offset.Zero
    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Main)
        val ch = ev.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (ch.isConsumed) { waitAllUp(PointerEventPass.Main, false); return null }
        if (!ch.pressed) { ch.consume(); return ch }
        moved += ch.positionChange()
        if (moved.getDistance() > slop * 2) { waitAllUp(PointerEventPass.Main, false); return null }
    }
}

private suspend fun AwaitPointerEventScope.waitAllUp(pass: PointerEventPass, consume: Boolean) {
    if (currentEvent.changes.none { it.pressed }) return
    while (true) {
        val ev = awaitPointerEvent(pass)
        if (consume) ev.changes.fastForEach { it.consume() }
        if (ev.changes.none { it.pressed }) return
    }
}
