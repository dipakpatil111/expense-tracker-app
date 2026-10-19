package com.example.expensetracker

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat

/* ============================ design tokens ============================ */

@Immutable
data class AppColors(
    val bg: Color, val surface: Color, val surfaceAlt: Color, val border: Color,
    val text: Color, val textMuted: Color, val primary: Color, val onPrimary: Color,
    val income: Color, val expense: Color, val warning: Color, val info: Color, val isDark: Boolean
)

val LightColors = AppColors(
    bg = Color(0xFFF4F6FB), surface = Color.White, surfaceAlt = Color(0xFFEEF1F8), border = Color(0xFFE3E8F2),
    text = Color(0xFF0F172A), textMuted = Color(0xFF64748B), primary = Color(0xFF4F46E5), onPrimary = Color.White,
    income = Color(0xFF059669), expense = Color(0xFFDC2626), warning = Color(0xFFD97706), info = Color(0xFF0284C7), isDark = false
)

val DarkColors = AppColors(
    bg = Color(0xFF0B1020), surface = Color(0xFF141B2D), surfaceAlt = Color(0xFF1C2540), border = Color(0xFF263150),
    text = Color(0xFFF1F5F9), textMuted = Color(0xFF94A3B8), primary = Color(0xFF818CF8), onPrimary = Color(0xFF0B1020),
    income = Color(0xFF34D399), expense = Color(0xFFF87171), warning = Color(0xFFFBBF24), info = Color(0xFF38BDF8), isDark = true
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

/** shorthand for the current theme colours */
val Th: AppColors
    @Composable get() = LocalAppColors.current

@Composable
fun ExpenseTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "DARK" -> true; "LIGHT" -> false; else -> isSystemInDarkTheme() }
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(primary = c.primary, onPrimary = c.onPrimary, background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textMuted,
            outline = c.border, secondaryContainer = c.surfaceAlt, onSecondaryContainer = c.text)
    } else {
        lightColorScheme(primary = c.primary, onPrimary = c.onPrimary, background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textMuted,
            outline = c.border, secondaryContainer = c.surfaceAlt, onSecondaryContainer = c.text)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = c.bg.toArgb()
            window.navigationBarColor = c.surface.toArgb()
            val ctrl = WindowCompat.getInsetsController(window, view)
            ctrl.isAppearanceLightStatusBars = !dark
            ctrl.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/* ============================ category look ============================ */

fun categoryEmoji(cat: String): String = when (cat) {
    "Petrol" -> "⛽"; "Food" -> "🍔"; "Bills" -> "🧾"; "Rides" -> "🚕"; "Rapido Recharge" -> "⚡"; "Rapido Subscription" -> "🎟️"; "Maintenance" -> "🔧"
    "Shopping" -> "🛍️"; "Health" -> "💊"; "Loan" -> "🤝"; "Other" -> "📦"; else -> "🏷️"
}

private val palette = listOf(
    Color(0xFF6366F1), Color(0xFFF59E0B), Color(0xFF0EA5E9), Color(0xFF10B981), Color(0xFFA78BFA),
    Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFFEF4444)
)

fun categoryColor(cat: String): Color = when (cat) {
    "Petrol" -> Color(0xFFEF4444); "Food" -> Color(0xFFF59E0B); "Bills" -> Color(0xFF0EA5E9)
    "Rides" -> Color(0xFF10B981); "Rapido Recharge" -> Color(0xFFF97316); "Rapido Subscription" -> Color(0xFFEAB308); "Maintenance" -> Color(0xFFA78BFA); "Shopping" -> Color(0xFFEC4899)
    "Health" -> Color(0xFF14B8A6); "Loan" -> Color(0xFF6366F1); "Other" -> Color(0xFF64748B)
    else -> palette[(cat.hashCode() and 0x7fffffff) % palette.size]
}

/* ============================ building blocks ============================ */

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    color: Color = Th.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val base = modifier.fillMaxWidth().clip(shape).background(color).border(1.dp, Th.border, shape)
    Column(modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(padding), content = content)
}

@Composable
fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Th.border))
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Th.text)
        if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Th.textMuted)
    }
}

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Th.text)
        if (action != null && onAction != null) {
            Text(action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Th.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp))
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape).background(if (selected) Th.primary else Th.surfaceAlt)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Th.onPrimary else Th.textMuted)
    }
}

@Composable
fun SmallTag(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
fun CategoryAvatar(cat: String, size: Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(categoryColor(cat).copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) { Text(categoryEmoji(cat), fontSize = (size.value * 0.45f).sp) }
}

@Composable
fun ProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(Th.surfaceAlt)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun StatTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Th.surfaceAlt).padding(12.dp)) {
        Text(label, fontSize = 11.sp, color = Th.textMuted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
        if (sub != null) Text(sub, fontSize = 10.sp, color = Th.textMuted)
    }
}

@Composable
fun AppTextField(
    value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier.fillMaxWidth(), number: Boolean = false, singleLine: Boolean = true,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = singleLine, modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else if (password) KeyboardType.Password else KeyboardType.Text),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Th.primary, unfocusedBorderColor = Th.border,
            focusedTextColor = Th.text, unfocusedTextColor = Th.text,
            focusedLabelColor = Th.primary, unfocusedLabelColor = Th.textMuted, cursorColor = Th.primary
        )
    )
}

@Composable
fun PrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
    color: Color = Th.primary, contentColor: Color = Th.onPrimary, enabled: Boolean = true
) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(50.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun AppDialog(onDismiss: () -> Unit, title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp), color = Th.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 660.dp)
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Th.text)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Th.textMuted)
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    AppDialog(onDismiss = onDismiss, title = title) {
        Text(text, fontSize = 14.sp, color = Th.textMuted)
        Spacer(Modifier.height(18.dp))
        PrimaryButton(confirmLabel, { onConfirm() }, color = if (danger) Th.expense else Th.primary, contentColor = Color.White)
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = Th.textMuted) }
    }
}
