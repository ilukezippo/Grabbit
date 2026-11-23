package com.ilukezippo.grabbit.recorder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ilukezippo.grabbit.MainActivity
import com.ilukezippo.grabbit.Notifications
import com.ilukezippo.grabbit.R
import com.ilukezippo.grabbit.download.Downloads
import com.ilukezippo.grabbit.download.MediaSaver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Records the screen (and, on Android 10+, the sound apps are playing) to MP4. */
class ScreenRecordService : Service() {
    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_AUDIO = "audio"
        const val EXTRA_COUNTDOWN = "countdown"
        const val ACTION_STOP = "com.ilukezippo.grabbit.STOP_RECORDING"

        private val _recording = MutableStateFlow(0L) // start time (elapsedRealtime) or 0
        val recordingSince: StateFlow<Long> = _recording
        private val _countdown = MutableStateFlow(0) // seconds left before recording starts, 0 = none
        val countdown: StateFlow<Int> = _countdown

        fun start(ctx: Context, code: Int, data: Intent, audio: Boolean, countdown: Int = 0) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ScreenRecordService::class.java)
                .putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data).putExtra(EXTRA_AUDIO, audio).putExtra(EXTRA_COUNTDOWN, countdown))
        }

        fun stop(ctx: Context) = ctx.startService(Intent(ctx, ScreenRecordService::class.java).setAction(ACTION_STOP))
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var muxer: MediaMuxer? = null
    private val muxLock = Object()
    private var videoTrack = -1
    private var audioTrack = -1
    private var expectedTracks = 1
    private var muxerStarted = false
    @Volatile private var running = false
    private var output: File? = null
    private var bubble: TextView? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopRecording(); return START_NOT_STICKY }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        ServiceCompat.startForeground(this, Notifications.ID_RECORDER, notification(), type)
        if (running || counting) return START_NOT_STICKY
        val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java) else intent?.getParcelableExtra(EXTRA_DATA)
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        val wantAudio = intent!!.getBooleanExtra(EXTRA_AUDIO, true)
        val seconds = intent.getIntExtra(EXTRA_COUNTDOWN, 0)
        try {
            acquire(code, data)
            if (seconds > 0) countdownThen(seconds) { safeBegin(wantAudio) } else begin(wantAudio)
        } catch (e: Throwable) {
            Toast.makeText(this, getString(R.string.rec_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            cleanup(); stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Uses the screen-capture consent right away (it's single-use); recording starts later. */
    private fun acquire(code: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = mpm.getMediaProjection(code, data) ?: error("projection denied")
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { ui.post { stopRecording() } }
        }, ui)
    }

    private fun safeBegin(wantAudio: Boolean) {
        try { begin(wantAudio) } catch (e: Throwable) {
            Toast.makeText(this, getString(R.string.rec_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            cleanup(); stopSelf()
        }
    }

    // ---- countdown before recording ----
    @Volatile private var counting = false
    private var countView: TextView? = null

    private fun countdownThen(seconds: Int, then: () -> Unit) {
        counting = true
        val d = resources.displayMetrics.density
        val canOverlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
        if (canOverlay) {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val tv = TextView(this).apply {
                setTextColor(0xFFFFFFFF.toInt()); textSize = 64f; gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xEE8B5CF6.toInt(), 0xEEEC4899.toInt(), 0xEEF59E0B.toInt())).apply { shape = GradientDrawable.OVAL }
            }
            val size = (150 * d).toInt()
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val lp = WindowManager.LayoutParams(size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
            runCatching { wm.addView(tv, lp); countView = tv }
        } else {
            Toast.makeText(this, getString(R.string.rec_starts_in, seconds), Toast.LENGTH_SHORT).show()
        }
        var left = seconds
        val tick = object : Runnable {
            override fun run() {
                if (!counting) return
                if (left <= 0) {
                    hideCount(); counting = false; _countdown.value = 0
                    // Let the countdown circle disappear before the first frame is captured.
                    ui.postDelayed({ then() }, 250)
                    return
                }
                _countdown.value = left
                countView?.text = left.toString()
                left--
                ui.postDelayed(this, 1000)
            }
        }
        ui.post(tick)
    }

    private fun hideCount() {
        countView?.let { v -> runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } }
        countView = null
    }

    private fun begin(wantAudio: Boolean) {
        val mp = projection ?: error("projection lost")

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val scale = minOf(1f, 1920f / maxOf(metrics.widthPixels, metrics.heightPixels))
        val w = ((metrics.widthPixels * scale).toInt() / 16) * 16
        val h = ((metrics.heightPixels * scale).toInt() / 16) * 16

        val out = File(cacheDir, "rec_${System.currentTimeMillis()}.mp4").also { output = it }
        muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val vc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        vc.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = vc.createInputSurface()
        vc.start()
        videoCodec = vc

        val audioOk = wantAudio && Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (audioOk) runCatching { setupAudio(mp) }.onFailure { audioRecord = null; audioCodec = null }
        expectedTracks = if (audioCodec != null) 2 else 1

        display = mp.createVirtualDisplay("Grabbit", w, h, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null)
        running = true
        _recording.value = SystemClock.elapsedRealtime()
        thread(name = "rec-video") { drain(vc, isVideo = true) }
        if (audioCodec != null) thread(name = "rec-audio") { audioLoop() }
        showBubble()
    }

    @SuppressLint("MissingPermission")
    private fun setupAudio(mp: MediaProjection) {
        if (Build.VERSION.SDK_INT < 29) return
        val cfg = AudioPlaybackCaptureConfiguration.Builder(mp)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val rate = 44100
        val fmt = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord.Builder().setAudioFormat(fmt).setBufferSizeInBytes(min * 4).setAudioPlaybackCaptureConfig(cfg).build()
        val af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, min * 4)
        }
        val ac = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        ac.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        ac.start()
        rec.startRecording()
        audioRecord = rec
        audioCodec = ac
    }

    private fun audioLoop() {
        val rec = audioRecord ?: return
        val ac = audioCodec ?: return
        val startNs = System.nanoTime()
        val buf = ByteArray(4096)
        try {
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                val idx = ac.dequeueInputBuffer(10_000)
                if (idx >= 0) {
                    ac.getInputBuffer(idx)!!.apply { clear(); put(buf, 0, n) }
                    ac.queueInputBuffer(idx, 0, n, (System.nanoTime() - startNs) / 1000, 0)
                }
                drainOnce(ac, isVideo = false)
            }
            val idx = ac.dequeueInputBuffer(10_000)
            if (idx >= 0) ac.queueInputBuffer(idx, 0, 0, (System.nanoTime() - startNs) / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            repeat(20) { drainOnce(ac, isVideo = false) }
        } catch (_: Throwable) { }
    }

    private fun drain(codec: MediaCodec, isVideo: Boolean) {
        try {
            while (running) drainOnce(codec, isVideo)
        } catch (_: Throwable) { }
    }

    private val info = ThreadLocal.withInitial { MediaCodec.BufferInfo() }

    private fun drainOnce(codec: MediaCodec, isVideo: Boolean) {
        val bi = info.get()!!
        val idx = codec.dequeueOutputBuffer(bi, 10_000)
        when {
            idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(muxLock) {
                val m = muxer ?: return
                if (isVideo) videoTrack = m.addTrack(codec.outputFormat) else audioTrack = m.addTrack(codec.outputFormat)
                val tracks = (if (videoTrack >= 0) 1 else 0) + (if (audioTrack >= 0) 1 else 0)
                if (tracks == expectedTracks && !muxerStarted) { m.start(); muxerStarted = true }
            }
            idx >= 0 -> {
                val data = codec.getOutputBuffer(idx)
                if (data != null && bi.size > 0 && bi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) synchronized(muxLock) {
                    if (muxerStarted) {
                        data.position(bi.offset); data.limit(bi.offset + bi.size)
                        muxer?.writeSampleData(if (isVideo) videoTrack else audioTrack, data, bi)
                    }
                }
                codec.releaseOutputBuffer(idx, false)
            }
        }
    }

    private fun stopRecording() {
        if (counting) { counting = false; _countdown.value = 0; hideCount() }
        if (!running) { cleanup(); stopSelf(); return }
        running = false
        _recording.value = 0
        hideBubble()
        thread(name = "rec-finish") {
            Thread.sleep(300)
            runCatching { videoCodec?.signalEndOfInputStream() }
            val hadData = synchronized(muxLock) { muxerStarted }
            cleanup()
            val out = output
            if (hadData && out != null && out.length() > 0) {
                val name = "Grabbit_rec_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
                runCatching {
                    val saved = MediaSaver.save(this, out, name)
                    Downloads.addExternal(this, getString(R.string.rec_title), saved)
                    ui.post { Toast.makeText(this, R.string.rec_saved, Toast.LENGTH_SHORT).show() }
                }
            }
            out?.delete()
            ui.post { stopSelf() }
        }
    }

    private fun cleanup() {
        runCatching { display?.release() }; display = null
        runCatching { audioRecord?.stop(); audioRecord?.release() }; audioRecord = null
        runCatching { audioCodec?.stop(); audioCodec?.release() }; audioCodec = null
        runCatching { videoCodec?.stop(); videoCodec?.release() }; videoCodec = null
        synchronized(muxLock) {
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            muxer = null; muxerStarted = false; videoTrack = -1; audioTrack = -1
        }
        runCatching { projection?.stop() }; projection = null
    }

    // ---- floating stop bubble ----
    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        val tv = TextView(this).apply {
            text = "● 00:00"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 40 * d; setColor(0xE6E5384F.toInt()) }
            elevation = 8 * d
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = (16 * d).toInt(); y = (120 * d).toInt()
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 10 * d) moved = true
                    lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt(); runCatching { wm.updateViewLayout(tv, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) stopRecording()
            }
            true
        }
        runCatching { wm.addView(tv, lp); bubble = tv }
        val tick = object : Runnable {
            override fun run() {
                val since = _recording.value
                val b = bubble ?: return
                if (since == 0L) return
                val s = (SystemClock.elapsedRealtime() - since) / 1000
                b.text = String.format(Locale.US, "■  %02d:%02d", s / 60, s % 60)
                ui.postDelayed(this, 500)
            }
        }
        ui.post(tick)
    }

    private fun hideBubble() {
        bubble?.let { b -> ui.post { runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(b) } } }
        bubble = null
    }

    private fun notification() = NotificationCompat.Builder(this, Notifications.CH_RECORDER)
        .setSmallIcon(R.drawable.ic_stat_grab)
        .setContentTitle(getString(R.string.rec_running))
        .setContentText(getString(R.string.rec_tap_stop))
        .setOngoing(true)
        .setUsesChronometer(true)
        .setContentIntent(PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, getString(R.string.stop), PendingIntent.getService(this, 2,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
        .build()

    override fun onDestroy() {
        if (running) { running = false; cleanup() }
        hideBubble()
        hideCount(); counting = false; _countdown.value = 0
        _recording.value = 0
        super.onDestroy()
    }
}
