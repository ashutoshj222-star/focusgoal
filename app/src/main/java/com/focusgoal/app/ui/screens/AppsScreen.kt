package com.focusgoal.app.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusgoal.app.data.AppEntry
import com.focusgoal.app.data.FocusMode
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.data.InstalledApps
import com.focusgoal.app.ui.theme.Palette
import com.focusgoal.app.ui.theme.SectionLabel
import com.focusgoal.app.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val repo = remember { FocusRepository.get(context) }
    val blocked by repo.blockedApps.collectAsStateWithLifecycle()
    val session by repo.session.collectAsStateWithLifecycle()
    val deepActive = session?.let { it.mode == FocusMode.DEEP && it.isRunning() } == true
    var query by rememberSaveable { mutableStateOf("") }
    var featuresTab by rememberSaveable { mutableStateOf(false) }

    val apps by produceState<List<AppEntry>?>(null) {
        val loaded = withContext(Dispatchers.IO) { InstalledApps.load(context) }
        if (!repo.hasChosenApps) {
            // First visit: pre-select the usual suspects that are actually installed.
            val installed = loaded.map { it.packageName }.toSet()
            repo.setBlockedApps(InstalledApps.SUGGESTED intersect installed)
        }
        value = loaded
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("What to block", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(50)).padding(4.dp)) {
            SegmentButton("Whole apps", selected = !featuresTab, modifier = Modifier.weight(1f)) { featuresTab = false }
            SegmentButton("Shorts & Reels", selected = featuresTab, modifier = Modifier.weight(1f)) { featuresTab = true }
        }
        Spacer(Modifier.height(12.dp))

        if (featuresTab) {
            FeatureBlocksList(deepActive)
        } else {
            WholeAppsContent(apps, query, { query = it }, blocked, deepActive) { pkg, on -> repo.setAppBlocked(pkg, on) }
        }
    }
}

@Composable
private fun SegmentButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Accent.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Palette.TextDim)
    }
}

@Composable
private fun WholeAppsContent(
    apps: List<AppEntry>?,
    query: String,
    onQuery: (String) -> Unit,
    blocked: Set<String>,
    deepActive: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    Column {
        Text(
            "These apps are locked completely while a focus session runs.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextDim,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Palette.TextFaint)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search apps", color = Palette.TextFaint, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                    cursorBrush = SolidColor(Palette.AccentLight),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (deepActive) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), fill = Palette.Accent.copy(alpha = 0.18f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Palette.AccentLight, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Deep Focus is on: you can add apps, but can't unblock any until it ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim,
                )
            }
        }

        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Accent)
            }
        } else {
            AppList(list, query, blocked, deepActive, onToggle)
        }
    }
}

@Composable
private fun AppList(
    list: List<AppEntry>,
    query: String,
    blocked: Set<String>,
    deepActive: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    val filtered = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    val (on, off) = filtered.partition { it.packageName in blocked }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (on.isNotEmpty()) {
            item(key = "h_on") { SectionLabel("Blocked · ${on.size}", Modifier.padding(start = 4.dp, bottom = 2.dp)) }
            items(on, key = { "on_" + it.packageName }) { app ->
                AppRow(app, checked = true, enabled = !deepActive) { onToggle(app.packageName, it) }
            }
        }
        item(key = "h_off") { SectionLabel("All apps", Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp)) }
        items(off, key = { "off_" + it.packageName }) { app ->
            AppRow(app, checked = false, enabled = true) { onToggle(app.packageName, it) }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), fill = if (checked) Palette.Accent.copy(alpha = 0.16f) else Palette.Glass)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.GlassStrong))
        }
        Spacer(Modifier.width(14.dp))
        Text(app.label, style = MaterialTheme.typography.bodyLarge, color = Palette.Text, modifier = Modifier.weight(1f), maxLines = 1)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palette.Accent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                uncheckedThumbColor = Palette.TextDim,
                uncheckedBorderColor = Color.White.copy(alpha = 0.2f),
            ),
        )
    }
}
