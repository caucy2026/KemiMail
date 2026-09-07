package com.kemi.windows.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.thunderbird.core.ui.compose.theme2.k9mail.lightThemeColorScheme

// Desktop adapters extend the existing K-9 theme, keeping raw Material controls inside this module.
@Composable fun KemiTheme(content: @Composable () -> Unit) {
    val c = lightThemeColorScheme
    MaterialTheme(colorScheme = lightColorScheme(primary = c.primary, onPrimary = c.onPrimary,
        secondary = c.secondary, surface = c.surface, onSurface = c.onSurface,
        error = c.error, outline = c.outline, primaryContainer = c.primaryContainer), content = content)
}
@Composable fun Label(text: String, modifier: Modifier = Modifier, title: Boolean = false,
                      muted: Boolean = false, maxLines: Int = Int.MAX_VALUE, error: Boolean = false) {
    Text(text, modifier, fontSize = if (title) 20.sp else 14.sp,
        fontWeight = if (title) FontWeight.SemiBold else FontWeight.Normal,
        color = when { error -> MaterialTheme.colorScheme.error; muted -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface }, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
@Composable fun Action(text: String, enabled: Boolean = true, primary: Boolean = false,
                       modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (primary) Button(onClick, modifier, enabled) { Text(text) }
    else OutlinedButton(onClick, modifier, enabled) { Text(text) }
}
@Composable fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
                      password: Boolean = false, multiline: Boolean = false, enabled: Boolean = true) {
    OutlinedTextField(value,onChange,modifier,label = { Text(label) },singleLine = !multiline,
        enabled = enabled, visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None)
}
@Composable fun Panel(modifier: Modifier = Modifier, tinted: Boolean = false, content: @Composable () -> Unit) {
    Surface(modifier, color = if (tinted) Color(0xFFF3F3FA) else Color.White, content = content)
}
@Composable fun Choice(text: String, selected: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier,
                       supporting: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Label(text, maxLines = 2)
            supporting?.let { Label(it, muted = true, maxLines = 2) }
        }
    }
}
@Composable fun Divider() { HorizontalDivider(color = Color(0xFFE7E8EE)) }
@Composable fun Busy() { LinearProgressIndicator(Modifier.fillMaxWidth()) }
@Composable fun Confirm(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Label(title,title = true) }, text = { Label(text) },
        confirmButton = { Action("确认", primary = true, onClick = onConfirm) },
        dismissButton = { Action("取消", onClick = onDismiss) })
}
