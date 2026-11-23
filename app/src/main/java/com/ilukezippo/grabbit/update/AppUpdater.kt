package com.ilukezippo.grabbit.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.ilukezippo.grabbit.BuildConfigInfo
import com.ilukezippo.grabbit.core.Versions
import com.ilukezippo.grabbit.engine.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

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

    /** Opens the APK link in the browser, which downloads it; tapping the download installs it.
     *  (No "install other apps" permission: some phones refuse to sideload apps that request it.) */
    fun openDownload(ctx: Context, u: AppUpdate) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
