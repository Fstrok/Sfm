package com.sfm.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sfm.app.ui.lock.sfmSwitchColors
import com.sfm.app.ui.theme.Sfm

internal object Tint {
    val Security = Sfm.Accent
    val Playback = Color(0xFF2FB8FF)
    val Downloads = Sfm.Accent2
    val View = Color(0xFFFFB547)
    val Subs = Sfm.Hot
    val Content = Color(0xFFFF8A3D)
    val Data = Color(0xFF8E9BFF)
}

@Composable
internal fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад", tint = Sfm.Text) }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Sfm.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Section title + rounded card; rows inside are separated by 1dp hairlines. */
@Composable
internal fun Section(title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(title, Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = Sfm.TextDim)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Sfm.Outline.copy(alpha = 0.55f)),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            content = content,
        )
        if (note != null) Text(note, Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim)
    }
}

@Composable
private fun RowShell(icon: ImageVector, tint: Color, modifier: Modifier, trailing: @Composable () -> Unit, text: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().background(Sfm.Surface).heightIn(min = 64.dp).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), content = text)
        trailing()
    }
}

@Composable
private fun Titles(title: String, subtitle: String?, titleColor: Color = Sfm.Text) {
    Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Sfm.TextDim)
}

/** Clickable row with an optional value on the right (value pickers, actions, navigation). */
@Composable
internal fun SettingRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    titleColor: Color = Sfm.Text,
    onClick: (() -> Unit)? = null,
) {
    RowShell(
        icon, tint,
        if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
        trailing = {
            if (value != null) Text(
                value, Modifier.padding(start = 12.dp).widthIn(max = 150.dp), style = MaterialTheme.typography.labelLarge,
                color = if (onClick != null) Sfm.Accent else Sfm.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.padding(start = 4.dp).size(22.dp), tint = Sfm.TextDim)
        },
    ) { Titles(title, subtitle, titleColor) }
}

/** The whole row toggles the switch. */
@Composable
internal fun SwitchRow(icon: ImageVector, tint: Color, title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowShell(
        icon, tint, Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
        trailing = { Spacer(Modifier.width(12.dp)); Switch(checked = checked, onCheckedChange = null, colors = sfmSwitchColors()) },
    ) { Titles(title, subtitle) }
}

@Composable
internal fun ChoiceDialog(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .selectable(i == selected, role = Role.RadioButton) { onSelect(i); onDismiss() }
                            .heightIn(min = 52.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Sfm.Accent, unselectedColor = Sfm.TextDim))
                        Spacer(Modifier.width(14.dp))
                        Text(o, style = MaterialTheme.typography.bodyLarge, color = Sfm.Text)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = Sfm.Surface2,
    )
}

@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, color = Sfm.TextDim) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else Sfm.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Sfm.Text) } },
        containerColor = Sfm.Surface2,
    )
}
