package com.wolftower.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Палитра: зелёный и красный взяты из иконки приложения. */
object WT {
    val BgTop = Color(0xFF0F1D15)
    val Bg = Color(0xFF0A120E)
    val Surface = Color(0xFF13201A)
    val SurfaceHigh = Color(0xFF1B2C23)
    val Outline = Color(0xFF273C31)
    val Green = Color(0xFF3FB566)
    val GreenGlow = Color(0xFF6BE596)
    val GreenDeep = Color(0xFF2B7338)
    val OnGreen = Color(0xFF04210F)
    val Red = Color(0xFFD8261C)
    val Amber = Color(0xFFF2B84B)
    val Text = Color(0xFFEAF3EC)
    val TextDim = Color(0xFF93A89A)
}

@Composable
fun WolfTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = WT.Green,
        onPrimary = WT.OnGreen,
        secondary = WT.GreenDeep,
        onSecondary = Color.White,
        background = WT.Bg,
        onBackground = WT.Text,
        surface = WT.Surface,
        onSurface = WT.Text,
        surfaceVariant = WT.SurfaceHigh,
        onSurfaceVariant = WT.TextDim,
        outline = WT.Outline,
        error = WT.Red,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------------------------------------------------------------- значки (рисуются на Canvas)

@Composable
fun CopyGlyph(color: Color, iconSize: Dp = 20.dp, cover: Color = WT.SurfaceHigh) {
    Canvas(Modifier.size(iconSize)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = s * 0.09f)
        val r = CornerRadius(s * 0.14f, s * 0.14f)
        drawRoundRect(color, topLeft = Offset(s * 0.30f, s * 0.08f), size = Size(s * 0.56f, s * 0.60f), cornerRadius = r, style = stroke)
        drawRoundRect(cover, topLeft = Offset(s * 0.14f, s * 0.28f), size = Size(s * 0.56f, s * 0.64f), cornerRadius = r)
        drawRoundRect(color, topLeft = Offset(s * 0.14f, s * 0.28f), size = Size(s * 0.56f, s * 0.64f), cornerRadius = r, style = stroke)
    }
}

@Composable
fun CheckGlyph(color: Color, iconSize: Dp = 20.dp) {
    Canvas(Modifier.size(iconSize)) {
        val s = this.size.minDimension
        val w = s * 0.12f
        drawLine(color, Offset(s * 0.20f, s * 0.53f), Offset(s * 0.42f, s * 0.75f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(s * 0.42f, s * 0.75f), Offset(s * 0.81f, s * 0.28f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

@Composable
fun PowerGlyph(color: Color, iconSize: Dp = 56.dp) {
    Canvas(Modifier.size(iconSize)) {
        val s = this.size.minDimension
        val w = s * 0.09f
        drawArc(
            color,
            startAngle = -55f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(s * 0.16f, s * 0.20f),
            size = Size(s * 0.68f, s * 0.68f),
            style = Stroke(width = w, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(s * 0.5f, s * 0.08f), Offset(s * 0.5f, s * 0.48f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

@Composable
fun KeyGlyph(color: Color, iconSize: Dp = 22.dp) {
    Canvas(Modifier.size(iconSize)) {
        val s = this.size.minDimension
        val w = s * 0.10f
        drawCircle(color, radius = s * 0.21f, center = Offset(s * 0.33f, s * 0.35f), style = Stroke(width = w))
        drawLine(color, Offset(s * 0.48f, s * 0.50f), Offset(s * 0.88f, s * 0.90f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(s * 0.70f, s * 0.72f), Offset(s * 0.82f, s * 0.60f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

@Composable
fun ShareGlyph(color: Color, iconSize: Dp = 18.dp) {
    Canvas(Modifier.size(iconSize)) {
        val s = this.size.minDimension
        val a = Offset(s * 0.78f, s * 0.20f)
        val b = Offset(s * 0.22f, s * 0.50f)
        val c = Offset(s * 0.78f, s * 0.80f)
        val w = s * 0.07f
        drawLine(color, a, b, strokeWidth = w)
        drawLine(color, b, c, strokeWidth = w)
        drawCircle(color, radius = s * 0.13f, center = a)
        drawCircle(color, radius = s * 0.13f, center = b)
        drawCircle(color, radius = s * 0.13f, center = c)
    }
}

@Composable
fun ChevronGlyph(color: Color, iconSize: Dp = 20.dp, open: Boolean = false) {
    Canvas(Modifier.size(iconSize).rotate(if (open) 180f else 0f)) {
        val s = this.size.minDimension
        val w = s * 0.10f
        drawLine(color, Offset(s * 0.22f, s * 0.38f), Offset(s * 0.50f, s * 0.66f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(s * 0.50f, s * 0.66f), Offset(s * 0.78f, s * 0.38f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- компоненты

@Composable
fun WtCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    var m = modifier.fillMaxWidth().clip(shape).background(WT.Surface).border(1.dp, WT.Outline, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        content()
    }
}

/** Кнопка «копировать»: после нажатия значок меняется на галочку, подпись на «скопировано», через 2 с возвращается. */
@Composable
fun CopyPill(label: String, doneLabel: String, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    val bg by animateColorAsState(targetValue = if (copied) WT.Green else WT.Surface, label = "copyBg")
    val fg by animateColorAsState(targetValue = if (copied) WT.OnGreen else WT.Text, label = "copyFg")
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, if (copied) WT.Green else WT.Outline, RoundedCornerShape(14.dp))
            .clickable {
                onCopy()
                copied = true
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (copied) CheckGlyph(fg, 18.dp) else CopyGlyph(fg, 18.dp, cover = WT.Surface)
        Text(if (copied) doneLabel else label, color = fg, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun SharePill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(WT.Surface)
            .border(1.dp, WT.Outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShareGlyph(WT.Text, 18.dp)
        Text(label, color = WT.Text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun WtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
) {
    val shape = RoundedCornerShape(14.dp)
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = WT.Green,
                contentColor = WT.OnGreen,
                disabledContainerColor = WT.SurfaceHigh,
                disabledContentColor = WT.TextDim,
            ),
        ) { Text(text, fontWeight = FontWeight.SemiBold) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            border = BorderStroke(1.dp, WT.Outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = WT.Text, disabledContentColor = WT.TextDim),
        ) { Text(text) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = WT.Green,
            unfocusedBorderColor = WT.Outline,
            cursorColor = WT.Green,
            focusedLabelColor = WT.Green,
            unfocusedLabelColor = WT.TextDim,
            focusedTextColor = WT.Text,
            unfocusedTextColor = WT.Text,
        ),
    )
}

@Composable
fun WtSwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = WT.Text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(subtitle, color = WT.TextDim, fontSize = 12.sp)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

/** Большая кнопка включения: когда защита работает, вокруг расходятся зелёные волны. */
@Composable
fun PowerButton(running: Boolean, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseValue",
    )
    val top by animateColorAsState(targetValue = if (running) Color(0xFF5FD98A) else Color(0xFF24392E), label = "btnTop")
    val bottom by animateColorAsState(targetValue = if (running) WT.GreenDeep else Color(0xFF16251D), label = "btnBottom")
    val glyph by animateColorAsState(
        targetValue = if (running) Color.White else if (enabled) WT.Text else WT.TextDim,
        label = "btnGlyph",
    )
    Box(Modifier.size(236.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val base = this.size.minDimension * 0.28f
            val reach = this.size.minDimension * 0.20f
            if (running) {
                val p = pulse
                drawCircle(WT.Green.copy(alpha = 0.30f * (1f - p)), radius = base + reach * p, center = center)
                val q = (p + 0.5f) % 1f
                drawCircle(WT.Green.copy(alpha = 0.30f * (1f - q)), radius = base + reach * q, center = center)
                drawCircle(WT.Green.copy(alpha = 0.10f), radius = base + 20.dp.toPx(), center = center)
            }
            drawCircle(
                if (running) WT.Green.copy(alpha = 0.55f) else WT.Outline,
                radius = base + 12.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Box(
            Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .clickable(enabled = enabled && !busy, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(48.dp), color = glyph, strokeWidth = 3.dp)
            } else {
                PowerGlyph(glyph, 60.dp)
            }
        }
    }
}

/** Выбор стратегии: простая плашка (своя, чтобы не зависеть от версии Material). */
@Composable
fun WtChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (selected) WT.GreenDeep else WT.Surface
    val fg = if (selected) Color.White else if (enabled) WT.Text else WT.TextDim
    Box(
        Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) WT.Green else WT.Outline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(text, color = fg, fontSize = 13.sp) }
}
