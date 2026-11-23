package com.ilukezippo.grabbit.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ilukezippo.grabbit.BuildConfigInfo
import com.ilukezippo.grabbit.core.Versions
import com.ilukezippo.grabbit.engine.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

data class AppUpdate(val version: String, val apkName: String, val apkUrl: String, val pageUrl: String, val notes: String)

/** Checks GitHub releases for a newer Grabbit and installs it. */
object AppUpdater {
    private const val API = "https://api.github.com/repos/ilukezippo/Grabbit/releases/latest"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    private val _available = MutableStateFlow<AppUpdate?>(null)
    val available: StateFlow<AppUpdate?> = _available

    /** Returns the update if there is one. Without [force], asks GitHub at most every 6 hours. */
    suspend fun check(ctx: Context, force: Boolean = false): AppUpdate? = withContext(Dispatchers.IO) {
        val sp = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - sp.getLong("last", 0) < CHECK_EVERY_MS) {
            return@withContext _available.value ?: cached(ctx)
        }
        val j = JSONObject(Http.get(API, mapOf("Accept" to "application/vnd.github+json"), Http.DESKTOP_UA))
        sp.edit().putLong("last", now).putString("json", j.toString()).apply()
        parse(ctx, j).also { _available.value = it }
    }

    private fun cached(ctx: Context): AppUpdate? = runCatching {
        val s = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE).getString("json", null) ?: return null
        parse(ctx, JSONObject(s)).also { _available.value = it }
    }.getOrNull()

    private fun parse(ctx: Context, j: JSONObject): AppUpdate? {
        val tag = j.optString("tag_name")
        if (!Versions.isNewer(tag, BuildConfigInfo.versionName(ctx))) return null
        val assets = j.optJSONArray("assets") ?: return null
        val all = (0 until assets.length()).map { assets.getJSONObject(it) }
        val name = Versions.pickApk(all.map { it.optString("name") }, Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a") ?: return null
        val url = all.first { it.optString("name") == name }.optString("browser_download_url")
        return AppUpdate(tag.removePrefix("v"), name, url, j.optString("html_url"), j.optString("body").take(600))
    }

    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows Grabbit to install updates. */
    fun askInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Downloads the APK and hands it to Android's installer. */
    suspend fun downloadAndInstall(ctx: Context, u: AppUpdate, onProgress: (Float) -> Unit) {
        val apk = withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
            val out = File(dir, u.apkName)
            Http.client.newCall(Request.Builder().url(u.apkUrl).header("User-Agent", Http.MOBILE_UA).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val body = r.body ?: error("Empty download")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(128 * 1024); var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf); if (n < 0) break
                            o.write(buf, 0, n); done += n
                            if (total > 0) withContext(Dispatchers.Main) { onProgress(done * 100f / total) }
                        }
                    }
                }
            }
            out
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
