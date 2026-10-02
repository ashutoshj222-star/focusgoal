package com.focusgoal.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusgoal.app.ai.AiProvider
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.focus.Permissions
import com.focusgoal.app.ui.rememberResumeTick
import com.focusgoal.app.ui.theme.GlassCard
import com.focusgoal.app.ui.theme.Palette
import com.focusgoal.app.ui.theme.SectionLabel
import com.focusgoal.app.ui.theme.glass

@Composable
fun SettingsScreen(onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { FocusRepository.get(context) }
    val settings by repo.settings.collectAsStateWithLifecycle()
    val stats by repo.stats.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    val blockerOn = remember(tick) { Permissions.accessibilityEnabled(context) }
    val adminOn = remember(tick) { Permissions.deviceAdminActive(context) }

    var name by remember { mutableStateOf(settings.userName) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(settings.aiModel) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)

        GlassCard {
            SectionLabel("You")
            Spacer(Modifier.height(10.dp))
            GlassField(
                value = name,
                placeholder = "Your name",
                onChange = {
                    name = it.take(30)
                    repo.updateSettings { s -> s.copy(userName = name.trim()) }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Total focus: ${stats.totalMinutes / 60}h ${stats.totalMinutes % 60}m · ${stats.sessionsCompleted} sessions",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextDim,
            )
        }

        GlassCard {
            SectionLabel("Mira's AI brain")
            Spacer(Modifier.height(6.dp))
            Text(
                "Paste an API key from Claude (console.anthropic.com), ChatGPT (platform.openai.com) or Gemini " +
                    "(aistudio.google.com) and Mira can chat about anything. Without one she uses built-in replies. " +
                    "The key stays on this phone; API usage is billed by that company.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextDim,
            )
            Spacer(Modifier.height(10.dp))
            GlassField(
                value = apiKey,
                placeholder = "sk-ant-…  /  sk-…  /  AIza…",
                password = !showKey,
                onChange = {
                    apiKey = it.trim()
                    repo.updateSettings { s -> s.copy(apiKey = apiKey) }
                },
            )
            val provider = AiProvider.detect(apiKey)
            if (apiKey.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    provider?.let { "✓ ${it.label} key detected" } ?: "Unknown key. It should start with sk-ant-, sk- or AIza",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (provider != null) Color(0xFF4ADE80) else Palette.Danger,
                )
            }
            if (provider != null) {
                Spacer(Modifier.height(10.dp))
                GlassField(
                    value = model,
                    placeholder = "Model (default: ${provider.defaultModel})",
                    onChange = {
                        model = it.trim()
                        repo.updateSettings { s -> s.copy(aiModel = model) }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (showKey) "Hide key" else "Show key",
                modifier = Modifier.clickable { showKey = !showKey },
                style = MaterialTheme.typography.labelLarge,
                color = Palette.AccentLight,
            )
        }

        GlassCard {
            SectionLabel("Deep Focus")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lock entire phone by default", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    Text("Only calls, messages and clock stay usable", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                }
                Switch(
                    checked = settings.lockWholePhoneByDefault,
                    onCheckedChange = { on -> repo.updateSettings { it.copy(lockWholePhoneByDefault = on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Color.White),
                )
            }
        }

        GlassCard(onClick = onOpenSetup) {
            SectionLabel("Protection")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Permissions & setup", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    Text(
                        "App blocker: ${if (blockerOn) "on ✓" else "off"} · Uninstall protection: ${if (adminOn) "on ✓" else "off"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextDim,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Palette.TextDim)
            }
        }

        Text(
            "FocusGoal 1.0 · Mira only sees what you type to her. The app blocker reads app names, never screen content.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextFaint,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun GlassField(value: String, placeholder: String, onChange: (String) -> Unit, password: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), fill = Palette.GlassStrong).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = Palette.TextFaint, style = MaterialTheme.typography.bodyLarge)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
            cursorBrush = SolidColor(Palette.AccentLight),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
