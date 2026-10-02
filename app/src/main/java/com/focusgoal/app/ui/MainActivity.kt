package com.focusgoal.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.focus.FocusManager
import com.focusgoal.app.ui.screens.AppsScreen
import com.focusgoal.app.ui.screens.ChatScreen
import com.focusgoal.app.ui.screens.HomeScreen
import com.focusgoal.app.ui.screens.SettingsScreen
import com.focusgoal.app.ui.screens.SetupScreen
import com.focusgoal.app.ui.theme.FocusTheme
import com.focusgoal.app.ui.theme.GlowBackground
import com.focusgoal.app.widget.FocusWidgetProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { FocusTheme { FocusGoalApp() } }
    }

    override fun onResume() {
        super.onResume()
        FocusManager.finishIfExpired(this)
        FocusRepository.get(this).refreshStats()
        FocusWidgetProvider.updateAll(this)
    }
}

@Composable
private fun FocusGoalApp() {
    val context = LocalContext.current
    val repo = remember { FocusRepository.get(context) }
    val settings by repo.settings.collectAsStateWithLifecycle()
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    GlowBackground {
        if (!settings.onboardingDone || showSetup) {
            if (showSetup) BackHandler { showSetup = false }
            SetupScreen(firstRun = !settings.onboardingDone, onDone = { showSetup = false })
        } else {
            MainTabs(tab = tab, onTab = { tab = it }, onOpenSetup = { showSetup = true })
        }
    }
}

@Composable
private fun MainTabs(tab: Tab, onTab: (Tab) -> Unit, onOpenSetup: () -> Unit) {
    if (tab != Tab.HOME) BackHandler { onTab(Tab.HOME) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    onOpenApps = { onTab(Tab.APPS) },
                    onOpenChat = { onTab(Tab.MIRA) },
                    onOpenSetup = onOpenSetup,
                )
                Tab.APPS -> AppsScreen()
                Tab.MIRA -> ChatScreen()
                Tab.SETTINGS -> SettingsScreen(onOpenSetup = onOpenSetup)
            }
        }
        if (!imeVisible) GlassNavBar(selected = tab, onSelect = onTab)
    }
}
