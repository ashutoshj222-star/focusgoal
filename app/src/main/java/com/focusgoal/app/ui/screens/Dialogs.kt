package com.focusgoal.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.focusgoal.app.focus.Permissions
import com.focusgoal.app.ui.character.Mira
import com.focusgoal.app.ui.character.MiraMood
import com.focusgoal.app.ui.rememberResumeTick
import com.focusgoal.app.ui.theme.Palette
import com.focusgoal.app.ui.theme.PillButton
import com.focusgoal.app.ui.theme.glass

private val DialogShape = RoundedCornerShape(30.dp)

@Composable
internal fun GlassDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .glass(DialogShape, fill = Color(0xFF2A2160).copy(alpha = 0.92f))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(onDismiss) {
        Mira(Modifier.size(96.dp), mood = MiraMood.STERN)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(confirmText, Modifier.weight(1f), filled = false, onClick = onConfirm)
            PillButton(dismissText, Modifier.weight(1f), onClick = onDismiss)
        }
    }
}

@Composable
fun DeepFocusDialog(
    minutes: Int,
    lockAllDefault: Boolean,
    blockedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (lockWholePhone: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val adminOn = remember(tick) { Permissions.deviceAdminActive(context) }
    var lockAll by remember { mutableStateOf(lockAllDefault) }

    GlassDialog(onDismiss) {
        Mira(Modifier.size(100.dp), mood = MiraMood.FOCUSED)
        Spacer(Modifier.height(6.dp))
        Text("Start Deep Focus?", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Bullet("The timer can't be stopped for $minutes minutes")
            Bullet(if (lockAll) "Every app is locked except calls, messages and clock" else "Your $blockedCount blocked apps stay locked")
            Bullet("Settings and uninstalling are blocked")
        }
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).clickable { lockAll = !lockAll }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Lock entire phone", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Text("Only calls, messages & clock", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
            Switch(
                checked = lockAll,
                onCheckedChange = { lockAll = it },
                colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Color.White),
            )
        }

        if (!adminOn) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), fill = Palette.Danger.copy(alpha = 0.12f))
                    .clickable { runCatching { context.startActivity(Permissions.deviceAdminRequest(context)) } }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Uninstall protection is off", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    Text("Tap to turn it on so you can't uninstall mid-session", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                }
                Spacer(Modifier.width(8.dp))
                Text("Turn on", style = MaterialTheme.typography.labelLarge, color = Palette.AccentLight)
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Not now", Modifier.weight(1f), filled = false, onClick = onDismiss)
            PillButton("Lock in 🔒", Modifier.weight(1f)) { onConfirm(lockAll) }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•  ", color = Palette.AccentLight, style = MaterialTheme.typography.bodyMedium)
        Text(text, color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium)
    }
}
