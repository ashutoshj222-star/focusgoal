package com.focusgoal.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusgoal.app.ui.theme.Palette
import com.focusgoal.app.ui.theme.glass
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Focus", Icons.Rounded.Home),
    APPS("Apps", Icons.Rounded.Lock),
    MIRA("Mira", Icons.Rounded.Face),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

@Composable
fun GlassNavBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp), fill = Palette.GlassStrong)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isSelected) Palette.Accent.copy(alpha = 0.28f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, contentDescription = tab.label, tint = if (isSelected) Color.White else Palette.TextFaint)
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else Palette.TextFaint,
                )
            }
        }
    }
}

/** Increments every time the screen resumes — use it as a key to re-check permissions. */
@Composable
fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

/** Current time, updated on every second boundary. */
@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(1000 - System.currentTimeMillis() % 1000)
        value = System.currentTimeMillis()
    }
}

fun formatCountdown(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatClock(time: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))

/** Circular progress ring with an accent sweep and a glowing head. */
@Composable
fun TimerRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2 + 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                Color.White.copy(alpha = 0.08f), 0f, 360f, false,
                topLeft = topLeft, size = arcSize, style = Stroke(sw),
            )
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                rotate(-90f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Palette.AccentDeep, Palette.Accent, Palette.AccentLight, Palette.AccentDeep),
                            center = center,
                        ),
                        startAngle = 0f, sweepAngle = sweep, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
                // glowing dot at the head of the arc
                val angle = Math.toRadians((sweep - 90f).toDouble())
                val r = arcSize.width / 2
                val head = Offset(center.x + (r * kotlin.math.cos(angle)).toFloat(), center.y + (r * kotlin.math.sin(angle)).toFloat())
                drawCircle(
                    Brush.radialGradient(listOf(Palette.AccentLight.copy(alpha = 0.7f), Color.Transparent), center = head, radius = sw * 1.6f),
                    radius = sw * 1.6f, center = head,
                )
                drawCircle(Color.White, radius = sw * 0.32f, center = head)
            }
        }
        content()
    }
}

/** Overlapping row of app icons, e.g. for "blocking 6 apps". */
@Composable
fun AppIconStack(icons: List<ImageBitmap?>, modifier: Modifier = Modifier, size: Dp = 30.dp, max: Int = 6) {
    Box(modifier) {
        icons.take(max).forEachIndexed { i, icon ->
            Box(
                Modifier
                    .offset(x = (size * 0.7f) * i)
                    .size(size)
                    .clip(CircleShape)
                    .background(Palette.BgBottom)
                    .border(1.5.dp, Palette.BgBottom, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(2.dp))
                } else {
                    Box(Modifier.fillMaxSize().background(Palette.GlassStrong))
                }
            }
        }
        // reserve the stack's width
        Box(Modifier.size(width = size + (size * 0.7f) * (icons.take(max).size - 1).coerceAtLeast(0), height = size))
    }
}
