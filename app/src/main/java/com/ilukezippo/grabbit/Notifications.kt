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
    const val CH_DONE = "done"


    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DOWNLOADS, ctx.getString(R.string.ch_downloads), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_RECORDER, ctx.getString(R.string.ch_recorder), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, ctx.getString(R.string.ch_done), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** "Download complete": tapping it opens the file. One notification per finished download. */
    fun done(ctx: Context, taskId: String, title: String, file: com.ilukezippo.grabbit.download.SavedFile?, failed: Boolean) {
        val open = if (file != null && !failed)
            android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(file.uri), file.mime)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        else android.content.Intent(ctx, MainActivity::class.java).putExtra("tab", 1)
        val pi = android.app.PendingIntent.getActivity(ctx, taskId.hashCode(), open,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val n = androidx.core.app.NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_grab)
            .setContentTitle(title)
            .setContentText(ctx.getString(if (failed) R.string.notif_failed else R.string.notif_done))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).notify(taskId.hashCode(), n) }
    }
}
