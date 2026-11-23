package com.ilukezippo.grabbit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object Notifications {
    const val CH_DOWNLOADS = "downloads"
    const val CH_RECORDER = "recorder"
    const val ID_DOWNLOADS = 1001
    const val ID_RECORDER = 1002

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DOWNLOADS, ctx.getString(R.string.ch_downloads), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_RECORDER, ctx.getString(R.string.ch_recorder), NotificationManager.IMPORTANCE_LOW))
    }
}
