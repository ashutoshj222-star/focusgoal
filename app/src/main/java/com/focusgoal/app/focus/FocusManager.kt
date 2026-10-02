package com.focusgoal.app.focus

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.focusgoal.app.R
import com.focusgoal.app.ai.MiraChat
import com.focusgoal.app.ai.MiraLines
import com.focusgoal.app.data.FocusMode
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.data.FocusSession
import com.focusgoal.app.ui.MainActivity
import com.focusgoal.app.widget.FocusWidgetProvider
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Starts, stops and finishes focus sessions, and keeps the notification, alarm and widget in sync. */
object FocusManager {

    const val CHANNEL_SESSION = "focus_session"
    const val CHANNEL_MIRA = "mira"
    private const val NOTIF_SESSION = 1001
    private const val NOTIF_MIRA = 1002
    private const val REQ_END_ALARM = 2001

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SESSION, "Focus session", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the running focus timer"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MIRA, "Messages from Mira", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Encouragement when sessions start and finish"
            },
        )
    }

    fun start(context: Context, mode: FocusMode, minutes: Int, lockWholePhone: Boolean = false): FocusSession {
        val repo = FocusRepository.get(context)
        repo.activeSession()?.let { return it } // never replace a running session

        val now = System.currentTimeMillis()
        val session = FocusSession(
            mode = mode,
            startAt = now,
            endAt = now + minutes * 60_000L,
            blocked = repo.blockedApps.value,
            lockWholePhone = mode == FocusMode.DEEP && lockWholePhone,
        )
        repo.saveSession(session)
        repo.updateSettings { it.copy(lastDurationMinutes = minutes) }

        scheduleEndAlarm(context, session.endAt)
        showSessionNotification(context, session)
        FocusWidgetProvider.updateAll(context)

        val line = MiraLines.sessionStart(mode, minutes, repo.settings.value.userName)
        MiraChat.get(context).addMiraMessage(line)
        notifyMira(context, line)
        return session
    }

    /** User pressed stop. Only allowed in regular Focus mode — Deep Focus can't be stopped. */
    fun stop(context: Context): Boolean {
        val repo = FocusRepository.get(context)
        val session = repo.activeSession() ?: run { repo.clearSession(); return true }
        if (session.mode == FocusMode.DEEP) return false

        val elapsedMin = ((System.currentTimeMillis() - session.startAt) / 60_000.0).roundToInt()
        if (elapsedMin > 0) repo.recordFocus(elapsedMin, completed = false)
        endCleanup(context)
        MiraChat.get(context).addMiraMessage(MiraLines.sessionStopped(elapsedMin))
        return true
    }

    /** Called when the timer runs out (alarm, UI tick or accessibility check — whichever is first). */
    fun finishIfExpired(context: Context) {
        val repo = FocusRepository.get(context)
        val session = repo.session.value ?: return
        if (session.isRunning()) return

        val minutes = (session.durationMs / 60_000.0).roundToInt()
        repo.recordFocus(minutes, completed = true)
        endCleanup(context)

        val line = MiraLines.sessionComplete(minutes, repo.settings.value.userName)
        MiraChat.get(context).addMiraMessage(line)
        notifyMira(context, line, title = "Session complete 🎉")
    }

    private fun endCleanup(context: Context) {
        FocusRepository.get(context).clearSession()
        cancelEndAlarm(context)
        NotificationManagerCompat.from(context).cancel(NOTIF_SESSION)
        FocusWidgetProvider.updateAll(context)
    }

    /** Re-arms alarm + notification after a reboot or app update. */
    fun restore(context: Context) {
        val repo = FocusRepository.get(context)
        val session = repo.session.value ?: return
        if (!session.isRunning()) {
            finishIfExpired(context)
            return
        }
        scheduleEndAlarm(context, session.endAt)
        showSessionNotification(context, session)
        FocusWidgetProvider.updateAll(context)
    }

    // ---- Alarm ----

    private fun endAlarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQ_END_ALARM,
        Intent(context, FocusEndReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleEndAlarm(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = endAlarmIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelEndAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(endAlarmIntent(context))
    }

    // ---- Notifications ----

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("MissingPermission")
    private fun showSessionNotification(context: Context, session: FocusSession) {
        if (!canNotify(context)) return
        val endsAt = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(session.endAt))
        val title = if (session.mode == FocusMode.DEEP) "Deep Focus 🔒" else "Focus mode"
        val text = when {
            session.lockWholePhone -> "Phone locked until $endsAt"
            session.mode == FocusMode.DEEP -> "Apps are locked until $endsAt. No stopping now!"
            else -> "Distracting apps are blocked until $endsAt"
        }
        val n = NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(session.endAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_SESSION, n)
    }

    @SuppressLint("MissingPermission")
    fun notifyMira(context: Context, message: String, title: String = "Mira") {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_MIRA)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_MIRA, n)
    }
}
