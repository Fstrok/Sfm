package com.sfm.app.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sfm.app.data.Term
import com.sfm.app.data.TermType
import com.sfm.app.ui.theme.Sfm
import java.util.Locale
import kotlin.math.abs

private val termPalette = listOf(
    Color(0xFF00DBA3), Color(0xFF8B6CFF), Color(0xFFFF5C7A), Color(0xFF2EB8F0),
    Color(0xFFFFB547), Color(0xFFE070FF), Color(0xFF5BE38A), Color(0xFFFF8A4C),
)

/** Stable accent color of a term (same name → same color everywhere). */
fun termColor(t: Term): Color = termPalette[Math.floorMod(t.name.lowercase().hashCode(), termPalette.size)]

/** 1328 → "1 328" (non-breaking space). */
fun Int.spaced(): String = "%,d".format(Locale.US, this).replace(',', ' ')

fun pluralRu(n: Int, one: String, few: String, many: String): String {
    val a = abs(n) % 100
    val b = a % 10
    return when { a in 11..14 -> many; b == 1 -> one; b in 2..4 -> few; else -> many }
}

fun videosLabel(n: Int) = "${n.spaced()} видео"

val TermType.icon: ImageVector
    get() = when (this) {
        TermType.GAME -> Icons.Outlined.SportsEsports
        TermType.CHARACTER -> Icons.Outlined.Person
        TermType.ARTIST -> Icons.Outlined.Brush
        TermType.TAG -> Icons.Outlined.Tag
    }

fun initials(name: String): String {
    val w = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    return (if (w.size == 1 && w[0].length <= 2) w[0] else w.take(2).joinToString("") { it.take(1) }).uppercase().ifEmpty { "#" }
}

/** Soft tinted circle with the term's initials (tags show a # glyph). */
@Composable
fun TermAvatar(t: Term, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val c = termColor(t)
    Box(modifier.size(size).clip(CircleShape).background(c.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        if (t.type == TermType.TAG) Icon(Icons.Outlined.Tag, null, Modifier.size(size * 0.5f), tint = c)
        else Text(initials(t.name), color = c, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp, maxLines = 1)
    }
}

@Composable
fun SubscribeBell(subscribed: Boolean, name: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconSize: Dp = 22.dp) {
    IconButton(onClick = onClick, modifier = modifier) {
        Crossfade(subscribed, label = "bell") { on ->
            Icon(
                if (on) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationAdd,
                if (on) "Отписаться от «$name»" else "Подписаться на «$name»",
                Modifier.size(iconSize),
                tint = if (on) Sfm.Accent else Sfm.TextDim,
            )
        }
    }
}

/** Name with the matched part of [query] highlighted. */
fun highlight(name: String, query: String?, color: Color = Sfm.Accent) = buildAnnotatedString {
    val q = query?.trim().orEmpty()
    val i = if (q.isEmpty()) -1 else name.indexOf(q, ignoreCase = true)
    if (i < 0) append(name) else {
        append(name.substring(0, i))
        withStyle(SpanStyle(color = color)) { append(name.substring(i, i + q.length)) }
        append(name.substring(i + q.length))
    }
}

/** List row: avatar, name, subtitle; the whole row opens the term, the bell toggles the subscription. */
@Composable
fun TermRow(
    t: Term,
    subtitle: String,
    subscribed: Boolean,
    onClick: () -> Unit,
    onToggleSub: () -> Unit,
    modifier: Modifier = Modifier,
    query: String? = null,
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TermAvatar(t)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(highlight(t.name, query), style = MaterialTheme.typography.titleSmall, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SubscribeBell(subscribed, t.name, onToggleSub)
    }
}

/** Rounded chip for term shortcuts (popular, related, matching terms). */
@Composable
fun TermChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, count: Int? = null, tint: Color = Sfm.Accent) {
    Row(
        modifier.height(34.dp).clip(CircleShape).background(Sfm.Surface2).border(1.dp, Sfm.Outline.copy(alpha = 0.7f), CircleShape)
            .clickable(onClick = onClick).padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = tint)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(count.spaced(), style = MaterialTheme.typography.labelMedium, color = Sfm.TextDim, maxLines = 1)
        }
    }
}

/** Tag chip split in two touch areas: name opens the tag, the bell toggles the subscription. */
@Composable
fun TagChip(t: Term, subscribed: Boolean, onClick: () -> Unit, onToggleSub: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.height(36.dp).clip(shape)
            .background(if (subscribed) Sfm.Accent.copy(alpha = 0.13f) else Sfm.Surface2)
            .border(1.dp, if (subscribed) Sfm.Accent.copy(alpha = 0.45f) else Sfm.Outline.copy(alpha = 0.7f), shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.fillMaxHeight().clickable(onClick = onClick).padding(start = 13.dp, end = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.name, style = MaterialTheme.typography.labelLarge, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp))
            Text(t.count.spaced(), style = MaterialTheme.typography.labelMedium, color = Sfm.TextDim, maxLines = 1)
        }
        Box(Modifier.width(1.dp).height(16.dp).background(Sfm.Outline))
        Box(
            Modifier.fillMaxHeight().width(34.dp)
                .semantics { role = Role.Button; contentDescription = if (subscribed) "Отписаться от «${t.name}»" else "Подписаться на «${t.name}»" }
                .clickable(onClick = onToggleSub),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (subscribed) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationAdd, null,
                Modifier.size(16.dp), tint = if (subscribed) Sfm.Accent else Sfm.TextDim,
            )
        }
    }
}

/** Section caption with an optional trailing action. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Sfm.Text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge, color = Sfm.Accent,
            )
        }
    }
}
