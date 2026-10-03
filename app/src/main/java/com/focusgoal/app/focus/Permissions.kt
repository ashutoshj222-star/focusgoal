package com.focusgoal.app.focus

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.focusgoal.app.service.BlockerAccessibilityService

/** Checks for, and settings screens that grant, everything FocusGoal needs. */
object Permissions {

    fun accessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, BlockerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (component in splitter) {
            if (ComponentName.unflattenFromString(component) == expected) return true
        }
        return false
    }

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun overlayAllowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun adminComponent(context: Context) = ComponentName(context, FocusDeviceAdminReceiver::class.java)

    fun deviceAdminActive(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(context))

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    // ---- Intents that open the right settings screen ----

    /**
     * Opens the Accessibility list. On many phones the fragment-args extras scroll to and highlight
     * FocusGoal's entry. (Opening our switch directly needs a system-only permission, so we don't.)
     */
    fun accessibilitySettings(context: Context): Intent {
        val component = ComponentName(context, BlockerAccessibilityService::class.java).flattenToString()
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", component)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", component) })
    }

    fun appInfo(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun notificationSettings(context: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun overlaySettings(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun deviceAdminRequest(context: Context) = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Lets Deep Focus stop FocusGoal from being uninstalled until your session ends.",
        )

    @android.annotation.SuppressLint("BatteryLife")
    fun batteryRequest(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
}
