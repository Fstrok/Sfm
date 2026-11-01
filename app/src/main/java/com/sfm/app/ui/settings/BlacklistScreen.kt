package com.sfm.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sfm.app.data.Term
import com.sfm.app.data.TermType
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.common.EmptyState
import com.sfm.app.ui.lock.FieldShape
import com.sfm.app.ui.lock.sfmFieldColors
import com.sfm.app.ui.theme.Sfm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun TermType.icon(): ImageVector = when (this) {
    TermType.GAME -> Icons.Outlined.SportsEsports
    TermType.CHARACTER -> Icons.Outlined.Person
    TermType.ARTIST -> Icons.Outlined.Brush
    TermType.TAG -> Icons.Outlined.Tag
}

private fun TermType.tint(): Color = when (this) {
    TermType.GAME -> Color(0xFF2FB8FF)
    TermType.CHARACTER -> Sfm.Accent
    TermType.ARTIST -> Color(0xFFFFB547)
    TermType.TAG -> Sfm.Accent2
}

/** Terms whose videos are hidden everywhere: search the site catalog to add, ✕ to remove. */
@Composable
fun BlacklistScreen() {
    val app = LocalApp.current
    val lib = app.library
    val catalog = app.graph.catalog
    val data by lib.data.collectAsState()
    val cat by catalog.data.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        loading = true
        failed = runCatching { catalog.ensureAll() }.isFailure
        loading = false
    }
    val suggestions by produceState(emptyList<Term>(), q, cat, data.blacklist) {
        if (q.isBlank()) { value = emptyList(); return@produceState }
        delay(120)
        value = withContext(Dispatchers.Default) { catalog.search(q, 30).filterNot { data.isBlacklisted(it) }.take(8) }
    }
    val catalogEmpty = cat.categories.isEmpty() && cat.tags.isEmpty()

    Surface(Modifier.fillMaxSize(), color = Sfm.Bg, contentColor = Sfm.Text) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Чёрный список") { app.back() }
            LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "intro") {
                    Text(
                        "Видео с этими разделами скрыты везде: в лентах, поиске и подписках",
                        Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp),
                        style = MaterialTheme.typography.bodyMedium, color = Sfm.TextDim,
                    )
                }
                item(key = "search") {
                    OutlinedTextField(
                        value = q, onValueChange = { q = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        singleLine = true,
                        placeholder = { Text("Персонаж, игра, автор или тег") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Outlined.Close, "Очистить") } },
                        shape = FieldShape,
                        colors = sfmFieldColors(),
                    )
                }
                if (q.isNotBlank()) {
                    when {
                        suggestions.isNotEmpty() -> {
                            item(key = "sugg-title") { Caption("Добавить в список") }
                            item(key = "sugg") {
                                Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(Sfm.Outline.copy(alpha = 0.55f)), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    suggestions.forEach { t ->
                                        TermRow(t, onClick = {
                                            lib.toggleBlacklist(t)
                                            app.toast("«${t.name}» в чёрном списке")
                                            q = ""
                                        }) { Icon(Icons.Outlined.Add, "Добавить", Modifier.size(24.dp), tint = Sfm.Accent) }
                                    }
                                }
                            }
                        }
                        loading && catalogEmpty -> item(key = "loading") {
                            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Загружаю разделы сайта…", color = Sfm.TextDim)
                            }
                        }
                        failed && catalogEmpty -> item(key = "failed") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Не удалось загрузить разделы", Modifier.weight(1f), color = Sfm.TextDim)
                                TextButton(onClick = { attempt++ }) { Text("Повторить") }
                            }
                        }
                        else -> item(key = "none") { Text("Ничего не найдено", Modifier.padding(20.dp), color = Sfm.TextDim) }
                    }
                }
                if (data.blacklist.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(Icons.Outlined.Block, "Чёрный список пуст", "Найдите персонажа, игру или тег, которые не хотите видеть, и добавьте их сюда")
                    }
                } else {
                    item(key = "list-title") { Caption("В списке · ${data.blacklist.size}") }
                    item(key = "list") {
                        Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(Sfm.Outline.copy(alpha = 0.55f)), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            data.blacklist.asReversed().forEach { t ->
                                TermRow(t, onClick = null) {
                                    IconButton(onClick = {
                                        lib.toggleBlacklist(t)
                                        app.toast("«${t.name}» убран из чёрного списка", "Вернуть") { if (!lib.value.isBlacklisted(t)) lib.toggleBlacklist(t) }
                                    }) { Icon(Icons.Outlined.Close, "Убрать «${t.name}»", tint = Sfm.TextDim) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = Sfm.TextDim)
}

@Composable
private fun TermRow(t: Term, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Sfm.Surface).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(t.type.tint().copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(t.type.icon(), null, Modifier.size(19.dp), tint = t.type.tint())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.bodyLarge, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(t.type.title, t.count.takeIf { it > 0 }?.let { "$it видео" }).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim)
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing() }
    }
}
