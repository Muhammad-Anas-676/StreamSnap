package com.anas.streamsnap.admin

import android.app.Application
import com.anas.streamsnap.core.cloud.Cloud
import com.anas.streamsnap.core.theme.ThemeManager

class AdminApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.init(this)
        Cloud.init(this)
    }
}
