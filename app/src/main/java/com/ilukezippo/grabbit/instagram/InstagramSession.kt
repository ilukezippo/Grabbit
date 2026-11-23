package com.ilukezippo.grabbit.instagram

import android.content.Context
import android.webkit.CookieManager
import com.ilukezippo.grabbit.core.NetscapeCookies
import java.io.File

/** Instagram login cookies, kept only in the app's private storage. */
object InstagramSession {
    private fun headerFile(ctx: Context) = File(ctx.filesDir, "ig_cookie_header.txt")
    private fun netscapeFile(ctx: Context) = File(ctx.filesDir, "ig_cookies.txt")

    fun header(ctx: Context): String? = headerFile(ctx).takeIf { it.exists() }?.readText()

    fun isLoggedIn(ctx: Context) = NetscapeCookies.value(header(ctx), "sessionid") != null

    fun username(ctx: Context): String? = ctx.getSharedPreferences("ig", Context.MODE_PRIVATE).getString("user", null)

    fun cookieFile(ctx: Context): File? = netscapeFile(ctx).takeIf { it.exists() }

    fun save(ctx: Context, header: String) {
        headerFile(ctx).writeText(header)
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        netscapeFile(ctx).writeText(NetscapeCookies.fromHeader(".instagram.com", header, expiry))
    }

    fun saveUsername(ctx: Context, user: String?) {
        ctx.getSharedPreferences("ig", Context.MODE_PRIVATE).edit().putString("user", user).apply()
    }

    fun logout(ctx: Context) {
        headerFile(ctx).delete()
        netscapeFile(ctx).delete()
        saveUsername(ctx, null)
        CookieManager.getInstance().removeAllCookies(null)
    }
}
