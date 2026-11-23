package com.ilukezippo.grabbit

import android.app.Application
import com.ilukezippo.grabbit.download.Downloads
import com.ilukezippo.grabbit.engine.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GrabbitApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.createChannels(this)
        Downloads.loadHistory(this)
        scope.launch(Dispatchers.IO) { Engine.init(this@GrabbitApp) }
    }

    companion object {
        lateinit var instance: GrabbitApp
            private set
    }
}
