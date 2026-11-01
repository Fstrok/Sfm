package com.sfm.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Sfm {
    val Accent = Color(0xFF00DBA3)
    val Accent2 = Color(0xFF7C5CFF)
    val Hot = Color(0xFFFF4D6D)
    val Bg = Color(0xFF0B0B12)
    val Surface = Color(0xFF14141E)
    val Surface2 = Color(0xFF1C1C29)
    val Surface3 = Color(0xFF262636)
    val Text = Color(0xFFF2F2F7)
    val TextDim = Color(0xFFA0A0B4)
    val Outline = Color(0xFF2E2E40)
    val brand = Brush.linearGradient(listOf(Accent, Color(0xFF00A8E8), Accent2))
    val scrimBottom = Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
}

private val scheme = darkColorScheme(
    primary = Sfm.Accent,
    onPrimary = Color(0xFF00241A),
    primaryContainer = Color(0xFF00382A),
    onPrimaryContainer = Color(0xFF7DF8CF),
    secondary = Sfm.Accent2,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C2453),
    onSecondaryContainer = Color(0xFFE2DAFF),
    tertiary = Sfm.Hot,
    background = Sfm.Bg,
    onBackground = Sfm.Text,
    surface = Sfm.Bg,
    onSurface = Sfm.Text,
    surfaceVariant = Sfm.Surface2,
    onSurfaceVariant = Sfm.TextDim,
    surfaceContainerLowest = Color(0xFF07070C),
    surfaceContainerLow = Sfm.Surface,
    surfaceContainer = Sfm.Surface,
    surfaceContainerHigh = Sfm.Surface2,
    surfaceContainerHighest = Sfm.Surface3,
    outline = Sfm.Outline,
    outlineVariant = Color(0xFF23232F),
    error = Color(0xFFFF6B6B),
    inverseSurface = Color(0xFFE8E8F0),
    inverseOnSurface = Color(0xFF15151C),
    scrim = Color.Black,
)

private val base = Typography()
private val typography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun SfmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

val TextStyle.dim get() = copy(color = Sfm.TextDim)
