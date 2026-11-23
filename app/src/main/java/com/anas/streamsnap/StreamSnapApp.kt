package com.anas.streamsnap

import android.app.Application
import com.anas.streamsnap.core.cloud.Cloud
import com.anas.streamsnap.core.theme.ThemeManager
import com.anas.streamsnap.session.CloudSync
import com.anas.streamsnap.session.ProfileStore
import com.anas.streamsnap.session.Session
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.engine.Engine
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.service.Notifier
import com.anas.streamsnap.util.LogStore

class StreamSnapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        ThemeManager.init(this)
        Cloud.init(this)
        ProfileStore.init(this)
        Session.init(this)
        LogStore.init(this)
        Notifier.ensureChannels(this)
        QueueManager.init(this)
        CloudSync.start()
        LogStore.i("app", "StreamSnap started")
        // Python + ffmpeg are unpacked on the first start, so do it off the main thread.
        // After that, an old engine (3+ weeks) is updated quietly: sites like YouTube change often.
        Thread({
            if (Engine.ensureInit(this)) Engine.autoUpdateIfOld(this)
        }, "engine-init").start()
    }
}
