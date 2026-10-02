package com.focusgoal.app.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.data.InstalledApps
import com.focusgoal.app.focus.FeatureRule
import com.focusgoal.app.focus.FeatureRules
import com.focusgoal.app.ui.theme.Palette
import com.focusgoal.app.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppFeatures(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap?,
    val installed: Boolean,
    val rules: List<FeatureRule>,
)

/** "Block Shorts and Reels only" — per-feature switches, grouped by app. */
@Composable
fun FeatureBlocksList(deepActive: Boolean) {
    val context = LocalContext.current
    val repo = remember { FocusRepository.get(context) }
    val enabled by repo.blockedFeatures.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()

    val groups by produceState(emptyList<AppFeatures>()) {
        value = withContext(Dispatchers.IO) {
            FeatureRules.ALL.groupBy { it.packageName }.map { (pkg, rules) ->
                val installed = try {
                    context.packageManager.getApplicationInfo(pkg, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
                AppFeatures(pkg, rules.first().appName, if (installed) InstalledApps.icon(context, pkg) else null, installed, rules)
            }.sortedByDescending { it.installed }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "intro") {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(14.dp)) {
                Text(
                    "Block only the endless feeds. Normal videos, chats and posts keep working. When one of these opens, I'll send you straight back.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Only during focus sessions", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            if (settings.featuresOnlyDuringFocus) "Blocked only while a timer runs" else "Blocked all the time",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextDim,
                        )
                    }
                    Switch(
                        checked = settings.featuresOnlyDuringFocus,
                        onCheckedChange = { on -> repo.updateSettings { it.copy(featuresOnlyDuringFocus = on) } },
                        enabled = !deepActive,
                        colors = switchColors(),
                    )
                }
            }
        }
        items(groups, key = { it.packageName }) { app ->
            AppFeatureCard(
                app = app,
                enabled = enabled,
                deepActive = deepActive,
                onToggle = { id, on -> repo.setFeatureBlocked(id, on) },
            )
        }
    }
}

@Composable
private fun AppFeatureCard(
    app: AppFeatures,
    enabled: Set<String>,
    deepActive: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), fill = if (app.rules.any { it.id in enabled }) Palette.Accent.copy(alpha = 0.14f) else Palette.Glass)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = app.icon
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Palette.GlassStrong))
            }
            Spacer(Modifier.width(12.dp))
            Text(app.appName, style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.weight(1f))
            if (!app.installed) {
                Text("Not installed", style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
            }
        }
        app.rules.forEach { rule ->
            val checked = rule.id in enabled
            val canChange = !(deepActive && checked)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = canChange) { onToggle(rule.id, !checked) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(rule.featureName, style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                    if (rule.experimental) {
                        Text("Beta: may miss some screens", style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
                    }
                }
                Switch(
                    checked = checked,
                    onCheckedChange = { onToggle(rule.id, it) },
                    enabled = canChange,
                    colors = switchColors(),
                )
            }
        }
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedTrackColor = Palette.Accent,
    checkedThumbColor = Color.White,
    uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
    uncheckedThumbColor = Palette.TextDim,
    uncheckedBorderColor = Color.White.copy(alpha = 0.2f),
)
