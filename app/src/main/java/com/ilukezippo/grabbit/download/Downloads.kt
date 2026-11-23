package com.ilukezippo.grabbit.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ilukezippo.grabbit.GrabbitApp
import com.ilukezippo.grabbit.core.Choice
import com.ilukezippo.grabbit.core.FileNames
import com.ilukezippo.grabbit.core.FormatSelector
import com.ilukezippo.grabbit.engine.Engine
import com.ilukezippo.grabbit.engine.FFmpegRunner
import com.ilukezippo.grabbit.engine.Http
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** The download queue: runs up to two jobs at once and keeps a history of finished ones. */
object Downloads {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks
    private val jobs = ConcurrentHashMap<String, Job>()
    private val stopAndSave = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(2)

    fun enqueue(ctx: Context, spec: DownloadSpec): String {
        val task = DownloadTask(UUID.randomUUID().toString().take(8), spec)
        _tasks.update { listOf(task) + it }
        ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        val app = ctx.applicationContext as GrabbitApp
        jobs[task.id] = app.scope.launch { gate.withPermit { run(app, task.id) } }
        return task.id
    }

    /** For live recordings this stops and keeps what was captured; otherwise it cancels. */
    fun stop(id: String) {
        val t = find(id) ?: return
        if (t.spec.isLive && t.status == Status.RUNNING) stopAndSave += id
        YoutubeDL.getInstance().destroyProcessById(id)
        if (!t.spec.isLive || t.status != Status.RUNNING) {
            jobs[id]?.cancel()
            set(id) { it.copy(status = Status.CANCELED) }
        }
    }

    fun remove(ctx: Context, id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
        saveHistory(ctx)
    }

    fun clearFinished(ctx: Context) {
        _tasks.update { list -> list.filter { it.active } }
        saveHistory(ctx)
    }

    fun retry(ctx: Context, id: String) {
        val t = find(id) ?: return
        remove(ctx, id)
        enqueue(ctx, t.spec)
    }

    fun addExternal(ctx: Context, title: String, file: SavedFile) {
        val t = DownloadTask(UUID.randomUUID().toString().take(8), DownloadSpec(file.uri, title, null, null),
            status = Status.DONE, progress = 100f, files = listOf(file))
        _tasks.update { listOf(t) + it }
        saveHistory(ctx)
    }

    private fun find(id: String) = _tasks.value.firstOrNull { it.id == id }
    private fun set(id: String, f: (DownloadTask) -> DownloadTask) =
        _tasks.update { list -> list.map { if (it.id == id) f(it) else it } }

    private suspend fun run(app: GrabbitApp, id: String) {
        val task = find(id) ?: return
        if (task.status == Status.CANCELED) return
        set(id) { it.copy(status = Status.RUNNING, progress = -1f) }
        val tmp = File(app.cacheDir, "dl/$id").apply { deleteRecursively(); mkdirs() }
        try {
            val produced = if (task.spec.direct.isNotEmpty()) fetchDirect(task, tmp) else fetchWithYtDlp(app, task, tmp)
            val saved = produced.map { MediaSaver.save(app, it) }
            if (saved.isEmpty()) error("Nothing was downloaded")
            set(id) { it.copy(status = Status.DONE, progress = 100f, files = saved, detail = "") }
        } catch (e: Throwable) {
            val stoppedLive = id in stopAndSave
            if (stoppedLive) {
                runCatching {
                    val saved = finishLive(app, tmp).map { MediaSaver.save(app, it) }
                    set(id) { it.copy(status = if (saved.isEmpty()) Status.CANCELED else Status.DONE, files = saved, progress = 100f) }
                }.onFailure { err -> set(id) { it.copy(status = Status.FAILED, error = err.message) } }
            } else if (find(id)?.status != Status.CANCELED) {
                val msg = (e.message ?: e.toString()).lines().firstOrNull { "ERROR" in it }?.substringAfter("ERROR:")?.trim()
                    ?: e.message?.lines()?.firstOrNull() ?: e.toString()
                set(id) { it.copy(status = Status.FAILED, error = msg.take(240)) }
            }
        } finally {
            stopAndSave -= id
            jobs -= id
            tmp.deleteRecursively()
            saveHistory(app)
        }
    }

    private suspend fun fetchWithYtDlp(app: GrabbitApp, task: DownloadTask, tmp: File): List<File> {
        Engine.awaitReady()
        val spec = task.spec
        val req = Engine.baseRequest(app, spec.url).apply {
            addOption("-o", File(tmp, "%(title).70B [%(id)s].%(ext)s").absolutePath)
            addOption("--no-playlist")
            addOption("--embed-metadata")
            if (spec.isLive) {
                val h = (spec.choice as? Choice.Video)?.height
                addOption("-f", if (h != null) "b[height<=$h]/bv*[height<=$h]+ba/b" else "b/bv*+ba")
                addOption("--hls-use-mpegts")
                addOption("--no-part")
            } else {
                val args = FormatSelector.args(spec.choice ?: Choice.Video(null, com.ilukezippo.grabbit.core.VideoContainer.MP4))
                var i = 0
                while (i < args.size) {
                    val k = args[i]
                    val v = args.getOrNull(i + 1)
                    if (v != null && !v.startsWith("-")) { addOption(k, v); i += 2 } else { addOption(k); i++ }
                }
            }
        }
        YoutubeDL.getInstance().execute(req, task.id) { progress, eta, line ->
            set(task.id) { it.copy(progress = progress, etaSec = eta, detail = line.takeLast(90)) }
        }
        return if (spec.isLive) finishLive(app, tmp) else outputs(tmp)
    }

    private fun outputs(tmp: File) = tmp.listFiles().orEmpty()
        .filter { it.isFile && it.length() > 0 && it.extension !in setOf("part", "ytdl", "temp", "json") && !it.name.contains(".part") }

    /** Live captures are MPEG-TS; remux to MP4 so phones' galleries can play them, keep .ts if that fails. */
    private suspend fun finishLive(app: GrabbitApp, tmp: File): List<File> = tmp.listFiles().orEmpty()
        .filter { it.isFile && it.length() > 0 }
        .map { src ->
            val base = FileNames.sanitize(src.nameWithoutExtension.substringBefore(".part"))
            val out = File(tmp, "out_$base.mp4")
            runCatching { FFmpegRunner.run(app, FFmpegRunner.remuxArgs(src, out)) }
                .fold({ File(tmp, "$base.mp4").also { out.renameTo(it) } }, { File(tmp, "$base.ts").also { src.renameTo(it) } })
        }

    private suspend fun fetchDirect(task: DownloadTask, tmp: File): List<File> {
        val items = task.spec.direct
        val base = FileNames.sanitize(task.spec.title, 60)
        return items.mapIndexed { i, item ->
            coroutineContext.ensureActive()
            val req = Request.Builder().url(item.url).header("User-Agent", Http.MOBILE_UA).build()
            Http.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val body = r.body ?: error("Empty response")
                val ext = extFor(r.header("Content-Type"), item.url, item.isVideo)
                val name = if (items.size > 1) "$base (${i + 1}).$ext" else "$base.$ext"
                val f = File(tmp, name)
                val total = body.contentLength()
                body.byteStream().use { input ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            val part = if (total > 0) done.toFloat() / total else 0f
                            set(task.id) { it.copy(progress = (i + part) / items.size * 100f, detail = "${i + 1}/${items.size}") }
                        }
                    }
                }
                f
            }
        }
    }

    private fun extFor(ct: String?, url: String, isVideo: Boolean): String {
        val c = ct?.lowercase() ?: ""
        return when {
            "jpeg" in c || "jpg" in c -> "jpg"
            "png" in c -> "png"
            "webp" in c -> "webp"
            "heic" in c -> "heic"
            "gif" in c -> "gif"
            "mp4" in c -> "mp4"
            "webm" in c -> "webm"
            "quicktime" in c -> "mov"
            else -> url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 } ?: if (isVideo) "mp4" else "jpg"
        }
    }

    // ---- history persistence (finished items only) ----
    private fun historyFile(ctx: Context) = File(ctx.filesDir, "history.json")

    fun loadHistory(ctx: Context) {
        val f = historyFile(ctx)
        if (!f.exists()) return
        runCatching {
            val arr = JSONArray(f.readText())
            val list = (0 until arr.length()).map { arr.getJSONObject(it) }.map { j ->
                val files = j.getJSONArray("files").let { fa -> (0 until fa.length()).map { fa.getJSONObject(it) } }
                    .map { SavedFile(it.getString("uri"), it.getString("name"), it.getString("mime"), it.optLong("size")) }
                DownloadTask(
                    id = j.getString("id"),
                    spec = DownloadSpec(j.getString("url"), j.getString("title"), j.optString("thumb").ifBlank { null }, null),
                    status = Status.valueOf(j.getString("status")),
                    progress = 100f, error = j.optString("error").ifBlank { null }, files = files, createdAt = j.optLong("at"),
                )
            }
            _tasks.value = list
        }
    }

    private fun saveHistory(ctx: Context) {
        val arr = JSONArray()
        _tasks.value.filter { !it.active }.take(300).forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id); put("url", t.spec.url); put("title", t.spec.title); put("thumb", t.spec.thumbnail ?: "")
                put("status", t.status.name); put("error", t.error ?: ""); put("at", t.createdAt)
                put("files", JSONArray().apply { t.files.forEach { f -> put(JSONObject().apply { put("uri", f.uri); put("name", f.name); put("mime", f.mime); put("size", f.size) }) } })
            })
        }
        runCatching { historyFile(ctx).writeText(arr.toString()) }
    }
}

