package com.focusgoal.app.ui.character

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.focusgoal.app.ui.theme.Palette
import com.focusgoal.app.ui.theme.glass
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class MiraMood { HAPPY, FOCUSED, STERN, CELEBRATE }

/**
 * Mira, FocusGoal's cartoon focus buddy, drawn entirely in code so she scales crisply and can
 * blink, bob and "talk". Give her a square-ish size, e.g. Modifier.size(160.dp).
 */
@Composable
fun Mira(modifier: Modifier = Modifier, mood: MiraMood = MiraMood.HAPPY, talking: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "mira")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val mouth by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(170, easing = LinearEasing), RepeatMode.Reverse),
        label = "mouth",
    )
    var blink by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2200, 5200))
            blink = true
            delay(130)
            blink = false
        }
    }
    Canvas(modifier) { drawMira(mood, blink, if (talking) mouth else 0f, bob) }
}

/** Glass speech bubble that cross-fades when Mira says something new. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.glass(
            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp),
            fill = Palette.GlassStrong,
        ),
    ) {
        Crossfade(targetState = text, label = "bubble") { line ->
            Text(
                line,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
            )
        }
    }
}

private val Hair = Color(0xFF2D2150)
private val HairShine = Color(0xFF5A4796)
private val Skin = Color(0xFFFFE4D6)
private val SkinShade = Color(0xFFF4C4AE)
private val Blush = Color(0xFFFF8FB1)
private val EyeDark = Color(0xFF241A42)
private val MouthColor = Color(0xFF9A3D63)

private fun DrawScope.drawMira(mood: MiraMood, blink: Boolean, mouthOpen: Float, bob: Float) {
    val w = size.minDimension
    val ox = (size.width - w) / 2f
    val oy = (size.height - w) / 2f + (bob - 0.5f) * 0.03f * w
    fun p(x: Float, y: Float) = Offset(ox + x * w, oy + y * w)
    fun sz(x: Float, y: Float) = Size(x * w, y * w)

    clipRect {
        // ---- Back hair ----
        drawPath(
            Path().apply {
                moveTo(p(0.5f, 0.12f))
                cubicTo(p(0.16f, 0.12f), p(0.11f, 0.45f), p(0.14f, 0.62f))
                lineTo(p(0.13f, 0.86f))
                quadraticTo(p(0.22f, 0.92f), p(0.31f, 0.84f))
                lineTo(p(0.69f, 0.84f))
                quadraticTo(p(0.78f, 0.92f), p(0.87f, 0.86f))
                lineTo(p(0.86f, 0.62f))
                cubicTo(p(0.89f, 0.45f), p(0.84f, 0.12f), p(0.5f, 0.12f))
                close()
            },
            Hair,
        )

        // ---- Neck + hoodie ----
        drawRect(SkinShade, topLeft = p(0.45f, 0.68f), size = sz(0.1f, 0.14f))
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Palette.AccentLight, Palette.Accent, Palette.AccentDeep),
                startY = p(0f, 0.76f).y,
                endY = p(0f, 1.1f).y,
            ),
            topLeft = p(0.22f, 0.77f),
            size = sz(0.56f, 0.4f),
            cornerRadius = CornerRadius(0.17f * w),
        )
        // collar + strings
        drawPath(
            Path().apply {
                moveTo(p(0.42f, 0.77f)); quadraticTo(p(0.5f, 0.86f), p(0.58f, 0.77f)); close()
            },
            SkinShade,
        )
        drawLine(Color.White.copy(alpha = 0.85f), p(0.45f, 0.82f), p(0.44f, 0.92f), strokeWidth = 0.012f * w, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.85f), p(0.55f, 0.82f), p(0.56f, 0.92f), strokeWidth = 0.012f * w, cap = StrokeCap.Round)

        // ---- Ears + face ----
        drawOval(Skin, topLeft = p(0.205f, 0.47f), size = sz(0.07f, 0.1f))
        drawOval(Skin, topLeft = p(0.725f, 0.47f), size = sz(0.07f, 0.1f))
        drawOval(Skin, topLeft = p(0.24f, 0.25f), size = sz(0.52f, 0.5f))

        // ---- Bangs + side locks ----
        drawPath(
            Path().apply {
                moveTo(p(0.22f, 0.5f))
                cubicTo(p(0.18f, 0.2f), p(0.4f, 0.11f), p(0.52f, 0.12f))
                cubicTo(p(0.72f, 0.12f), p(0.85f, 0.26f), p(0.79f, 0.5f))
                quadraticTo(p(0.75f, 0.37f), p(0.67f, 0.34f))
                quadraticTo(p(0.65f, 0.4f), p(0.6f, 0.42f))
                quadraticTo(p(0.58f, 0.34f), p(0.5f, 0.32f))
                quadraticTo(p(0.46f, 0.39f), p(0.41f, 0.41f))
                quadraticTo(p(0.4f, 0.33f), p(0.33f, 0.34f))
                quadraticTo(p(0.26f, 0.38f), p(0.22f, 0.5f))
                close()
            },
            Hair,
        )
        drawPath(
            Path().apply {
                moveTo(p(0.235f, 0.38f)); quadraticTo(p(0.19f, 0.56f), p(0.22f, 0.72f))
                quadraticTo(p(0.27f, 0.62f), p(0.285f, 0.44f)); close()
            },
            Hair,
        )
        drawPath(
            Path().apply {
                moveTo(p(0.765f, 0.38f)); quadraticTo(p(0.81f, 0.56f), p(0.78f, 0.72f))
                quadraticTo(p(0.73f, 0.62f), p(0.715f, 0.44f)); close()
            },
            Hair,
        )
        drawArc(
            HairShine, startAngle = 200f, sweepAngle = 70f, useCenter = false,
            topLeft = p(0.3f, 0.17f), size = sz(0.3f, 0.2f),
            style = Stroke(width = 0.022f * w, cap = StrokeCap.Round),
        )

        // ---- Accessory: headphones while focusing, a hair clip otherwise ----
        if (mood == MiraMood.FOCUSED) {
            drawArc(
                Palette.AccentLight, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = p(0.17f, 0.08f), size = sz(0.66f, 0.72f),
                style = Stroke(width = 0.04f * w, cap = StrokeCap.Round),
            )
            drawRoundRect(Palette.Accent, topLeft = p(0.14f, 0.41f), size = sz(0.1f, 0.18f), cornerRadius = CornerRadius(0.05f * w))
            drawRoundRect(Palette.Accent, topLeft = p(0.76f, 0.41f), size = sz(0.1f, 0.18f), cornerRadius = CornerRadius(0.05f * w))
        } else {
            drawCircle(Palette.AccentLight, radius = 0.038f * w, center = p(0.69f, 0.25f))
            drawCircle(Color.White, radius = 0.014f * w, center = p(0.69f, 0.25f))
        }

        // ---- Eyes ----
        val eyeL = p(0.39f, 0.54f)
        val eyeR = p(0.61f, 0.54f)
        val closedHappy = mood == MiraMood.HAPPY && blink || mood == MiraMood.CELEBRATE
        for ((i, c) in listOf(eyeL, eyeR).withIndex()) {
            when {
                closedHappy -> drawArc(
                    EyeDark, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = c - Offset(0.042f * w, 0.02f * w), size = sz(0.084f, 0.07f),
                    style = Stroke(width = 0.016f * w, cap = StrokeCap.Round),
                )
                blink -> drawArc(
                    EyeDark, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                    topLeft = c - Offset(0.04f * w, 0.045f * w), size = sz(0.08f, 0.05f),
                    style = Stroke(width = 0.014f * w, cap = StrokeCap.Round),
                )
                else -> {
                    val h = if (mood == MiraMood.STERN) 0.09f else 0.12f
                    drawOval(EyeDark, topLeft = c - Offset(0.045f * w, h / 2 * w), size = sz(0.09f, h))
                    drawOval(
                        Palette.Accent.copy(alpha = 0.85f),
                        topLeft = c + Offset(-0.033f * w, 0f), size = sz(0.066f, h / 2 - 0.008f),
                    )
                    drawCircle(Color.White, radius = 0.017f * w, center = c + Offset(-0.014f * w, -0.024f * w))
                    drawCircle(Color.White.copy(alpha = 0.8f), radius = 0.008f * w, center = c + Offset(0.016f * w, 0.02f * w))
                    // little lash flick on the outer corner
                    val dir = if (i == 0) -1f else 1f
                    drawLine(
                        EyeDark, c + Offset(dir * 0.04f * w, -0.035f * w), c + Offset(dir * 0.062f * w, -0.05f * w),
                        strokeWidth = 0.012f * w, cap = StrokeCap.Round,
                    )
                }
            }
        }

        // ---- Brows ----
        val browStroke = 0.012f * w
        when (mood) {
            MiraMood.STERN -> {
                drawLine(Hair, p(0.335f, 0.435f), p(0.43f, 0.465f), strokeWidth = browStroke, cap = StrokeCap.Round)
                drawLine(Hair, p(0.665f, 0.435f), p(0.57f, 0.465f), strokeWidth = browStroke, cap = StrokeCap.Round)
            }
            MiraMood.FOCUSED -> {
                drawLine(Hair, p(0.345f, 0.455f), p(0.43f, 0.45f), strokeWidth = browStroke, cap = StrokeCap.Round)
                drawLine(Hair, p(0.655f, 0.455f), p(0.57f, 0.45f), strokeWidth = browStroke, cap = StrokeCap.Round)
            }
            else -> Unit // hidden under the bangs
        }

        // ---- Blush + nose ----
        drawOval(Blush.copy(alpha = 0.45f), topLeft = p(0.29f, 0.6f), size = sz(0.09f, 0.045f))
        drawOval(Blush.copy(alpha = 0.45f), topLeft = p(0.62f, 0.6f), size = sz(0.09f, 0.045f))
        drawCircle(SkinShade, radius = 0.008f * w, center = p(0.5f, 0.6f))

        // ---- Mouth ----
        val m = p(0.5f, 0.655f)
        when {
            mouthOpen > 0f -> {
                drawOval(MouthColor, topLeft = m - Offset(0.03f * w, 0.012f * w), size = sz(0.06f, 0.02f + 0.04f * mouthOpen))
            }
            mood == MiraMood.CELEBRATE -> drawArc(
                MouthColor, startAngle = 0f, sweepAngle = 180f, useCenter = true,
                topLeft = m - Offset(0.045f * w, 0.035f * w), size = sz(0.09f, 0.07f),
            )
            mood == MiraMood.STERN -> drawArc(
                MouthColor, startAngle = 205f, sweepAngle = 130f, useCenter = false,
                topLeft = m - Offset(0.03f * w, 0f), size = sz(0.06f, 0.04f),
                style = Stroke(width = 0.012f * w, cap = StrokeCap.Round),
            )
            mood == MiraMood.FOCUSED -> drawArc(
                MouthColor, startAngle = 30f, sweepAngle = 120f, useCenter = false,
                topLeft = m - Offset(0.025f * w, 0.025f * w), size = sz(0.05f, 0.03f),
                style = Stroke(width = 0.012f * w, cap = StrokeCap.Round),
            )
            else -> drawArc(
                MouthColor, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = m - Offset(0.04f * w, 0.035f * w), size = sz(0.08f, 0.05f),
                style = Stroke(width = 0.013f * w, cap = StrokeCap.Round),
            )
        }
    }
}

private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineTo(o: Offset) = lineTo(o.x, o.y)
private fun Path.quadraticTo(c: Offset, e: Offset) = quadraticTo(c.x, c.y, e.x, e.y)
private fun Path.cubicTo(c1: Offset, c2: Offset, e: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
