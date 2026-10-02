package com.focusgoal.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

enum class FocusMode { FOCUS, DEEP }

data class FocusSession(
    val mode: FocusMode,
    val startAt: Long,
    val endAt: Long,
    val blocked: Set<String>,
    /** Deep Focus only: block every app except calls, messages, clock and the launcher. */
    val lockWholePhone: Boolean,
) {
    val durationMs: Long get() = endAt - startAt
    fun remainingMs(now: Long = System.currentTimeMillis()): Long = (endAt - now).coerceAtLeast(0)
    fun isRunning(now: Long = System.currentTimeMillis()): Boolean = now < endAt
}

data class FocusSettings(
    val userName: String = "",
    val apiKey: String = "",
    val lockWholePhoneByDefault: Boolean = false,
    val onboardingDone: Boolean = false,
    val lastDurationMinutes: Int = 25,
)

data class FocusStats(
    val todayMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val sessionsCompleted: Int = 0,
    val streakDays: Int = 0,
)

/**
 * Single source of truth for everything FocusGoal stores. Lives in SharedPreferences so the
 * accessibility service, receivers, widget and UI all read the same state, and so an active
 * session survives the app being killed or the phone rebooting.
 */
class FocusRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("focusgoal", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<FocusSession?> = _session.asStateFlow()

    private val _blockedApps = MutableStateFlow(prefs.getStringSet(KEY_BLOCKED, null)?.toSet() ?: emptySet())
    val blockedApps: StateFlow<Set<String>> = _blockedApps.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<FocusSettings> = _settings.asStateFlow()

    private val _stats = MutableStateFlow(loadStats())
    val stats: StateFlow<FocusStats> = _stats.asStateFlow()

    /** Set when setup sends the user to Accessibility settings; the service then reopens the app. */
    var returnAfterAccessibility: Boolean
        get() = prefs.getBoolean(KEY_RETURN_AFTER_A11Y, false)
        set(value) = prefs.edit().putBoolean(KEY_RETURN_AFTER_A11Y, value).apply()

    /** True once the user has picked apps at least once (so we don't re-apply suggestions). */
    val hasChosenApps: Boolean get() = prefs.contains(KEY_BLOCKED)

    /** The session if one is currently running (an expired-but-not-yet-cleared one returns null). */
    fun activeSession(now: Long = System.currentTimeMillis()): FocusSession? =
        _session.value?.takeIf { it.isRunning(now) }

    // ---- Session ----

    fun saveSession(session: FocusSession) {
        prefs.edit()
            .putString(KEY_S_MODE, session.mode.name)
            .putLong(KEY_S_START, session.startAt)
            .putLong(KEY_S_END, session.endAt)
            .putStringSet(KEY_S_BLOCKED, session.blocked)
            .putBoolean(KEY_S_LOCK_ALL, session.lockWholePhone)
            .apply()
        _session.value = session
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_S_MODE).remove(KEY_S_START).remove(KEY_S_END)
            .remove(KEY_S_BLOCKED).remove(KEY_S_LOCK_ALL)
            .apply()
        _session.value = null
    }

    private fun loadSession(): FocusSession? {
        val mode = prefs.getString(KEY_S_MODE, null) ?: return null
        return FocusSession(
            mode = runCatching { FocusMode.valueOf(mode) }.getOrDefault(FocusMode.FOCUS),
            startAt = prefs.getLong(KEY_S_START, 0),
            endAt = prefs.getLong(KEY_S_END, 0),
            blocked = prefs.getStringSet(KEY_S_BLOCKED, null)?.toSet() ?: emptySet(),
            lockWholePhone = prefs.getBoolean(KEY_S_LOCK_ALL, false),
        )
    }

    // ---- Blocked apps ----

    /**
     * Returns false when the change isn't allowed: during Deep Focus you can add apps to the
     * block list but not remove them.
     */
    fun setAppBlocked(packageName: String, blocked: Boolean): Boolean {
        val active = activeSession()
        if (!blocked && active?.mode == FocusMode.DEEP && packageName in active.blocked) return false

        val updated = if (blocked) _blockedApps.value + packageName else _blockedApps.value - packageName
        prefs.edit().putStringSet(KEY_BLOCKED, updated).apply()
        _blockedApps.value = updated

        // Keep a running session in sync so changes apply immediately.
        if (active != null) {
            saveSession(active.copy(blocked = if (blocked) active.blocked + packageName else active.blocked - packageName))
        }
        return true
    }

    fun setBlockedApps(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_BLOCKED, packages).apply()
        _blockedApps.value = packages
    }

    // ---- Settings ----

    fun updateSettings(transform: (FocusSettings) -> FocusSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putString(KEY_NAME, s.userName)
            .putString(KEY_API_KEY, s.apiKey)
            .putBoolean(KEY_LOCK_ALL_DEFAULT, s.lockWholePhoneByDefault)
            .putBoolean(KEY_ONBOARDING, s.onboardingDone)
            .putInt(KEY_LAST_DURATION, s.lastDurationMinutes)
            .apply()
        _settings.value = s
    }

    private fun loadSettings() = FocusSettings(
        userName = prefs.getString(KEY_NAME, "") ?: "",
        apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        lockWholePhoneByDefault = prefs.getBoolean(KEY_LOCK_ALL_DEFAULT, false),
        onboardingDone = prefs.getBoolean(KEY_ONBOARDING, false),
        lastDurationMinutes = prefs.getInt(KEY_LAST_DURATION, 25),
    )

    // ---- Stats ----

    /** Adds focused minutes; [completed] sessions also count toward the daily streak. */
    fun recordFocus(minutes: Int, completed: Boolean) {
        val today = LocalDate.now()
        val todayKey = today.toString()
        val storedDay = prefs.getString(KEY_STATS_DAY, null)
        val todayMinutes = (if (storedDay == todayKey) prefs.getInt(KEY_STATS_TODAY, 0) else 0) + minutes

        var streak = prefs.getInt(KEY_STATS_STREAK, 0)
        var lastCompleted = prefs.getString(KEY_STATS_LAST_COMPLETED, null)
        var sessions = prefs.getInt(KEY_STATS_SESSIONS, 0)
        if (completed) {
            sessions += 1
            streak = when (lastCompleted) {
                todayKey -> streak.coerceAtLeast(1)
                today.minusDays(1).toString() -> streak + 1
                else -> 1
            }
            lastCompleted = todayKey
        }

        prefs.edit()
            .putString(KEY_STATS_DAY, todayKey)
            .putInt(KEY_STATS_TODAY, todayMinutes)
            .putInt(KEY_STATS_TOTAL, prefs.getInt(KEY_STATS_TOTAL, 0) + minutes)
            .putInt(KEY_STATS_SESSIONS, sessions)
            .putInt(KEY_STATS_STREAK, streak)
            .putString(KEY_STATS_LAST_COMPLETED, lastCompleted)
            .apply()
        _stats.value = loadStats()
    }

    /** Re-reads stats so "today" rolls over at midnight. */
    fun refreshStats() {
        _stats.value = loadStats()
    }

    private fun loadStats(): FocusStats {
        val today = LocalDate.now()
        val todayMinutes = if (prefs.getString(KEY_STATS_DAY, null) == today.toString()) prefs.getInt(KEY_STATS_TODAY, 0) else 0
        val last = prefs.getString(KEY_STATS_LAST_COMPLETED, null)
        val streakAlive = last == today.toString() || last == today.minusDays(1).toString()
        return FocusStats(
            todayMinutes = todayMinutes,
            totalMinutes = prefs.getInt(KEY_STATS_TOTAL, 0),
            sessionsCompleted = prefs.getInt(KEY_STATS_SESSIONS, 0),
            streakDays = if (streakAlive) prefs.getInt(KEY_STATS_STREAK, 0) else 0,
        )
    }

    companion object {
        private const val KEY_BLOCKED = "blocked_apps"
        private const val KEY_RETURN_AFTER_A11Y = "return_after_a11y"
        private const val KEY_S_MODE = "session_mode"
        private const val KEY_S_START = "session_start"
        private const val KEY_S_END = "session_end"
        private const val KEY_S_BLOCKED = "session_blocked"
        private const val KEY_S_LOCK_ALL = "session_lock_all"
        private const val KEY_NAME = "user_name"
        private const val KEY_API_KEY = "anthropic_api_key"
        private const val KEY_LOCK_ALL_DEFAULT = "lock_all_default"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_LAST_DURATION = "last_duration"
        private const val KEY_STATS_DAY = "stats_day"
        private const val KEY_STATS_TODAY = "stats_today"
        private const val KEY_STATS_TOTAL = "stats_total"
        private const val KEY_STATS_SESSIONS = "stats_sessions"
        private const val KEY_STATS_STREAK = "stats_streak"
        private const val KEY_STATS_LAST_COMPLETED = "stats_last_completed"

        @Volatile
        private var instance: FocusRepository? = null

        fun get(context: Context): FocusRepository =
            instance ?: synchronized(this) {
                instance ?: FocusRepository(context).also { instance = it }
            }
    }
}
