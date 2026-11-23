package com.ilukezippo.grabbit.download

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.ilukezippo.grabbit.MainActivity
import com.ilukezippo.grabbit.Notifications
import com.ilukezippo.grabbit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps the app alive while downloads run and shows their progress. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, Notifications.ID_DOWNLOADS, build(0, -1f, getString(R.string.preparing)), type)
        if (!started) {
            started = true
            scope.launch {
                Downloads.tasks.collectLatest { list ->
                    val active = list.filter { it.active }
                    if (active.isEmpty()) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@collectLatest
                    }
                    val running = active.firstOrNull { it.status == Status.RUNNING }
                    val n = build(active.size, running?.progress ?: -1f, running?.spec?.title ?: getString(R.string.preparing))
                    runCatching { NotificationManagerCompat.from(this@DownloadService).notify(Notifications.ID_DOWNLOADS, n) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(count: Int, progress: Float, title: String) =
        NotificationCompat.Builder(this, Notifications.CH_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_stat_grab)
            .setContentTitle(title)
            .setContentText(resources.getQuantityString(R.plurals.n_downloading, count.coerceAtLeast(1), count.coerceAtLeast(1)))
            .setProgress(100, progress.toInt().coerceAtLeast(0), progress < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("tab", 1), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
