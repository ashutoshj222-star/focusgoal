package com.focusgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusgoal.app.ai.MiraChat
import com.focusgoal.app.ai.MiraLines
import com.focusgoal.app.data.FocusMode
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.data.FocusSession
import com.focusgoal.app.data.InstalledApps
import com.focusgoal.app.focus.FocusManager
import com.focusgoal.app.focus.Permissions
import com.focusgoal.app.ui.AppIconStack
import com.focusgoal.app.ui.TimerRing
import com.focusgoal.app.ui.character.Mira
import com.focusgoal.app.ui.character.MiraMood
import com.focusgoal.app.ui.character.SpeechBubble
import com.focusgoal.app.ui.formatClock
import com.focusgoal.app.ui.formatCountdown
import com.focusgoal.app.ui.rememberNow
import com.focusgoal.app.ui.rememberResumeTick
import com.focusgoal.app.ui.theme.GlassCard
import com.focusgoal.app.ui.theme.Palette
import com.focusgoal.app.ui.theme.PillButton
import com.focusgoal.app.ui.theme.SectionLabel
import com.focusgoal.app.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

private val DURATIONS = listOf(10, 15, 25, 45, 60, 90, 120)

@Composable
fun HomeScreen(onOpenApps: () -> Unit, onOpenChat: () -> Unit, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { FocusRepository.get(context) }
    val session by repo.session.collectAsStateWithLifecycle()
    val now by rememberNow()

    val current = session
    LaunchedEffect(current, now / 1000) {
        if (current != null && !current.isRunning(now)) FocusManager.finishIfExpired(context)
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (current != null && current.isRunning(now)) {
            ActiveSessionContent(current, now, onOpenChat)
        } else {
            IdleContent(onOpenApps, onOpenChat, onOpenSetup, now)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Idle: pick a duration and choose Focus or Deep Focus
// ---------------------------------------------------------------------------------------------

@Composable
private fun IdleContent(onOpenApps: () -> Unit, onOpenChat: () -> Unit, onOpenSetup: () -> Unit, now: Long) {
    val context = LocalContext.current
    val repo = remember { FocusRepository.get(context) }
    val chat = remember { MiraChat.get(context) }
    val settings by repo.settings.collectAsStateWithLifecycle()
    val stats by repo.stats.collectAsStateWithLifecycle()
    val blocked by repo.blockedApps.collectAsStateWithLifecycle()
    val messages by chat.messages.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    val blockerOn = remember(tick) { Permissions.accessibilityEnabled(context) }

    var minutes by remember { mutableStateOf(settings.lastDurationMinutes) }
    var showDeepDialog by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(settings.userName) { MiraLines.greeting(settings.userName, hour) }
    val lastMira = messages.lastOrNull { !it.fromUser }
    val bubble = notice ?: lastMira?.takeIf { now - it.time < 120_000 }?.text ?: greeting

    // ---- Header ----
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (settings.userName.isBlank()) "Hi there 👋" else "Hi, ${settings.userName} 👋",
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Text,
            )
            Text("Ready to focus?", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        }
        Text(
            "🔥 ${stats.streakDays} day${if (stats.streakDays == 1) "" else "s"}",
            modifier = Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Text,
        )
    }

    MiraHero(bubble = bubble, mood = MiraMood.HAPPY, onClick = onOpenChat)

    if (!blockerOn) {
        GlassCard(onClick = onOpenSetup) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Palette.Danger)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("App blocker is off", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    Text("Tap to finish setup so I can block apps.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                }
            }
        }
    }

    // ---- Timer picker ----
    GlassCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 22.dp, horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            RoundGlassButton("−") { minutes = (minutes - 5).coerceAtLeast(5) }
            TimerRing(progress = (minutes / 120f).coerceIn(0.04f, 1f), modifier = Modifier.padding(horizontal = 16.dp).size(190.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatCountdown(minutes * 60_000L), style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light), color = Palette.Text)
                    Text("minutes", style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
                }
            }
            RoundGlassButton("+") { minutes = (minutes + 5).coerceAtMost(240) }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DURATIONS.forEach { d ->
                val selected = d == minutes
                Text(
                    if (d < 60) "$d m" else if (d % 60 == 0) "${d / 60} h" else "${d / 60}h ${d % 60}m",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Palette.Accent.copy(alpha = 0.55f) else Palette.Glass)
                        .clickable { minutes = d }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else Palette.TextDim,
                )
            }
        }
    }

    // ---- Start buttons ----
    fun ready(lockAll: Boolean): Boolean {
        if (!blockerOn) {
            notice = "I need the app blocker turned on first! Tap the warning above 🙏"
            return false
        }
        if (blocked.isEmpty() && !lockAll) {
            notice = "Pick some apps to block first — open the Apps tab 📱"
            return false
        }
        return true
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton(
            "Focus",
            modifier = Modifier.weight(1f),
            filled = false,
            leading = { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White) },
        ) {
            if (ready(lockAll = false)) {
                notice = null
                FocusManager.start(context, FocusMode.FOCUS, minutes)
            }
        }
        PillButton(
            "Deep Focus",
            modifier = Modifier.weight(1f),
            leading = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White) },
        ) {
            if (ready(lockAll = settings.lockWholePhoneByDefault)) {
                notice = null
                showDeepDialog = true
            }
        }
    }
    Text(
        "Focus can be stopped any time. Deep Focus can't be stopped, and FocusGoal can't be uninstalled until it ends.",
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.TextFaint,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    BlockedAppsCard(blocked, onOpenApps)

    // ---- Stats ----
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("Today", "${stats.todayMinutes}m", Modifier.weight(1f))
        StatTile("Streak", "${stats.streakDays}d", Modifier.weight(1f))
        StatTile("Sessions", "${stats.sessionsCompleted}", Modifier.weight(1f))
    }

    if (showDeepDialog) {
        DeepFocusDialog(
            minutes = minutes,
            lockAllDefault = settings.lockWholePhoneByDefault,
            blockedCount = blocked.size,
            onDismiss = { showDeepDialog = false },
            onConfirm = { lockAll ->
                showDeepDialog = false
                if (lockAll || blocked.isNotEmpty()) {
                    FocusManager.start(context, FocusMode.DEEP, minutes, lockWholePhone = lockAll)
                } else {
                    notice = "Pick some apps to block first — open the Apps tab 📱"
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Running session
// ---------------------------------------------------------------------------------------------

@Composable
private fun ActiveSessionContent(session: FocusSession, now: Long, onOpenChat: () -> Unit) {
    val context = LocalContext.current
    val chat = remember { MiraChat.get(context) }
    val messages by chat.messages.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }
    val deep = session.mode == FocusMode.DEEP

    val remaining = session.remainingMs(now)
    val progress = remaining.toFloat() / session.durationMs.coerceAtLeast(1)
    val lastMira = messages.lastOrNull { !it.fromUser }
    val bubble = lastMira?.takeIf { now - it.time < 30_000 }?.text ?: MiraLines.duringSession((now / 30_000).toInt())

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Row(
            Modifier.glass(RoundedCornerShape(50), fill = if (deep) Palette.Accent.copy(alpha = 0.3f) else Palette.Glass)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (deep) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (deep) "DEEP FOCUS · LOCKED" else "FOCUS MODE",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TimerRing(progress = progress, modifier = Modifier.size(270.dp), stroke = 16.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatCountdown(remaining), style = MaterialTheme.typography.displayLarge, color = Palette.Text)
                Text("ends at ${formatClock(session.endAt)}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
        }
    }

    MiraHero(bubble = bubble, mood = MiraMood.FOCUSED, onClick = onOpenChat)

    val icons by produceState<List<ImageBitmap?>>(emptyList(), session.blocked) {
        value = withContext(Dispatchers.IO) { session.blocked.map { InstalledApps.icon(context, it) } }
    }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        session.lockWholePhone -> "Whole phone locked"
                        else -> "${session.blocked.size} apps blocked"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                )
                Text(
                    if (session.lockWholePhone) "Only calls, messages and clock work" else "They'll unlock when the timer ends",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim,
                )
            }
            AppIconStack(icons)
        }
    }

    if (deep) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Palette.AccentLight)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Deep Focus can't be stopped, and Settings and uninstalling are blocked until it ends. You've got this!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim,
                )
            }
        }
    } else {
        PillButton("Stop session", modifier = Modifier.fillMaxWidth(), filled = false) { confirmStop = true }
    }

    if (confirmStop) {
        ConfirmDialog(
            title = "Stop focusing?",
            message = "You still have ${formatCountdown(remaining)} left. Mira will be a little sad 🥺",
            confirmText = "Stop",
            dismissText = "Keep going",
            onConfirm = {
                confirmStop = false
                FocusManager.stop(context)
            },
            onDismiss = { confirmStop = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------------------------

@Composable
fun MiraHero(bubble: String, mood: MiraMood, onClick: () -> Unit) {
    var talking by remember { mutableStateOf(false) }
    LaunchedEffect(bubble) {
        talking = true
        delay(1600)
        talking = false
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mira(Modifier.size(128.dp), mood = mood, talking = talking)
        Spacer(Modifier.width(4.dp))
        SpeechBubble(bubble, Modifier.weight(1f))
    }
}

@Composable
private fun BlockedAppsCard(blocked: Set<String>, onOpenApps: () -> Unit) {
    val context = LocalContext.current
    val icons by produceState<List<ImageBitmap?>>(emptyList(), blocked) {
        value = withContext(Dispatchers.IO) { blocked.map { InstalledApps.icon(context, it) } }
    }
    GlassCard(onClick = onOpenApps) {
        SectionLabel("Blocked during focus")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (blocked.isEmpty()) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Palette.AccentLight)
                Spacer(Modifier.width(10.dp))
                Text("Choose apps to block", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.weight(1f))
            } else {
                AppIconStack(icons)
                Spacer(Modifier.width(12.dp))
                Text("${blocked.size} apps", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.weight(1f))
                Text("Edit", style = MaterialTheme.typography.labelLarge, color = Palette.AccentLight)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.glass(RoundedCornerShape(20.dp)).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
    }
}

@Composable
private fun RoundGlassButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).glass(CircleShape, fill = Palette.GlassStrong).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
    }
}
