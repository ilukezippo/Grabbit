package com.ilukezippo.grabbit.ui

import android.content.Context
import com.ilukezippo.grabbit.core.AudioFormat
import com.ilukezippo.grabbit.core.VideoContainer

/** Remembered download defaults. height 0 = best. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    var audioMode: Boolean
        get() = sp.getBoolean("audio", false); set(v) = sp.edit().putBoolean("audio", v).apply()
    var height: Int
        get() = sp.getInt("height", 1080); set(v) = sp.edit().putInt("height", v).apply()
    var container: VideoContainer
        get() = runCatching { VideoContainer.valueOf(sp.getString("container", "MP4")!!) }.getOrDefault(VideoContainer.MP4)
        set(v) = sp.edit().putString("container", v.name).apply()
    var audioFormat: AudioFormat
        get() = runCatching { AudioFormat.valueOf(sp.getString("afmt", "MP3")!!) }.getOrDefault(AudioFormat.MP3)
        set(v) = sp.edit().putString("afmt", v.name).apply()
    var recordAudio: Boolean
        get() = sp.getBoolean("recAudio", true); set(v) = sp.edit().putBoolean("recAudio", v).apply()
}
