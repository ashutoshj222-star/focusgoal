package com.focusgoal.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.focus.BlockPolicy
import com.focusgoal.app.focus.FocusManager
import com.focusgoal.app.ui.BlockedActivity

/**
 * Watches which app comes to the foreground. While a session runs, opening a blocked app sends the
 * user home and shows Mira's "not now" screen. Only package names are read — never screen content.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private var lastBlockedPkg: String? = null
    private var lastBlockedAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        val repo = FocusRepository.get(this)
        val session = repo.activeSession()
        if (session == null) {
            // Timer ran out but the alarm hasn't fired yet (e.g. Doze) — wrap the session up now.
            if (repo.session.value != null) FocusManager.finishIfExpired(this)
            return
        }

        if (!BlockPolicy.shouldBlock(this, pkg, session)) return

        // Some apps fire several window events while opening; react once.
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastBlockedPkg && now - lastBlockedAt < 800) return
        lastBlockedPkg = pkg
        lastBlockedAt = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockedActivity::class.java)
                .putExtra(BlockedActivity.EXTRA_PACKAGE, pkg)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                ),
        )
    }

    override fun onInterrupt() = Unit
}
