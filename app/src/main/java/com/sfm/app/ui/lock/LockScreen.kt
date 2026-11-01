package com.sfm.app.ui.lock

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import com.sfm.app.R
import com.sfm.app.data.Library
import com.sfm.app.ui.LocalApp
import com.sfm.app.ui.theme.Sfm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Password gate: first launch creates a password, later launches ask for it (or fingerprint / face). */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val bio = remember { biometricAvailable(ctx) }
    LockContent(onUnlocked, bio)
}

@Composable
internal fun LockContent(onUnlocked: () -> Unit, bioAvailable: Boolean) {
    val lib = LocalApp.current.library
    val setup = remember { !lib.hasPassword }
    Surface(
        Modifier.fillMaxSize()
            // swallow touches so nothing under the lock reacts
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        color = Color.Transparent,
        contentColor = Sfm.Text,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().brandGlow().systemBarsPadding().imePadding()) {
            val wide = maxWidth > maxHeight && maxHeight < 560.dp
            if (setup) SetupForm(wide, bioAvailable, onUnlocked) else UnlockForm(wide, bioAvailable && lib.settings.biometric, onUnlocked)
        }
    }
}

/** Portrait: logo above the form, centred and scrollable. Landscape: logo on the left, form on the right. */
@Composable
private fun LockLayout(wide: Boolean, title: String, subtitle: String?, form: @Composable () -> Unit) {
    if (wide) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Header(title, subtitle, logo = 72.dp) }
            Spacer(Modifier.width(24.dp))
            Column(
                Modifier.weight(1.2f).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) { form() }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Header(title, subtitle, logo = 96.dp)
                Spacer(Modifier.height(32.dp))
                form()
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String?, logo: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(R.drawable.ic_logo), "SFM",
            Modifier.size(logo).drawBehind {
                val r = size.minDimension * 1.05f
                drawCircle(Brush.radialGradient(listOf(Sfm.Accent.copy(alpha = 0.30f), Sfm.Accent2.copy(alpha = 0.10f), Color.Transparent), center, r), r)
            },
        )
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Sfm.Text, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Sfm.TextDim, textAlign = TextAlign.Center)
        }
    }
}

/** Error / hint line with a fixed minimum height, so the layout does not jump. */
@Composable
private fun Message(text: String?, wait: Boolean = false) {
    Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = 6.dp, bottom = 8.dp), contentAlignment = Alignment.CenterStart) {
        if (text != null) Row(verticalAlignment = Alignment.CenterVertically) {
            val c = if (wait) Color(0xFFFFB547) else MaterialTheme.colorScheme.error
            Icon(if (wait) Icons.Outlined.Timer else Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = c)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = c)
        }
    }
}

private suspend fun Animatable<Float, *>.shake() {
    for (x in listOf(22f, -18f, 14f, -9f, 5f, 0f)) animateTo(x, tween(45))
}

@Composable
private fun UnlockForm(wide: Boolean, bioEnabled: Boolean, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val lib = LocalApp.current.library
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val shake = remember { Animatable(0f) }
    val unlocked by rememberUpdatedState(onUnlocked)
    var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockedUntil by remember { mutableLongStateOf(lib.value.security.lockedUntil) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var forgot by remember { mutableStateOf(false) }
    val waitSec = ((lockedUntil - now + 999) / 1000).toInt().coerceAtLeast(0)

    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) { now = System.currentTimeMillis(); delay(250) }
        now = System.currentTimeMillis()
    }
    fun focusField() = runCatching { focus.requestFocus() }
    fun prompt() = showBiometricPrompt(ctx, "Ввести пароль", onSuccess = { unlocked() }, onCancel = { focusField() })
    LaunchedEffect(Unit) { if (!(bioEnabled && prompt())) focusField() }
    LaunchedEffect(waitSec == 0) { if (waitSec == 0 && lockedUntil > 0) focusField() }

    fun submit() {
        if (busy || waitSec > 0) return
        if (pw.isEmpty()) { error = "Введите пароль"; scope.launch { shake.shake() }; return }
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.Default) { lib.checkPassword(pw) }
            busy = false
            pw = ""
            when (r) {
                Library.Check.Ok -> { keyboard?.hide(); unlocked() }
                Library.Check.Wrong -> {
                    val left = 5 - lib.value.security.failCount
                    error = if (left in 1..3) "Неверный пароль. Осталось попыток: $left" else "Неверный пароль"
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    shake.shake()
                }
                is Library.Check.Wait -> {
                    error = null
                    now = System.currentTimeMillis()
                    lockedUntil = lib.value.security.lockedUntil.takeIf { it > now } ?: (now + r.seconds * 1000L)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    shake.shake()
                }
            }
        }
    }

    LockLayout(wide, "Введите пароль", "Чтобы открыть SFM") {
        Column(Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value * density }) {
            PasswordField(
                value = pw, onChange = { pw = it; error = null }, label = "Пароль",
                visible = show, onToggleVisible = { show = !show },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                enabled = waitSec == 0, isError = error != null, onIme = ::submit,
            )
            Message(if (waitSec > 0) "Слишком много попыток, подождите $waitSec с" else error, wait = waitSec > 0)
            BrandButton("Войти", ::submit, Modifier.fillMaxWidth(), enabled = waitSec == 0, loading = busy)
            if (bioEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { prompt() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = FieldShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Sfm.Outline),
                ) {
                    Icon(Icons.Outlined.Fingerprint, null, Modifier.size(22.dp), tint = Sfm.Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("Войти по отпечатку или лицу", color = Sfm.Text)
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { forgot = true }, Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                Text("Забыли пароль?", color = Sfm.TextDim)
            }
        }
    }

    if (forgot) AlertDialog(
        onDismissRequest = { forgot = false },
        title = { Text("Забыли пароль?") },
        text = {
            Text(
                "Пароль хранится только на этом телефоне и не восстанавливается. Начать заново можно, очистив данные приложения: " +
                    "Настройки Android → Приложения → SFM → Хранилище → Очистить данные. Избранное и история при этом удалятся, скачанные файлы останутся в Download/SFM.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                forgot = false
                runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text("Открыть настройки") }
        },
        dismissButton = { TextButton(onClick = { forgot = false }) { Text("Понятно") } },
    )
}

@Composable
private fun SetupForm(wide: Boolean, bioAvailable: Boolean, onUnlocked: () -> Unit) {
    val lib = LocalApp.current.library
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val focus2 = remember { FocusRequester() }
    val shake = remember { Animatable(0f) }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var tooShort by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    var bio by remember { mutableStateOf(bioAvailable) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() {
        if (busy) return
        tooShort = pw.length < 4
        mismatch = !tooShort && pw != pw2
        if (tooShort || mismatch) { scope.launch { shake.shake() }; return }
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                lib.setPassword(pw)
                if (bioAvailable) lib.settings { it.copy(biometric = bio) }
            }
            keyboard?.hide()
            onUnlocked()
        }
    }

    LockLayout(wide, "Придумайте пароль", "Без него приложение не откроется") {
        Column(Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value * density }) {
            PasswordField(
                value = pw, onChange = { pw = it; tooShort = false; mismatch = false }, label = "Пароль",
                visible = show, onToggleVisible = { show = !show },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                isError = tooShort, supporting = "Минимум 4 символа",
                imeAction = ImeAction.Next, onIme = { runCatching { focus2.requestFocus() } },
            )
            Spacer(Modifier.height(4.dp))
            PasswordField(
                value = pw2, onChange = { pw2 = it; mismatch = false }, label = "Повторите пароль",
                visible = show, onToggleVisible = { show = !show },
                modifier = Modifier.fillMaxWidth().focusRequester(focus2),
                isError = mismatch, onIme = ::submit,
            )
            Message(if (mismatch) "Пароли не совпадают" else null)
            if (bioAvailable) {
                Row(
                    Modifier.fillMaxWidth().clip(FieldShape).background(Sfm.Surface)
                        .toggleable(bio, role = Role.Switch) { bio = it }
                        .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Sfm.Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Fingerprint, null, Modifier.size(22.dp), tint = Sfm.Accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Входить по отпечатку или лицу", style = MaterialTheme.typography.bodyLarge, color = Sfm.Text)
                        Text("Пароль останется запасным способом", style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = bio, onCheckedChange = null, colors = sfmSwitchColors())
                }
                Spacer(Modifier.height(16.dp))
            }
            BrandButton("Сохранить и войти", ::submit, Modifier.fillMaxWidth(), loading = busy)
            Spacer(Modifier.height(14.dp))
            Text(
                "Пароль хранится только на этом телефоне. Забытый пароль сбрасывается лишь очисткой данных приложения.",
                style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
fun sfmSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = Sfm.Accent, checkedBorderColor = Sfm.Accent,
    uncheckedThumbColor = Sfm.TextDim, uncheckedTrackColor = Sfm.Surface3, uncheckedBorderColor = Sfm.Outline,
)
