package com.ilukezippo.grabbit.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/** Runs the bundled ffmpeg binary directly, for converting files on the phone. */
object FFmpegRunner {
    private val timeRegex = Regex("""time=(\d+):(\d+):(\d+(?:\.\d+)?)""")
    private val durRegex = Regex("""Duration: (\d+):(\d+):(\d+(?:\.\d+)?)""")

    private fun secs(m: MatchResult) = m.groupValues[1].toDouble() * 3600 + m.groupValues[2].toDouble() * 60 + m.groupValues[3].toDouble()

    suspend fun run(ctx: Context, args: List<String>, onProgress: (Float) -> Unit = {}): Unit = withContext(Dispatchers.IO) {
        Engine.awaitReady()
        val bin = File(ctx.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val pb = ProcessBuilder(listOf(bin.absolutePath, "-hide_banner", "-y") + args).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = Engine.ffmpegLibDirs(ctx)
        pb.environment()["TMPDIR"] = ctx.cacheDir.absolutePath
        val p = pb.start()
        var total = 0.0
        val tail = ArrayDeque<String>()
        try {
            val reader = p.inputStream.bufferedReader()
            val buf = StringBuilder()
            while (true) {
                coroutineContext.ensureActive()
                val c = reader.read()
                if (c == -1) break
                if (c == '\r'.code || c == '\n'.code) {
                    val line = buf.toString(); buf.clear()
                    if (line.isBlank()) continue
                    tail.addLast(line); if (tail.size > 8) tail.removeFirst()
                    if (total == 0.0) durRegex.find(line)?.let { total = secs(it) }
                    timeRegex.find(line)?.let { if (total > 0) onProgress((secs(it) / total * 100).toFloat().coerceIn(0f, 100f)) }
                } else buf.append(c.toChar())
            }
            val code = p.waitFor()
            if (code != 0) throw IllegalStateException(tail.joinToString("\n").takeLast(300))
        } finally {
            p.destroy()
        }
    }

    fun convertArgs(input: File, output: File): List<String> {
        val base = listOf("-i", input.absolutePath)
        val tail = when (output.extension.lowercase()) {
            "mp3" -> listOf("-vn", "-c:a", "libmp3lame", "-q:a", "0")
            "m4a" -> listOf("-vn", "-c:a", "aac", "-b:a", "256k")
            "opus" -> listOf("-vn", "-c:a", "libopus", "-b:a", "160k")
            "mp4" -> listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", "22", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart")
            "mkv" -> listOf("-c", "copy")
            "webm" -> listOf("-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "32", "-deadline", "realtime", "-cpu-used", "8", "-c:a", "libopus")
            "gif" -> listOf("-vf", "fps=12,scale=480:-1:flags=lanczos", "-loop", "0")
            "jpg", "png", "webp" -> listOf("-frames:v", "1")
            else -> emptyList()
        }
        return base + tail + output.absolutePath
    }

    fun remuxArgs(input: File, output: File) = listOf("-i", input.absolutePath, "-c", "copy", "-movflags", "+faststart", output.absolutePath)
}
