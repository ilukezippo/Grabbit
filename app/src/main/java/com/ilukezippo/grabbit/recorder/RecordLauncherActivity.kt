package com.ilukezippo.grabbit.recorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ilukezippo.grabbit.ui.Prefs

/**
 * Invisible screen that asks for the microphone/app-sound permission and Android's
 * screen-capture consent, then starts [ScreenRecordService]. Used by the Tools button,
 * the notification shortcut and the Quick Settings tile. If already recording, it stops.
 */
class RecordLauncherActivity : AppCompatActivity() {
    private val prefs by lazy { Prefs(this) }
    private val fromApp by lazy { intent.getBooleanExtra(EXTRA_FROM_APP, false) }

    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
            ScreenRecordService.start(this, r.resultCode, r.data!!, wantAudio(), prefs.recCountdown)
            // From inside Grabbit: get out of the way so the user's screen is what gets recorded.
            if (fromApp) startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    private val mic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) prefs.recordAudio = false
        askCapture()
    }

    private fun wantAudio() = prefs.recordAudio && Build.VERSION.SDK_INT >= 29 &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun askCapture() {
        capture.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // a result is on its way
        if (ScreenRecordService.recordingSince.value != 0L || ScreenRecordService.countdown.value != 0) {
            ScreenRecordService.stop(this); finish(); return
        }
        if (prefs.recordAudio && Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) mic.launch(Manifest.permission.RECORD_AUDIO) else askCapture()
    }

    companion object {
        const val EXTRA_FROM_APP = "fromApp"
    }
}
