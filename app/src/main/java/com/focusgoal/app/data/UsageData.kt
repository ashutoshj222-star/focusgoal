package com.focusgoal.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

enum class AppCategory(val label: String, val color: Color) {
    DISTRACTING("Distracting", Color(0xFFFF8A3D)),
    PRODUCTIVE("Productive", Color(0xFF4ADE80)),
    OTHERS("Others", Color(0xFF8E8AA8)),
}

/** Time spent in each app (milliseconds) on one day. */
data class DayUsage(val date: LocalDate, val perApp: Map<String, Long>)

data class WeekUsage(val weekStart: LocalDate, val days: List<DayUsage>)

/**
 * Screen-time numbers from Android's usage history (needs the "Usage access" permission).
 * Time is counted from app open/close events, the same source Digital Wellbeing uses.
 */
object UsageData {

    /** Apps that are usually work/study. Everything else falls back to Android's own app category. */
    private val PRODUCTIVE = setOf(
        "com.openai.chatgpt",
        "com.anthropic.claude",
        "com.google.android.apps.bard",
        "com.google.android.gm",
        "com.google.android.calendar",
        "com.google.android.keep",
        "com.google.android.apps.docs",
        "com.google.android.apps.docs.editors.docs",
        "com.google.android.apps.docs.editors.sheets",
        "com.google.android.apps.docs.editors.slides",
        "com.google.android.apps.classroom",
        "com.microsoft.office.word",
        "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint",
        "com.microsoft.office.outlook",
        "com.microsoft.teams",
        "com.microsoft.office.onenote",
        "notion.id",
        "com.slack",
        "us.zoom.videomeetings",
        "org.khanacademy.android",
        "com.duolingo",
        "com.adobe.reader",
        "com.todoist",
        "com.ticktick.task",
    )

    private val cache = ConcurrentHashMap<LocalDate, Map<String, Long>>()

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Some phones open FocusGoal's own switch with the package Uri; others need the plain list. */
    fun settingsIntents(context: Context) = listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
    )

    fun week(context: Context, anyDay: LocalDate): WeekUsage {
        val start = anyDay.with(DayOfWeek.MONDAY)
        return WeekUsage(start, (0L until 7L).map { day(context, start.plusDays(it)) })
    }

    fun day(context: Context, date: LocalDate): DayUsage {
        val today = LocalDate.now()
        if (date.isAfter(today)) return DayUsage(date, emptyMap())
        if (date.isBefore(today)) cache[date]?.let { return DayUsage(date, it) }

        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val usage = usageBetween(context, start, end)
        if (date.isBefore(today)) cache[date] = usage
        return DayUsage(date, usage)
    }

    private fun usageBetween(context: Context, start: Long, end: Long): Map<String, Long> {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val events = runCatching { usm.queryEvents(start, end) }.getOrNull() ?: return emptyMap()
        val excluded = excludedPackages(context)

        val openSince = HashMap<String, Long>()
        val seen = HashSet<String>()
        val totals = HashMap<String, Long>()
        fun add(pkg: String, ms: Long) {
            if (ms > 0) totals[pkg] = (totals[pkg] ?: 0L) + ms
        }

        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            val t = e.timeStamp
            when (e.eventType) {
                EVENT_RESUMED -> {
                    if (pkg !in openSince) openSince[pkg] = t
                    seen += pkg
                }
                EVENT_PAUSED -> {
                    // An app already open at midnight shows up first as a "paused" event.
                    val from = openSince.remove(pkg) ?: if (pkg !in seen) start else null
                    if (from != null) add(pkg, t - from)
                    seen += pkg
                }
                EVENT_STOPPED -> openSince.remove(pkg)?.let { add(pkg, t - it) }
                EVENT_SCREEN_OFF, EVENT_SHUTDOWN -> {
                    openSince.forEach { (p, from) -> add(p, t - from) }
                    openSince.clear()
                }
            }
        }
        val stop = minOf(end, System.currentTimeMillis())
        openSince.forEach { (p, from) -> add(p, stop - from) }

        return totals.filter { (pkg, ms) -> pkg !in excluded && ms >= 30_000 }
    }

    private fun excludedPackages(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = context.packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }
        return launchers.toSet() + setOf(context.packageName, "android", "com.android.systemui")
    }

    // ---- Categories ----

    private fun prefs(context: Context) = context.getSharedPreferences("usage_categories", Context.MODE_PRIVATE)

    fun category(context: Context, packageName: String): AppCategory {
        prefs(context).getString(packageName, null)?.let { saved ->
            runCatching { return AppCategory.valueOf(saved) }
        }
        if (packageName in InstalledApps.SUGGESTED) return AppCategory.DISTRACTING
        if (packageName in FocusRepository.get(context).blockedApps.value) return AppCategory.DISTRACTING
        if (packageName in PRODUCTIVE) return AppCategory.PRODUCTIVE
        val androidCategory = runCatching { context.packageManager.getApplicationInfo(packageName, 0).category }.getOrDefault(-1)
        return when (androidCategory) {
            ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_VIDEO -> AppCategory.DISTRACTING
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVE
            else -> AppCategory.OTHERS
        }
    }

    fun setCategory(context: Context, packageName: String, category: AppCategory) {
        prefs(context).edit().putString(packageName, category.name).apply()
    }

    // UsageEvents.Event types (numeric so they work on every Android version we support).
    private const val EVENT_RESUMED = 1 // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    private const val EVENT_PAUSED = 2 // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    private const val EVENT_SCREEN_OFF = 16 // SCREEN_NON_INTERACTIVE
    private const val EVENT_STOPPED = 23 // ACTIVITY_STOPPED
    private const val EVENT_SHUTDOWN = 26 // DEVICE_SHUTDOWN
}

/** "7h 8m", "32m", "<1m". */
fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
