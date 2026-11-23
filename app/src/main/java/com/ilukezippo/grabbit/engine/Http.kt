package com.ilukezippo.grabbit.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Http {
    const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap(), ua: String = MOBILE_UA): String {
        val req = Request.Builder().url(url).header("User-Agent", ua).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw HttpError(r.code)
            return r.body?.string() ?: ""
        }
    }

    /** Content type of a URL, or null if it can't be fetched. */
    fun contentType(url: String): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", MOBILE_UA).head().build()
        client.newCall(req).execute().use { it.header("Content-Type") }
    }.getOrNull()
}

class HttpError(val code: Int) : Exception("HTTP $code")
