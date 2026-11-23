package com.ilukezippo.grabbit.recorder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/** "Grabbit Record" Quick Settings tile: starts (or stops) a screen recording from the pull-down shade. */
@RequiresApi(24)
class RecordTileService : TileService() {
    override fun onStartListening() {
        val active = ScreenRecordService.recordingSince.value != 0L || ScreenRecordService.countdown.value != 0
        qsTile?.apply { state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; updateTile() }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        if (ScreenRecordService.recordingSince.value != 0L || ScreenRecordService.countdown.value != 0) {
            ScreenRecordService.stop(this); return
        }
        val i = Intent(this, RecordLauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}
