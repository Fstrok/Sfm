package com.sfm.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sfm.app.ui.theme.Sfm
import kotlin.math.min
import kotlin.math.roundToInt

/** Accumulated double-tap seek: side -1/+1, total seconds, [tick] changes on every tap (restarts the ripple). */
data class SeekFeedback(val side: Int, val seconds: Int, val tick: Int)

/** YouTube-like half-screen arc with a ripple and "−20 с". */
@Composable
fun BoxScope.SeekRipple(fb: SeekFeedback?) {
    val left = (fb?.side ?: -1) < 0
    // the arc covers the video band, not the whole tall portrait screen
    val h = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.let { min(it.height, (it.width * 0.8f).toInt()) }.toDp() }
    AnimatedVisibility(
        fb != null, enter = fadeIn(tween(90)), exit = fadeOut(tween(260)),
        modifier = Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd).heightIn(max = h).fillMaxHeight().fillMaxWidth(0.42f),
    ) {
        val ripple = remember { Animatable(0f) }
        LaunchedEffect(fb?.tick) { ripple.snapTo(0f); ripple.animateTo(1f, tween(450)) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().clipToBounds()) {
                // arc: a large circle bulging toward the screen center
                val r = size.height * 1.1f
                val fade = Brush.verticalGradient(0f to Color.Transparent, 0.18f to Color.White.copy(alpha = 0.10f), 0.82f to Color.White.copy(alpha = 0.10f), 1f to Color.Transparent)
                drawCircle(fade, r, Offset(if (left) size.width - r else r, size.height / 2))
                val rr = size.minDimension * 0.55f * ripple.value
                drawCircle(Color.White.copy(alpha = 0.18f * (1f - ripple.value)), rr, Offset(size.width / 2, size.height / 2))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (left) Icons.Rounded.FastRewind else Icons.Rounded.FastForward, null, Modifier.size(34.dp), tint = Color.White)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${if (left) "−" else "+"}${fb?.seconds ?: 10} с",
                    color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/** "2×" pill shown at the top while the long press is held. */
@Composable
fun BoxScope.SpeedBadge(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier.align(Alignment.TopCenter), enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f)) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("2×", color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.FastForward, null, Modifier.size(18.dp), tint = Sfm.Accent)
        }
    }
}

/** Brightness / volume level: vertical capsule with fill, icon and percent. */
@Composable
fun LevelIndicator(zone: LevelZone, level: Float, modifier: Modifier = Modifier) {
    val icon = when (zone) {
        LevelZone.BRIGHTNESS -> if (level < 0.4f) Icons.Rounded.BrightnessLow else Icons.Rounded.BrightnessHigh
        LevelZone.VOLUME -> when {
            level <= 0.001f -> Icons.AutoMirrored.Rounded.VolumeOff
            level < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
            else -> Icons.AutoMirrored.Rounded.VolumeUp
        }
    }
    Column(
        modifier.width(52.dp).clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = Color.White)
        Canvas(Modifier.width(6.dp).height(140.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.width / 2)
            drawRoundRect(Color.White.copy(alpha = 0.22f), cornerRadius = r)
            val h = size.height * level.coerceIn(0f, 1f)
            drawRoundRect(Sfm.Accent, Offset(0f, size.height - h), Size(size.width, h), r)
        }
        Text("${(level * 100).roundToInt()}", color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
    }
}

/** Short centered message ("Заполнить", "1,5×"). */
@Composable
fun BoxScope.FlashBadge(text: String?) {
    AnimatedVisibility(text != null, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
        Text(
            text.orEmpty(),
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 18.dp, vertical = 10.dp),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
