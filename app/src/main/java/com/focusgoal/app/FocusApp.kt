package com.focusgoal.app

import android.app.Application
import com.focusgoal.app.focus.FocusManager

class FocusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FocusManager.createChannels(this)
        FocusManager.restore(this)
    }
}
