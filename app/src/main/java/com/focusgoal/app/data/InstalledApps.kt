package com.focusgoal.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

object InstalledApps {

    /** Apps people most often want to block. Pre-selected the first time if installed. */
    val SUGGESTED = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok (some regions)
        "com.snapchat.android",
        "com.twitter.android", // X
        "com.reddit.frontpage",
        "com.pinterest",
        "com.netflix.mediaclient",
        "com.whatsapp",
        "org.telegram.messenger",
        "com.discord",
        "tv.twitch.android.app",
        "com.linkedin.android",
        "in.mohalla.sharechat",
        "com.instagram.barcelona", // Threads
    )

    @Volatile
    private var cache: List<AppEntry>? = null

    /** Every launchable app except FocusGoal itself, sorted by name. Call off the main thread. */
    fun load(context: Context, forceRefresh: Boolean = false): List<AppEntry> {
        if (!forceRefresh) cache?.let { return it }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val iconPx = (48 * context.resources.displayMetrics.density).toInt()
        val apps = pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                val icon = runCatching {
                    info.loadIcon(pm).toBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888).asImageBitmap()
                }.getOrNull()
                AppEntry(info.packageName, info.loadLabel(pm).toString(), icon)
            }
            .sortedBy { it.label.lowercase() }
            .toList()
        cache = apps
        return apps
    }

    fun label(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    fun icon(context: Context, packageName: String): ImageBitmap? = cache?.firstOrNull { it.packageName == packageName }?.icon
        ?: runCatching {
            val px = (48 * context.resources.displayMetrics.density).toInt()
            context.packageManager.getApplicationIcon(packageName).toBitmap(px, px, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull()
}
