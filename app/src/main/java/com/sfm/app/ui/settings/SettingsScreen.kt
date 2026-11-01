package com.sfm.app.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.SingletonImageLoader
import com.sfm.app.BuildConfig
import com.sfm.app.R
import com.sfm.app.data.Library
import com.sfm.app.data.Settings
import com.sfm.app.data.SubsSync
import com.sfm.app.data.formatBytes
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.Screen
import com.sfm.app.ui.lock.PasswordField
import com.sfm.app.ui.lock.biometricAvailable
import com.sfm.app.ui.lock.showBiometricPrompt
import com.sfm.app.ui.theme.Sfm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val bio = remember { biometricAvailable(ctx) }
    SettingsContent(bio)
}

private class Choice(val title: String, val options: List<String>, val selected: Int, val onSelect: (Int) -> Unit)

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val LOCK_DELAYS = listOf(0 to "Сразу", 30 to "30 сек", 60 to "1 мин", 300 to "5 мин")
private val SUBS_HOURS = listOf(0, 3, 6, 12, 24)
private val RESIZE = listOf("Вписать", "Заполнить", "Растянуть")
private val COLUMNS = listOf("Авто", "1", "2", "3")

private fun speedLabel(f: Float) = if (f == 1f) "Обычная" else (if (f % 1f == 0f) "${f.toInt()}×" else "$f×").replace('.', ',')

@Composable
internal fun SettingsContent(bioAvailable: Boolean) {
    val app = LocalApp.current
    val lib = app.library
    val ctx = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val data by lib.data.collectAsState()
    val s = data.settings
    var choice by remember { mutableStateOf<Choice?>(null) }
    var changePw by remember { mutableStateOf(false) }
    var confirmCache by remember { mutableStateOf(false) }
    var confirmHistory by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf<Long?>(null) }
    var cacheTick by remember { mutableIntStateOf(0) }
    var clearing by remember { mutableStateOf(false) }

    LaunchedEffect(cacheTick) { cacheSize = withContext(Dispatchers.IO) { dirSize(ctx.cacheDir) } }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) app.toast("Уведомления запрещены — о новых видео подскажет только значок на вкладке «Подписки»")
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(lib.exportJson().toByteArray()) } }.isSuccess
            }
            app.toast(if (ok) "Библиотека сохранена" else "Не удалось сохранить файл")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull() }
            val before = lib.value
            if (text != null && lib.importJson(text)) {
                val after = lib.value
                app.toast("Импортировано: избранное +${after.favorites.size - before.favorites.size}, история +${after.history.size - before.history.size}")
            } else app.toast("Файл не похож на экспорт библиотеки SFM")
        }
    }

    fun setSubs(h: Int) {
        lib.settings { it.copy(subsCheckHours = h) }
        SubsSync.schedule(ctx, h)
        if (h > 0 && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun setBiometric(on: Boolean) {
        if (!on) { lib.settings { it.copy(biometric = false) }; return }
        // confirm with a real scan before relying on it
        val shown = showBiometricPrompt(ctx, "Отмена", onSuccess = {
            lib.settings { it.copy(biometric = true) }
            app.toast("Вход по отпечатку или лицу включён")
        })
        if (!shown) lib.settings { it.copy(biometric = true) }
    }

    fun clearCache() {
        clearing = true
        scope.launch {
            val before = cacheSize ?: 0
            withContext(Dispatchers.IO) {
                runCatching { SingletonImageLoader.get(ctx).run { memoryCache?.clear(); diskCache?.clear() } }
                runCatching { app.graph.net.client.cache?.evictAll() }
                listOf("images", "video", "http").forEach { d -> File(ctx.cacheDir, d).listFiles()?.forEach { runCatching { it.deleteRecursively() } } }
            }
            val after = withContext(Dispatchers.IO) { dirSize(ctx.cacheDir) }
            cacheSize = after
            clearing = false
            app.toast("Кэш очищен, освобождено ${formatBytes((before - after).coerceAtLeast(0))}")
        }
    }

    Surface(Modifier.fillMaxSize(), color = Sfm.Bg, contentColor = Sfm.Text) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Настройки") { app.back() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
                Section("Защита") {
                    SettingRow(Icons.Outlined.Key, Tint.Security, "Сменить пароль", onClick = { changePw = true })
                    if (bioAvailable) SwitchRow(Icons.Outlined.Fingerprint, Tint.Security, "Вход по отпечатку или лицу", "Пароль остаётся запасным способом", s.biometric, ::setBiometric)
                    val lockIdx = LOCK_DELAYS.indexOfFirst { it.first == s.lockDelaySec }
                    SettingRow(
                        Icons.Outlined.LockClock, Tint.Security, "Блокировать после", "Сколько приложение может быть свёрнуто без пароля",
                        value = LOCK_DELAYS.getOrNull(lockIdx)?.second ?: "${s.lockDelaySec} сек",
                    ) { choice = Choice("Блокировать после", LOCK_DELAYS.map { it.second }, lockIdx) { i -> lib.settings { it.copy(lockDelaySec = LOCK_DELAYS[i].first) } } }
                    SwitchRow(Icons.Outlined.Shield, Tint.Security, "Скрывать содержимое в недавних", "В списке приложений и на снимках экрана будет пусто", s.hideInRecents) { v -> lib.settings { it.copy(hideInRecents = v) } }
                }
                Section("Воспроизведение", note = "Видео всегда зациклены, следующее — свайпом влево") {
                    val sp = SPEEDS.indexOfFirst { it == s.speed }
                    SettingRow(Icons.Outlined.Speed, Tint.Playback, "Скорость по умолчанию", value = speedLabel(s.speed)) {
                        choice = Choice("Скорость по умолчанию", SPEEDS.map(::speedLabel), sp) { i -> lib.settings { it.copy(speed = SPEEDS[i]) } }
                    }
                    SwitchRow(Icons.Outlined.Restore, Tint.Playback, "Продолжать с места остановки", null, s.resume) { v -> lib.settings { it.copy(resume = v) } }
                    SwitchRow(Icons.Outlined.Headphones, Tint.Playback, "Фоновое воспроизведение", "Звук продолжается при выключенном экране", s.background) { v -> lib.settings { it.copy(background = v) } }
                    SwitchRow(Icons.Outlined.PictureInPictureAlt, Tint.Playback, "Мини-плеер при выходе из приложения", "Окно «картинка в картинке»", s.autoPip) { v -> lib.settings { it.copy(autoPip = v) } }
                    SwitchRow(Icons.AutoMirrored.Outlined.VolumeOff, Tint.Playback, "Без звука по умолчанию", null, s.muted) { v -> lib.settings { it.copy(muted = v) } }
                    SettingRow(Icons.Outlined.AspectRatio, Tint.Playback, "Масштаб видео", value = RESIZE.getOrElse(s.resizeMode) { RESIZE[0] }) {
                        choice = Choice("Масштаб видео", RESIZE, s.resizeMode) { i -> lib.settings { it.copy(resizeMode = i) } }
                    }
                }
                Section("Загрузки") {
                    SwitchRow(Icons.Outlined.Wifi, Tint.Downloads, "Только по Wi-Fi", "По мобильной сети скачивание будет ждать Wi-Fi", s.wifiOnly) { v -> lib.settings { it.copy(wifiOnly = v) } }
                    SwitchRow(Icons.Outlined.PhotoLibrary, Tint.Downloads, "Показывать скачанное в галерее", "Видео будут видны в «Галерее» и других плеерах", s.showInGallery) { v -> lib.settings { it.copy(showInGallery = v) } }
                    SettingRow(Icons.Outlined.FolderOpen, Tint.Downloads, "Папка", value = "Download/SFM")
                }
                Section("Вид") {
                    SettingRow(Icons.Outlined.GridView, Tint.View, "Колонки", "Сколько превью в ряд", value = COLUMNS.getOrElse(s.columns) { COLUMNS[0] }) {
                        choice = Choice("Колонки", COLUMNS, s.columns) { i -> lib.settings { it.copy(columns = i) } }
                    }
                    SwitchRow(Icons.Outlined.DoneAll, Tint.View, "Скрывать просмотренные", "Досмотренные видео не показываются в лентах", s.hideWatched) { v -> lib.settings { it.copy(hideWatched = v) } }
                }
                Section("Подписки") {
                    val idx = SUBS_HOURS.indexOf(s.subsCheckHours)
                    SettingRow(
                        Icons.Outlined.NotificationsActive, Tint.Subs, "Проверять новые видео", "Уведомление, когда в подписках появится новое",
                        value = if (s.subsCheckHours == 0) "Выкл" else "Каждые ${s.subsCheckHours} ч",
                    ) { choice = Choice("Проверять новые видео", SUBS_HOURS.map { if (it == 0) "Выкл" else "Каждые $it ч" }, idx) { i -> setSubs(SUBS_HOURS[i]) } }
                }
                Section("Контент") {
                    SettingRow(Icons.Outlined.Block, Tint.Content, "Чёрный список", "Разделы, которые не показываются нигде", value = "${data.blacklist.size}", chevron = true) { app.push(Screen.Blacklist) }
                    SettingRow(Icons.Outlined.VisibilityOff, Tint.Content, "Скрытые видео", "Вернуть можно в Библиотеке → Скрытые", value = "${data.hidden.size}")
                }
                Section("Данные") {
                    SettingRow(Icons.Outlined.CleaningServices, Tint.Data, "Очистить кэш", "Превью и временные файлы видео", value = if (clearing) "…" else cacheSize?.let(::formatBytes) ?: "…") { confirmCache = true }
                    SettingRow(Icons.Outlined.History, Tint.Data, "Очистить историю", "Просмотрено: ${data.history.size}") { confirmHistory = true }
                    SettingRow(Icons.Outlined.FileUpload, Tint.Data, "Экспорт библиотеки", "Избранное, история, подписки и чёрный список в файл") { exportLauncher.launch("sfm-library-${LocalDate.now()}.json") }
                    SettingRow(Icons.Outlined.FileDownload, Tint.Data, "Импорт библиотеки", "Добавить данные из файла экспорта") { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                }
                Section("О приложении") {
                    Row(Modifier.fillMaxWidth().background(Sfm.Surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_logo), null, Modifier.size(44.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("SFM", style = MaterialTheme.typography.titleMedium, color = Sfm.Text)
                            Text("Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim)
                        }
                    }
                    SettingRow(Icons.Outlined.BugReport, Tint.Data, "Скопировать отчёт для разработчика", "Версия, устройство и настройки — без пароля") {
                        val report = devReport(data.settings, data)
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("SFM report", report))) }
                        app.toast("Отчёт скопирован")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    choice?.let { c -> ChoiceDialog(c.title, c.options, c.selected, c.onSelect) { choice = null } }
    if (changePw) ChangePasswordDialog { changePw = false }
    if (confirmCache) ConfirmDialog(
        "Очистить кэш?", "Превью и кэш видео будут удалены. Скачанные файлы не затрагиваются", "Очистить",
        onConfirm = ::clearCache, onDismiss = { confirmCache = false },
    )
    if (confirmHistory) ConfirmDialog(
        "Очистить историю?", "Просмотренные видео и места остановки будут забыты", "Очистить",
        onConfirm = { lib.clearHistory(); app.toast("История очищена") }, onDismiss = { confirmHistory = false },
    )
}

private fun dirSize(f: File): Long = f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

private val reportJson = Json { encodeDefaults = true; prettyPrint = true }

internal fun devReport(settings: Settings, d: com.sfm.app.data.LibraryData) = buildString {
    appendLine("SFM ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Библиотека: избранное ${d.favorites.size}, история ${d.history.size}, скачано ${d.downloads.size}, скрыто ${d.hidden.size}, чёрный список ${d.blacklist.size}, подписки ${d.subs.size}")
    appendLine("Пароль задан: ${if (d.security.hash != null) "да" else "нет"}")
    appendLine("Настройки:")
    append(reportJson.encodeToString(Settings.serializer(), settings))
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val lib = app.library
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var rep by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun save() {
        if (busy) return
        error = when {
            cur.isEmpty() -> "Введите текущий пароль"
            new.length < 4 -> "Новый пароль: минимум 4 символа"
            new != rep -> "Пароли не совпадают"
            else -> null
        }
        if (error != null) return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.Default) { lib.checkPassword(cur) }
            when (r) {
                Library.Check.Ok -> {
                    withContext(Dispatchers.IO) { lib.setPassword(new) }
                    app.toast("Пароль изменён")
                    onDismiss()
                }
                Library.Check.Wrong -> { error = "Неверный текущий пароль"; cur = "" }
                is Library.Check.Wait -> error = "Слишком много попыток, подождите ${r.seconds} с"
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сменить пароль") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PasswordField(cur, { cur = it; error = null }, "Текущий пароль", show, { show = !show }, Modifier.fillMaxWidth(), imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                Spacer(Modifier.height(8.dp))
                PasswordField(new, { new = it; error = null }, "Новый пароль", show, { show = !show }, Modifier.fillMaxWidth(), supporting = "Минимум 4 символа", imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                PasswordField(rep, { rep = it; error = null }, "Повторите новый пароль", show, { show = !show }, Modifier.fillMaxWidth(), onIme = ::save)
                if (error != null) Text(error!!, Modifier.padding(top = 10.dp, start = 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Sfm.Text) } },
        containerColor = Sfm.Surface2,
    )
}
