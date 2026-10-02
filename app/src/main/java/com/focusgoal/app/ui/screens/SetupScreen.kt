package com.focusgoal.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.focus.Permissions
import com.focusgoal.app.ui.character.Mira
import com.focusgoal.app.ui.character.MiraMood
import com.focusgoal.app.ui.rememberResumeTick
import com.focusgoal.app.ui.theme.Palette
import com.focusgoal.app.ui.theme.PillButton
import com.focusgoal.app.ui.theme.glass

private fun Context.launch(intent: Intent, fallback: Intent = Permissions.appInfo(this)) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        try {
            startActivity(fallback)
        } catch (e2: ActivityNotFoundException) {
            startActivity(Permissions.appInfo(this))
        }
    }
}

/**
 * The "allow, allow, done" setup flow: each card opens the right system screen and flips to a
 * green tick once Android reports the permission as granted.
 */
@Composable
fun SetupScreen(firstRun: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { FocusRepository.get(context) }
    val settings by repo.settings.collectAsStateWithLifecycle()
    val resumeTick = rememberResumeTick()
    var localTick by remember { mutableIntStateOf(0) }
    val key = resumeTick + localTick

    val accessibility = remember(key) { Permissions.accessibilityEnabled(context) }
    val notifications = remember(key) { Permissions.notificationsAllowed(context) }
    val overlay = remember(key) { Permissions.overlayAllowed(context) }
    val admin = remember(key) { Permissions.deviceAdminActive(context) }
    val battery = remember(key) { Permissions.batteryUnrestricted(context) }
    val grantedCount = listOf(accessibility, notifications, overlay, admin, battery).count { it }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { localTick++ }
    var name by remember { mutableStateOf(settings.userName) }
    var showBlockerGuide by remember { mutableStateOf(false) }

    if (showBlockerGuide) {
        BlockerGuideDialog(
            onOpenSettings = {
                showBlockerGuide = false
                repo.returnAfterAccessibility = true
                context.launch(Permissions.accessibilitySettings(context), fallback = Permissions.accessibilityList())
            },
            onOpenAppInfo = {
                showBlockerGuide = false
                context.launch(Permissions.appInfo(context))
            },
            onDismiss = { showBlockerGuide = false },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Mira(Modifier.size(150.dp), mood = if (grantedCount == 5) MiraMood.CELEBRATE else MiraMood.HAPPY)
            Text(
                if (firstRun) "Hi, I'm Mira!" else "Permissions",
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Allow a few things so I can guard your focus. Tap Allow, switch it on, then come back here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextDim,
                textAlign = TextAlign.Center,
            )
        }

        if (firstRun) {
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (name.isEmpty()) Text("What should I call you?", color = Palette.TextFaint, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                        cursorBrush = SolidColor(Palette.AccentLight),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        PermissionCard(
            title = "App blocker",
            description = "Lets me see which app opens so I can block distractions. Tap Allow and I'll show you exactly what to do.",
            hint = "Greyed out? On Android 13+ open App info → ⋮ menu → \"Allow restricted settings\", then try again.",
            granted = accessibility,
            required = true,
            onAllow = { showBlockerGuide = true },
            secondaryLabel = "App info",
            onSecondary = { context.launch(Permissions.appInfo(context)) },
        )
        PermissionCard(
            title = "Notifications",
            description = "Shows your running timer and my messages.",
            granted = notifications,
            onAllow = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.launch(Permissions.notificationSettings(context))
                }
            },
        )
        PermissionCard(
            title = "Display over other apps",
            description = "Lets the block screen appear instantly on top of blocked apps.",
            granted = overlay,
            onAllow = { context.launch(Permissions.overlaySettings(context)) },
        )
        PermissionCard(
            title = "Uninstall protection",
            description = "For Deep Focus: FocusGoal can't be uninstalled while a Deep Focus session is running. You can turn this off any time outside a session.",
            granted = admin,
            onAllow = { context.launch(Permissions.deviceAdminRequest(context)) },
        )
        PermissionCard(
            title = "Run in background",
            description = "Stops the battery saver from killing the blocker mid-session.",
            granted = battery,
            onAllow = { context.launch(Permissions.batteryRequest(context)) },
        )

        Spacer(Modifier.height(4.dp))
        Text(
            "$grantedCount of 5 allowed",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.TextDim,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        PillButton(
            if (accessibility) (if (firstRun) "Let's focus ✨" else "Done") else "Continue without blocker",
            modifier = Modifier.fillMaxWidth(),
            filled = accessibility,
        ) {
            repo.updateSettings { it.copy(userName = if (firstRun) name.trim() else it.userName, onboardingDone = true) }
            onDone()
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onAllow: () -> Unit,
    required: Boolean = false,
    hint: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), fill = if (granted) Palette.Accent.copy(alpha = 0.14f) else Palette.Glass)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    if (required) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "REQUIRED",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.AccentLight,
                            modifier = Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF4ADE80).copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Allowed", tint = Color.White)
                }
            } else {
                Text(
                    "Allow",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Palette.AccentGradient)
                        .clickable(onClick = onAllow)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
        if (!granted && (hint != null || secondaryLabel != null)) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint, modifier = Modifier.weight(1f))
                }
                if (secondaryLabel != null && onSecondary != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        secondaryLabel,
                        modifier = Modifier.glass(RoundedCornerShape(50)).clickable(onClick = onSecondary)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.AccentLight,
                    )
                }
            }
        }
    }
}

/** Step-by-step help shown before opening Accessibility settings — that screen confuses everyone. */
@Composable
private fun BlockerGuideDialog(onOpenSettings: () -> Unit, onOpenAppInfo: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(onDismiss) {
        Mira(Modifier.size(90.dp), mood = MiraMood.HAPPY)
        Text("Turn on the app blocker", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GuideStep(1, "Tap \"FocusGoal app blocker\". If you don't see it, first tap \"Installed apps\" or \"Downloaded apps\".")
            GuideStep(2, "Turn the switch ON.")
            GuideStep(3, "Tap \"Allow\" on the popup.")
            GuideStep(4, "I'll bring you right back here ✓")
        }
        Spacer(Modifier.height(20.dp))
        PillButton("Open settings", Modifier.fillMaxWidth(), onClick = onOpenSettings)
        Spacer(Modifier.height(14.dp))
        Text(
            "Switch greyed out or says \"Restricted setting\"? Tap below, then ⋮ (top-right) → \"Allow restricted settings\", and come back.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextFaint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Open App info",
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onOpenAppInfo).padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Palette.AccentLight,
        )
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Palette.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.Text, modifier = Modifier.padding(top = 2.dp))
    }
}
