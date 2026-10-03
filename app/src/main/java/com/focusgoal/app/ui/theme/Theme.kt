package com.focusgoal.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One accent colour on a deep night background — everything else is white at different opacities. */
object Palette {
    val BgTop = Color(0xFF0E0B1F)
    val BgBottom = Color(0xFF1B1446)
    val Accent = Color(0xFF8B7CFF)
    val AccentLight = Color(0xFFB9AEFF)
    val AccentDeep = Color(0xFF5B45E0)
    val Text = Color.White
    val TextDim = Color.White.copy(alpha = 0.68f)
    val TextFaint = Color.White.copy(alpha = 0.42f)
    val Glass = Color.White.copy(alpha = 0.08f)
    val GlassStrong = Color.White.copy(alpha = 0.14f)
    val GlassBorder = Color.White.copy(alpha = 0.22f)
    val Danger = Color(0xFFFF7A9A)

    val AccentGradient = Brush.linearGradient(listOf(AccentLight, Accent, AccentDeep))
}

private val colorScheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    secondary = Palette.AccentLight,
    background = Palette.BgTop,
    surface = Color(0xFF1C1640),
    onSurface = Color.White,
    onBackground = Color.White,
    surfaceVariant = Color(0xFF2A2258),
    onSurfaceVariant = Palette.TextDim,
    error = Palette.Danger,
)

private val typography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}

/** Night gradient with soft glowing colour blobs — what the glass panels sit on. */
@Composable
fun GlowBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.BgTop, Palette.BgBottom)))
            .drawBehind {
                fun blob(color: Color, center: Offset, radius: Float) = drawCircle(
                    brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
                    radius = radius,
                    center = center,
                )
                blob(Palette.Accent.copy(alpha = 0.45f), Offset(size.width * 0.1f, size.height * 0.08f), size.width * 0.8f)
                blob(Color(0xFFFF6FB5).copy(alpha = 0.18f), Offset(size.width * 1.0f, size.height * 0.38f), size.width * 0.7f)
                blob(Color(0xFF4F7BFF).copy(alpha = 0.22f), Offset(size.width * 0.15f, size.height * 0.95f), size.width * 0.9f)
            },
        content = content,
    )
}

/** Frosted-glass panel: translucent fill, light top-left highlight and a hairline border. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    fill: Color = Palette.Glass,
    borderWidth: Dp = 1.dp,
): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(fill.copy(alpha = (fill.alpha * 1.6f).coerceAtMost(1f)), fill.copy(alpha = fill.alpha * 0.6f)),
        ),
    )
    .border(
        borderWidth,
        Brush.linearGradient(listOf(Palette.GlassBorder, Color.White.copy(alpha = 0.04f))),
        shape,
    )

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .glass()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Big pill button. [filled] uses the accent gradient, otherwise it's glass. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val base = if (filled) {
        modifier
            .clip(shape)
            .background(if (enabled) Palette.AccentGradient else Brush.linearGradient(listOf(Palette.GlassStrong, Palette.Glass)))
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
    } else {
        modifier.glass(shape, fill = Palette.GlassStrong)
    }
    Row(
        base
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (leading != null) {
            leading()
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else Palette.TextFaint)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = Palette.TextFaint,
    )
}
