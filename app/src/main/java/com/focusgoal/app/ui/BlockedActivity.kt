package com.focusgoal.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusgoal.app.ai.MiraLines
import com.focusgoal.app.data.FocusMode
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.data.InstalledApps
import com.focusgoal.app.ui.character.Mira
import com.focusgoal.app.ui.character.MiraMood
import com.focusgoal.app.ui.character.SpeechBubble
import com.focusgoal.app.ui.theme.FocusTheme
import com.focusgoal.app.ui.theme.GlowBackground
import com.focusgoal.app.ui.theme.Palette
import com.focusgoal.app.ui.theme.PillButton
import kotlinx.coroutines.delay

/** Mira's "not now!" screen, shown when a blocked app is opened during a session. */
class BlockedActivity : ComponentActivity() {

    private var blockedPackage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE)
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            FocusTheme {
                BlockedScreen(
                    packageName = blockedPackage,
                    onBackToWork = ::goHome,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                    onSessionOver = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE)
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
    }
}

@Composable
private fun BlockedScreen(
    packageName: String?,
    onBackToWork: () -> Unit,
    onOpenApp: () -> Unit,
    onSessionOver: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { FocusRepository.get(context) }
    val now by rememberNow()
    val session = repo.activeSession(now)

    LaunchedEffect(session == null) { if (session == null) onSessionOver() }

    val label = remember(packageName) { packageName?.let { InstalledApps.label(context, it) } ?: "This app" }
    val icon = remember(packageName) { packageName?.let { InstalledApps.icon(context, it) } }
    val deep = session?.mode == FocusMode.DEEP
    val line = remember(packageName) { MiraLines.blocked(label, deep) }
    var talking by remember { mutableStateOf(true) }
    LaunchedEffect(line) {
        talking = true
        delay(1800)
        talking = false
    }

    GlowBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Mira(Modifier.size(200.dp), mood = MiraMood.STERN, talking = talking)
            SpeechBubble(line, Modifier.fillMaxWidth())
            Spacer(Modifier.height(28.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(10.dp))
                }
                Text("$label is blocked", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            }
            Spacer(Modifier.height(8.dp))
            if (session != null) {
                Text(
                    "Unlocks in ${formatCountdown(session.remainingMs(now))}",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize),
                    color = Palette.AccentLight,
                )
                Text(
                    if (deep) "Deep Focus is on — no shortcuts today 🔒" else "You can do this. Back to your task!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(32.dp))
            PillButton("Back to work 💪", Modifier.fillMaxWidth(), onClick = onBackToWork)
            Spacer(Modifier.height(12.dp))
            Text(
                "Open FocusGoal",
                modifier = Modifier.clickable(onClick = onOpenApp).padding(8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.TextDim,
            )
        }
    }
}
