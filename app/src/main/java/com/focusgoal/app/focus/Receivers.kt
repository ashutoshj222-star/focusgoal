package com.focusgoal.app.focus

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusgoal.app.data.FocusMode
import com.focusgoal.app.data.FocusRepository

/** Fires when the session's end time is reached. */
class FocusEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FocusManager.finishIfExpired(context)
    }
}

/** Puts the session notification and end alarm back after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FocusManager.restore(context)
    }
}

/**
 * While FocusGoal is an active device admin, Android won't let it be uninstalled until the admin is
 * turned off. During Deep Focus the blocker keeps the user out of Settings, so it can't be turned off
 * until the session ends. Outside a session the user can switch it off normally.
 */
class FocusDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val session = FocusRepository.get(context).activeSession()
        return if (session?.mode == FocusMode.DEEP) {
            "Deep Focus is running. Uninstall protection stays on until your session ends."
        } else {
            "Turning this off means FocusGoal can be uninstalled during Deep Focus."
        }
    }
}
