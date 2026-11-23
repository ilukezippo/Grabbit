package com.ilukezippo.grabbit.instagram

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ilukezippo.grabbit.R
import com.ilukezippo.grabbit.core.NetscapeCookies

/** Plain Instagram login page; once a session cookie appears it is stored locally and the screen closes. */
class InstagramLoginActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.ig_login_title)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        val web = WebView(this)
        cm.setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString = web.settings.userAgentString.replace("; wv", "")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) = check()
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) = check()
        }
        setContentView(web)
        web.loadUrl("https://www.instagram.com/accounts/login/")
    }

    private var done = false
    private fun check() {
        if (done) return
        val header = CookieManager.getInstance().getCookie("https://www.instagram.com") ?: return
        if (NetscapeCookies.value(header, "sessionid") == null) return
        done = true
        CookieManager.getInstance().flush()
        InstagramSession.save(this, header)
        InstagramSession.saveUsername(this, NetscapeCookies.value(header, "ds_user_id")?.let { "id $it" })
        Toast.makeText(this, R.string.ig_login_ok, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }
}
