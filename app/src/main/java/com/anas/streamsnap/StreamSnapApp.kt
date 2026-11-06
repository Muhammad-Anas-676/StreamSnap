package com.anas.streamsnap

import android.app.Application
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.engine.Engine
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.service.Notifier
import com.anas.streamsnap.util.LogStore

class StreamSnapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        LogStore.init(this)
        Notifier.ensureChannels(this)
        QueueManager.init(this)
        LogStore.i("app", "StreamSnap started")
        // Python + ffmpeg are unpacked on the first start, so do it off the main thread.
        Thread({ Engine.ensureInit(this) }, "engine-init").start()
    }
}
