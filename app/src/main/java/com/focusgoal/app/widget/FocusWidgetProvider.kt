package com.focusgoal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.focusgoal.app.R
import com.focusgoal.app.data.FocusMode
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.focus.FocusManager
import com.focusgoal.app.focus.Permissions
import com.focusgoal.app.ui.MainActivity

/** Home-screen widget: a live countdown while focusing, or a one-tap "start focus" button. */
class FocusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        FocusManager.finishIfExpired(context)
        ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_QUICK_START) return

        val repo = FocusRepository.get(context)
        if (repo.activeSession() == null && isReady(context)) {
            FocusManager.start(context, FocusMode.FOCUS, repo.settings.value.lastDurationMinutes)
        } else {
            updateAll(context)
        }
    }

    companion object {
        private const val ACTION_QUICK_START = "com.focusgoal.app.widget.QUICK_START"

        /** Quick start only works once the blocker is enabled and some apps are picked. */
        private fun isReady(context: Context) =
            Permissions.accessibilityEnabled(context) && FocusRepository.get(context).blockedApps.value.isNotEmpty()

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FocusWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val repo = FocusRepository.get(context)
            val session = repo.activeSession()
            val views = RemoteViews(context.packageName, R.layout.widget_focus)

            val openApp = PendingIntent.getActivity(
                context, 10,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)

            if (session != null) {
                val base = SystemClock.elapsedRealtime() + session.remainingMs()
                views.setViewVisibility(R.id.widget_chrono, View.VISIBLE)
                views.setViewVisibility(R.id.widget_idle_time, View.GONE)
                views.setChronometer(R.id.widget_chrono, base, null, true)
                views.setChronometerCountDown(R.id.widget_chrono, true)
                views.setTextViewText(
                    R.id.widget_status,
                    if (session.mode == FocusMode.DEEP) "Deep Focus · locked 🔒" else "Focusing · apps blocked",
                )
                views.setTextViewText(R.id.widget_title, "Mira is guarding your focus")
                views.setTextViewText(R.id.widget_button, "Open FocusGoal")
                views.setOnClickPendingIntent(R.id.widget_button, openApp)
            } else {
                val minutes = repo.settings.value.lastDurationMinutes
                views.setViewVisibility(R.id.widget_chrono, View.GONE)
                views.setViewVisibility(R.id.widget_idle_time, View.VISIBLE)
                views.setTextViewText(R.id.widget_idle_time, String.format("%02d:00", minutes))
                views.setTextViewText(R.id.widget_status, "Today: ${repo.stats.value.todayMinutes} min focused")
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                if (isReady(context)) {
                    views.setTextViewText(R.id.widget_button, "Start $minutes min focus")
                    val quickStart = PendingIntent.getBroadcast(
                        context, 11,
                        Intent(context, FocusWidgetProvider::class.java).setAction(ACTION_QUICK_START),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    views.setOnClickPendingIntent(R.id.widget_button, quickStart)
                } else {
                    views.setTextViewText(R.id.widget_button, "Set up FocusGoal")
                    views.setOnClickPendingIntent(R.id.widget_button, openApp)
                }
            }
            return views
        }
    }
}
