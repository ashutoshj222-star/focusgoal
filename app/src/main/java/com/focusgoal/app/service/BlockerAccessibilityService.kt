package com.focusgoal.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.focus.BlockPolicy
import com.focusgoal.app.focus.FeatureRules
import com.focusgoal.app.focus.FocusManager
import com.focusgoal.app.ui.BlockedActivity
import com.focusgoal.app.ui.MainActivity

/**
 * Two jobs:
 *  1. During a session, opening a blocked app covers it with the focus timer screen.
 *  2. Feature blocks (YouTube Shorts, Instagram Reels…): when that screen appears, press Back so the
 *     rest of the app stays usable.
 * Screen content is only inspected in apps that have a feature block switched on, and nothing is stored.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private var lastBlockedPkg: String? = null
    private var lastBlockedAt = 0L

    /** Latest Activity class per app, from window-state events (used by screen-name rules). */
    private val activityByPackage = HashMap<String, String>()
    private val lastFeatureCheck = HashMap<String, Long>()
    private var lastFeatureBlockAt = 0L
    private var lastToastAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        val stateChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (stateChanged) event.className?.toString()?.let { activityByPackage[pkg] = it }

        if (stateChanged && handleAppBlock(pkg)) return
        if (pkg in FeatureRules.packages) handleFeatureBlock(pkg, stateChanged)
    }

    /** Whole-app blocking during a session. Returns true if the app was blocked. */
    private fun handleAppBlock(pkg: String): Boolean {
        val repo = FocusRepository.get(this)
        val session = repo.activeSession()
        if (session == null) {
            // Timer ran out but the alarm hasn't fired yet (e.g. Doze) — wrap the session up now.
            if (repo.session.value != null) FocusManager.finishIfExpired(this)
            return false
        }
        if (!BlockPolicy.shouldBlock(this, pkg, session)) return false

        // Some apps fire several window events while opening; react once.
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastBlockedPkg && now - lastBlockedAt < 800) return true
        lastBlockedPkg = pkg
        lastBlockedAt = now

        // Cover the blocked app with the focus timer screen (it stays hidden underneath).
        startActivity(
            Intent(this, BlockedActivity::class.java)
                .putExtra(BlockedActivity.EXTRA_PACKAGE, pkg)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                ),
        )
        return true
    }

    /** Shorts / Reels / Stories blocking inside otherwise-allowed apps. */
    private fun handleFeatureBlock(pkg: String, stateChanged: Boolean) {
        val rules = FeatureRules.forPackage(pkg, FocusRepository.get(this).activeFeatureBlocks())
        if (rules.isEmpty()) return

        // Scrolling fires many events; look at the screen at most every 300 ms per app.
        val now = SystemClock.elapsedRealtime()
        if (!stateChanged && now - (lastFeatureCheck[pkg] ?: 0L) < 300) return
        lastFeatureCheck[pkg] = now
        if (now - lastFeatureBlockAt < 700) return // give the last Back press time to land

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        val hit = rules.firstOrNull { FeatureRules.isShowing(it, root, activityByPackage[pkg]) } ?: return
        lastFeatureBlockAt = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (now - lastToastAt > 3000) {
            lastToastAt = now
            Toast.makeText(this, "Mira blocked ${hit.appName} ${hit.featureName} 🙅‍♀️", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val repo = FocusRepository.get(this)
        if (repo.returnAfterAccessibility) {
            repo.returnAfterAccessibility = false
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }

    override fun onInterrupt() = Unit
}
