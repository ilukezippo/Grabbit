package com.ilukezippo.grabbit.engine

import android.content.Context
import com.ilukezippo.grabbit.R
import com.ilukezippo.grabbit.core.InstagramIds
import com.ilukezippo.grabbit.core.IgTarget
import com.ilukezippo.grabbit.instagram.InstagramClient
import com.ilukezippo.grabbit.instagram.InstagramSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResolveException(message: String) : Exception(message)

/** Turns any link into something downloadable, trying the best source for each site. */
object Resolver {
    suspend fun resolve(ctx: Context, url: String): MediaInfo = withContext(Dispatchers.IO) {
        val ig = InstagramIds.parse(url)
        val loggedIn = InstagramSession.isLoggedIn(ctx)
        if (ig != null && loggedIn) {
            runCatching { InstagramClient(ctx).resolve(ig, url) }.getOrNull()?.let { return@withContext it }
        }
        if (ig is IgTarget.Story && !loggedIn) throw ResolveException(ctx.getString(R.string.err_ig_login_stories))

        val ytError = try {
            return@withContext Engine.info(ctx, url)
        } catch (e: Throwable) { e }

        runCatching { Fallbacks.twitter(url) }.getOrNull()?.let { return@withContext it }
        runCatching { Fallbacks.directFile(url) }.getOrNull()?.let { return@withContext it }
        if (ig == null) runCatching { Fallbacks.openGraph(url) }.getOrNull()?.let { return@withContext it }

        if (ig != null && !loggedIn) throw ResolveException(ctx.getString(R.string.err_ig_login))
        throw ResolveException(cleanError(ytError.message ?: ctx.getString(R.string.err_unsupported)))
    }

    private fun cleanError(msg: String): String =
        msg.lines().firstOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()?.take(200)
            ?: msg.lines().first().take(200)
}
