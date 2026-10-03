package com.focusgoal.app.focus

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.focusgoal.app.data.FocusMode
import com.focusgoal.app.data.FocusSession

/** Decides whether a package that just came to the foreground must be blocked. */
object BlockPolicy {

    /** Ways out of Deep Focus: Settings (turning off the blocker / admin) and uninstallers. */
    private val DEEP_FOCUS_ESCAPES = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.android.vending", // Play Store can uninstall apps too
    )

    /** Always reachable, even with "lock entire phone": calls, emergency, alarms, system UI. */
    private val ALWAYS_ALLOWED = setOf(
        "android",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.android.dialer",
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
    )

    @Volatile
    private var essentialsCache: Pair<Long, Set<String>>? = null

    fun shouldBlock(context: Context, packageName: String, session: FocusSession): Boolean {
        if (packageName == context.packageName) return false
        if (packageName in session.blocked) return true
        if (session.mode != FocusMode.DEEP) return false
        if (packageName in DEEP_FOCUS_ESCAPES) return true
        if (session.lockWholePhone) return packageName !in essentials(context, session.startAt)
        return false
    }

    /** Launchers, keyboards, the default dialer/SMS apps and [ALWAYS_ALLOWED]. Cached per session. */
    private fun essentials(context: Context, sessionKey: Long): Set<String> {
        essentialsCache?.let { (key, set) -> if (key == sessionKey) return set }
        val pm = context.packageManager
        val set = HashSet(ALWAYS_ALLOWED)
        set += context.packageName

        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(home, 0).forEach { set += it.activityInfo.packageName }

        runCatching {
            context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.forEach { set += it.packageName }
        }
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')?.let { set += it }
        runCatching { context.getSystemService(TelecomManager::class.java).defaultDialerPackage }.getOrNull()?.let { set += it }
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let { set += it }

        essentialsCache = sessionKey to set
        return set
    }
}
