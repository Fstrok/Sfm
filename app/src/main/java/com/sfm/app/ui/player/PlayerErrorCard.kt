package com.sfm.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sfm.app.ui.theme.Sfm

/** In-player failure card: retry, open on the site, skip to the next video. */
@Composable
fun PlayerErrorCard(
    message: String,
    onRetry: () -> Unit,
    onOpenSite: () -> Unit,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(24.dp)
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE6141420))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Sfm.Hot.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.VideocamOff, null, Modifier.size(28.dp), tint = Sfm.Hot)
        }
        Spacer(Modifier.height(14.dp))
        Text("Видео не воспроизводится", style = MaterialTheme.typography.titleMedium, color = Sfm.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sfm.Accent, contentColor = Color(0xFF00241A)),
        ) { ButtonContent(Icons.Rounded.Refresh, "Повторить") }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenSite, modifier = Modifier.weight(1f).height(46.dp), contentPadding = ButtonDefaults.TextButtonContentPadding) {
                ButtonContent(Icons.AutoMirrored.Rounded.OpenInNew, "На сайте", Sfm.Text)
            }
            if (onNext != null) {
                OutlinedButton(onClick = onNext, modifier = Modifier.weight(1f).height(46.dp), contentPadding = ButtonDefaults.TextButtonContentPadding) {
                    ButtonContent(Icons.Rounded.SkipNext, "Следующее", Sfm.Text)
                }
            }
        }
    }
}

@Composable
private fun ButtonContent(icon: ImageVector, text: String, color: Color = Color.Unspecified) {
    Icon(icon, null, Modifier.size(18.dp), tint = if (color == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else color)
    Spacer(Modifier.size(6.dp))
    Text(text, maxLines = 1, color = color, style = MaterialTheme.typography.labelLarge)
}
