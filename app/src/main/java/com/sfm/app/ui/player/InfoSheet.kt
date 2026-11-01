package com.sfm.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sfm.app.data.Term
import com.sfm.app.data.TermType
import com.sfm.app.data.Video
import com.sfm.app.data.formatCount
import com.sfm.app.data.timeAgo
import com.sfm.app.ui.common.Pill
import com.sfm.app.ui.common.Thumb
import com.sfm.app.ui.common.shimmer
import com.sfm.app.ui.theme.Sfm

/** A term row in the info sheet; [term] null = name known only as text (not clickable, no subscription). */
@Immutable
data class InfoTerm(val name: String, val type: TermType, val term: Term?, val subscribed: Boolean)

/** Video details: stats, artist and categories with subscribe buttons, tags, description, similar videos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoSheetContent(
    v: Video,
    terms: List<InfoTerm>,
    tags: List<Term>?,
    related: List<Video>?,
    onTerm: (Term) -> Unit,
    onToggleSub: (Term) -> Unit,
    onOpenLink: (String) -> Unit,
    onRelated: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("infoSheet")) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(v.title, style = MaterialTheme.typography.titleLarge, color = Sfm.Text)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                v.durationLabel?.let { Pill(it, Modifier.align(Alignment.CenterVertically), bg = Sfm.Surface3, fg = Sfm.Text) }
                v.views?.let { Stat(Icons.Rounded.Visibility, "${formatCount(it)} просм.") }
                v.score?.let { Stat(Icons.Rounded.ThumbUp, formatCount(it.toLong())) }
                timeAgo(v.date).takeIf { it.isNotEmpty() }?.let { Stat(Icons.Rounded.Schedule, it) }
            }
        }
        if (terms.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            terms.forEach { TermRow(it, onTerm, onToggleSub) }
        }
        if (!tags.isNullOrEmpty()) {
            SectionTitle("Теги")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t ->
                    Text(
                        "#${t.name}",
                        Modifier.clip(RoundedCornerShape(50)).background(Sfm.Surface3).clickable { onTerm(t) }.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge, color = Sfm.Text, maxLines = 1,
                    )
                }
            }
        }
        val desc = v.description?.trim().orEmpty()
        if (desc.isNotEmpty()) {
            SectionTitle("Описание")
            Text(desc, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium, color = Sfm.Text.copy(alpha = 0.88f))
        }
        val src = v.sourceUrl
        if (!src.isNullOrBlank()) {
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().clickable { onOpenLink(src) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = Sfm.Accent)
                Spacer(Modifier.width(10.dp))
                Text("Источник: " + src.substringAfter("://").substringBefore('/').removePrefix("www."), style = MaterialTheme.typography.bodyMedium, color = Sfm.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (related == null || related.isNotEmpty()) {
            SectionTitle("Похожие")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (related == null) items(3) { RelatedSkeleton() }
                else itemsIndexed(related, key = { _, it -> it.id }) { i, r -> RelatedCard(r) { onRelated(i) } }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Stat(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = Sfm.TextDim)
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Sfm.TextDim)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = Sfm.Text)
}

@Composable
private fun TermRow(t: InfoTerm, onTerm: (Term) -> Unit, onToggleSub: (Term) -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (t.term != null) Modifier.clickable { onTerm(t.term) } else Modifier).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Sfm.Accent.copy(alpha = 0.85f), Sfm.Accent2.copy(alpha = 0.85f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(t.name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.titleSmall, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.type.title, style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim)
        }
        if (t.term != null) {
            Spacer(Modifier.width(8.dp))
            SubscribeButton(t.subscribed) { onToggleSub(t.term) }
        }
    }
}

@Composable
fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(36.dp).clip(shape)
            .then(if (subscribed) Modifier.background(Sfm.Surface3).border(1.dp, Sfm.Outline, shape) else Modifier.background(Sfm.Accent))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (subscribed) Sfm.Text else Color(0xFF00241A)
        Icon(if (subscribed) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone, null, Modifier.size(18.dp), tint = if (subscribed) Sfm.Accent else fg)
        Spacer(Modifier.width(6.dp))
        Text(if (subscribed) "Подписка" else "Подписаться", color = fg, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
private fun RelatedCard(v: Video, onClick: () -> Unit) {
    Column(Modifier.width(176.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            Thumb(v, Modifier.matchParentSize())
            v.durationLabel?.let { Pill(it, Modifier.align(Alignment.BottomEnd).padding(5.dp)) }
        }
        Text(
            v.title, Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp), style = MaterialTheme.typography.bodyMedium,
            color = Sfm.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2,
        )
        val meta = listOfNotNull(v.views?.let { formatCount(it) + " просм." }, timeAgo(v.date).ifEmpty { null }).joinToString(" · ")
        Text(meta, Modifier.padding(horizontal = 2.dp), style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, maxLines = 1)
    }
}

@Composable
private fun RelatedSkeleton() {
    Column(Modifier.width(176.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).shimmer())
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.9f).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(4.dp)).shimmer())
    }
}
