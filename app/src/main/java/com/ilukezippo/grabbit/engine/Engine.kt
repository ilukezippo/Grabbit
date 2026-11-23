package com.ilukezippo.grabbit.engine

import android.content.Context
import com.ilukezippo.grabbit.instagram.InstagramSession
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

sealed interface EngineState {
    data object Loading : EngineState
    data class Ready(val version: String) : EngineState
    data class Failed(val message: String) : EngineState
}

object Engine {
    private val _state = MutableStateFlow<EngineState>(EngineState.Loading)
    val state: StateFlow<EngineState> = _state

    fun init(ctx: Context) {
        _state.value = try {
            YoutubeDL.getInstance().init(ctx)
            FFmpeg.getInstance().init(ctx)
            EngineState.Ready(YoutubeDL.getInstance().versionName(ctx) ?: YoutubeDL.getInstance().version(ctx) ?: "?")
        } catch (e: Throwable) {
            EngineState.Failed(e.message ?: e.toString())
        }
    }

    suspend fun awaitReady() {
        val s = _state.first { it !is EngineState.Loading }
        if (s is EngineState.Failed) throw IllegalStateException(s.message)
    }

    /** Adds options every yt-dlp call should have (cookies, UA niceties). */
    fun baseRequest(ctx: Context, url: String): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        InstagramSession.cookieFile(ctx)?.let { addOption("--cookies", it.absolutePath) }
        addOption("--no-mtime")
        addOption("--no-warnings")
    }

    suspend fun info(ctx: Context, url: String): MediaInfo = withContext(Dispatchers.IO) {
        awaitReady()
        val req = baseRequest(ctx, url).apply {
            addOption("-J")
            addOption("--flat-playlist")
            addOption("--no-playlist")
            addOption("--skip-download")
        }
        val resp = YoutubeDL.getInstance().execute(req, null, null)
        MediaInfo.fromJson(JSONObject(resp.out.trim().lines().last { it.startsWith("{") }), url)
    }

    suspend fun update(ctx: Context): String = withContext(Dispatchers.IO) {
        awaitReady()
        val status = YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE)
        val v = YoutubeDL.getInstance().versionName(ctx) ?: YoutubeDL.getInstance().version(ctx) ?: "?"
        _state.value = EngineState.Ready(v)
        "${status?.name ?: "DONE"}|$v"
    }

    fun ffmpegLibDirs(ctx: Context): String {
        val pk = File(ctx.noBackupFilesDir, "youtubedl-android/packages")
        return listOf(File(pk, "python/usr/lib"), File(pk, "ffmpeg/usr/lib"), File(ctx.applicationInfo.nativeLibraryDir))
            .joinToString(":") { it.absolutePath }
    }
}
