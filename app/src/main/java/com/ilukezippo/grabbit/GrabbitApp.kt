package com.ilukezippo.grabbit

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.ilukezippo.grabbit.download.Downloads
import com.ilukezippo.grabbit.engine.Engine
import com.ilukezippo.grabbit.update.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GrabbitApp : Application(), ImageLoaderFactory {
    /** Lets thumbnails show a frame from downloaded videos and screen recordings. */
    override fun newImageLoader() = ImageLoader.Builder(this).components { add(VideoFrameDecoder.Factory()) }.build()

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.createChannels(this)
        if (com.ilukezippo.grabbit.ui.Prefs(this).recShortcut) Notifications.showRecorderShortcut(this)
        Downloads.loadHistory(this)
        scope.launch(Dispatchers.IO) { Engine.init(this@GrabbitApp) }
        scope.launch { runCatching { AppUpdater.check(this@GrabbitApp) } }
    }

    companion object {
        lateinit var instance: GrabbitApp
            private set
    }
}
