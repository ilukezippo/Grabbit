package com.ilukezippo.grabbit.instagram

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Calls Instagram's web API from inside a hidden WebView: the same browser, cookies and
 * user agent the user signed in with. Instagram refuses these calls from plain HTTP clients
 * (yt-dlp on a phone, OkHttp) even with valid cookies.
 */
object InstagramWeb {
    private const val ORIGIN = "https://www.instagram.com/"
    private val main = Handler(Looper.getMainLooper())
    private val lock = Mutex()
    private var web: WebView? = null
    private var ready: CompletableDeferred<Unit>? = null
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Pair<Int, String>>>()
    private val ids = AtomicInteger()

    class IgHttpError(val code: Int, body: String) : Exception("Instagram answered $code: ${body.take(160)}")

    private class Bridge {
        @JavascriptInterface fun done(id: Int, status: Int, body: String) { pending.remove(id)?.complete(status to body) }
        @JavascriptInterface fun fail(id: Int, message: String) { pending.remove(id)?.completeExceptionally(IllegalStateException(message)) }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun ensure(ctx: Context): CompletableDeferred<Unit> {
        ready?.let { if (!it.isCancelled) return it }
        val d = CompletableDeferred<Unit>()
        ready = d
        main.post {
            val w = web ?: WebView(ctx.applicationContext).also { v ->
                v.settings.javaScriptEnabled = true
                v.settings.domStorageEnabled = true
                v.settings.userAgentString = v.settings.userAgentString.replace("; wv", "")
                CookieManager.getInstance().setAcceptThirdPartyCookies(v, true)
                v.addJavascriptInterface(Bridge(), "GrabbitIg")
                web = v
            }
            w.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { if (url.startsWith(ORIGIN)) d.complete(Unit) }
            }
            // robots.txt: tiny, same origin, so fetch() carries the session cookies.
            w.loadUrl(ORIGIN + "robots.txt")
        }
        return d
    }

    /** GET any www.instagram.com URL as the signed-in browser; returns (status, body). */
    suspend fun fetch(ctx: Context, fullUrl: String, apiHeaders: Boolean): Pair<Int, String> = lock.withLock {
        try { withTimeout(30_000) { ensure(ctx).await() } } catch (e: Exception) { ready = null; throw e }
        val id = ids.incrementAndGet()
        val result = CompletableDeferred<Pair<Int, String>>()
        pending[id] = result
        val url = JSONObject.quote(fullUrl)
        val headers = if (apiHeaders) """{'X-IG-App-ID':'936619743392459','X-ASBD-ID':'359341','X-IG-WWW-Claim':'0',
                'X-Requested-With':'XMLHttpRequest','X-CSRFToken': m ? m[1] : '', 'Accept':'*/*'}""" else "{}"
        val js = """
            (function(){
              var m = document.cookie.match(/(?:^|; )csrftoken=([^;]+)/);
              fetch($url, {credentials:'include', headers:$headers})
              .then(function(r){ return r.text().then(function(t){ GrabbitIg.done($id, r.status, t); }); })
              .catch(function(e){ GrabbitIg.fail($id, String(e)); });
            })();
        """.trimIndent()
        withContext(Dispatchers.Main) { web?.evaluateJavascript(js, null) }
        try { withTimeout(30_000) { result.await() } } finally { pending.remove(id) }
    }

    /** GET https://www.instagram.com/api/v1/[path] as the signed-in browser. */
    suspend fun api(ctx: Context, path: String): JSONObject {
        val (status, body) = fetch(ctx, ORIGIN + "api/v1/" + path, apiHeaders = true)
        if (status !in 200..299 || !body.trimStart().startsWith("{")) throw IgHttpError(status, body)
        return JSONObject(body)
    }

    /** An Instagram web page's HTML, as the signed-in browser sees it. */
    suspend fun page(ctx: Context, path: String): String {
        val (status, body) = fetch(ctx, ORIGIN + path.removePrefix("/"), apiHeaders = false)
        if (status !in 200..299) throw IgHttpError(status, body)
        return body
    }

    /** Copies the browser's current Instagram cookies into the files yt-dlp uses. */
    fun syncCookies(ctx: Context) {
        val header = CookieManager.getInstance().getCookie("https://www.instagram.com") ?: return
        if ("sessionid=" in header) InstagramSession.save(ctx, header)
    }
}
