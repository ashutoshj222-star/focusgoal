package com.focusgoal.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusgoal.app.R
import com.focusgoal.app.ai.MiraLines
import com.focusgoal.app.data.FocusMode
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.data.InstalledApps
import com.focusgoal.app.ui.theme.FocusTheme
import com.focusgoal.app.ui.theme.GlowBackground
import com.focusgoal.app.ui.theme.Palette
import com.focusgoal.app.ui.theme.PillButton

/** Covers a blocked app during a session: FocusGoal logo, the live timer and a "Stay focused" button. */
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
    val deep = session?.mode == FocusMode.DEEP
    val line = remember(packageName) { MiraLines.blocked(label, deep) }

    GlowBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- Our logo + name (the blocked app's content stays hidden underneath) ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.BgBottom),
                )
                Spacer(Modifier.width(10.dp))
                Text("FocusGoal", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            }

            Spacer(Modifier.weight(1f))

            Text(
                if (deep) "DEEP FOCUS · LOCKED 🔒" else "FOCUS MODE",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.AccentLight,
            )
            Spacer(Modifier.height(16.dp))

            if (session != null) {
                val remaining = session.remainingMs(now)
                TimerRing(
                    progress = remaining.toFloat() / session.durationMs.coerceAtLeast(1),
                    modifier = Modifier.size(260.dp),
                    stroke = 16.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatCountdown(remaining),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = if (remaining >= 3_600_000L) 44.sp else 56.sp),
                            color = Palette.Text,
                        )
                        Text("ends at ${formatClock(session.endAt)}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "$label is blocked right now",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, textAlign = TextAlign.Center)

            Spacer(Modifier.weight(1f))

            PillButton("Stay focused 💪", Modifier.fillMaxWidth(), onClick = onBackToWork)
            Spacer(Modifier.height(8.dp))
            Text(
                "Open FocusGoal",
                modifier = Modifier.clickable(onClick = onOpenApp).padding(10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.TextDim,
            )
        }
    }
}
