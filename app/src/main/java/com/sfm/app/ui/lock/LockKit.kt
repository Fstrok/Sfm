package com.sfm.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sfm.app.ui.theme.Sfm

val FieldShape = RoundedCornerShape(16.dp)

@Composable
fun sfmFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Sfm.Surface, unfocusedContainerColor = Sfm.Surface, errorContainerColor = Sfm.Surface,
    disabledContainerColor = Sfm.Surface.copy(alpha = 0.5f),
    focusedBorderColor = Sfm.Accent, unfocusedBorderColor = Sfm.Outline, disabledBorderColor = Sfm.Outline.copy(alpha = 0.5f),
    focusedTextColor = Sfm.Text, unfocusedTextColor = Sfm.Text, disabledTextColor = Sfm.TextDim,
    focusedLabelColor = Sfm.Accent, unfocusedLabelColor = Sfm.TextDim, disabledLabelColor = Sfm.TextDim.copy(alpha = 0.6f),
    focusedLeadingIconColor = Sfm.Accent, unfocusedLeadingIconColor = Sfm.TextDim, disabledLeadingIconColor = Sfm.TextDim.copy(alpha = 0.5f),
    focusedTrailingIconColor = Sfm.Text, unfocusedTrailingIconColor = Sfm.TextDim, disabledTrailingIconColor = Sfm.TextDim.copy(alpha = 0.5f),
    focusedPlaceholderColor = Sfm.TextDim, unfocusedPlaceholderColor = Sfm.TextDim,
    focusedSupportingTextColor = Sfm.TextDim, unfocusedSupportingTextColor = Sfm.TextDim,
    cursorColor = Sfm.Accent,
)

@Composable
fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supporting: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onIme: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(64)) },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
        trailingIcon = {
            IconButton(onClick = onToggleVisible, enabled = enabled) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Скрыть пароль" else "Показать пароль")
            }
        },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        shape = FieldShape,
        colors = sfmFieldColors(),
    )
}

/** Main call to action: brand gradient, 54dp tall. */
@Composable
fun BrandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val fg = if (enabled) Color(0xFF03140F) else Sfm.TextDim
    Box(
        modifier.height(54.dp).clip(FieldShape)
            .background(if (enabled) Sfm.brand else SolidColor(Sfm.Surface3))
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = fg, strokeWidth = 2.5.dp)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, Modifier.size(20.dp), tint = fg); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}

/** Dark backdrop with two soft brand glows. */
fun Modifier.brandGlow(strength: Float = 1f): Modifier = drawBehind {
    drawRect(Sfm.Bg)
    val r1 = size.maxDimension * 0.75f
    drawCircle(Brush.radialGradient(listOf(Sfm.Accent.copy(alpha = 0.20f * strength), Color.Transparent), Offset(size.width * 0.1f, size.height * 0.06f), r1), r1, Offset(size.width * 0.1f, size.height * 0.06f))
    val r2 = size.maxDimension * 0.8f
    drawCircle(Brush.radialGradient(listOf(Sfm.Accent2.copy(alpha = 0.22f * strength), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.95f), r2), r2, Offset(size.width * 0.95f, size.height * 0.95f))
}
