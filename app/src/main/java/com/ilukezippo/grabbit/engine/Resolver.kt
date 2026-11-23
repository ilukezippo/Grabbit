package com.ilukezippo.grabbit.engine

import android.content.Context
import com.ilukezippo.grabbit.R
import com.ilukezippo.grabbit.core.InstagramIds
import com.ilukezippo.grabbit.core.IgTarget
import com.ilukezippo.grabbit.instagram.InstagramClient
import com.ilukezippo.grabbit.instagram.IgNoStories
import com.ilukezippo.grabbit.instagram.IgStepsError
import com.ilukezippo.grabbit.instagram.InstagramSession
import com.ilukezippo.grabbit.instagram.InstagramWeb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResolveException(message: String) : Exception(message)

/** Turns any link into something downloadable, trying the best source for each site. */
object Resolver {
    suspend fun resolve(ctx: Context, url: String): MediaInfo = withContext(Dispatchers.IO) {
        val ig = InstagramIds.parse(url)
        val loggedIn = InstagramSession.isLoggedIn(ctx)
        var igError: Throwable? = null
        if (ig != null && loggedIn) {
            try {
                return@withContext InstagramClient(ctx).resolve(ig, url)
            } catch (e: Throwable) {
                igError = e
            }
            // Stories only exist behind the sign-in; yt-dlp can't do better than the browser did.
            if (ig is IgTarget.Story) throw ResolveException(igMessage(ctx, igError, ig.username))
            withContext(Dispatchers.Main) { InstagramWeb.syncCookies(ctx) }
        }
        if (ig is IgTarget.Story && !loggedIn) throw ResolveException(ctx.getString(R.string.err_ig_login_stories))

        val ytError = try {
            return@withContext Engine.info(ctx, url)
        } catch (e: Throwable) { e }

        runCatching { Fallbacks.twitter(url) }.getOrNull()?.let { return@withContext it }
        runCatching { Fallbacks.directFile(url) }.getOrNull()?.let { return@withContext it }
        if (ig == null) runCatching { Fallbacks.openGraph(url) }.getOrNull()?.let { return@withContext it }

        if (ig != null && !loggedIn) throw ResolveException(ctx.getString(R.string.err_ig_login))
        if (ig != null && igError != null) throw ResolveException(igMessage(ctx, igError, null))
        throw ResolveException(cleanError(ytError.message ?: ctx.getString(R.string.err_unsupported)))
    }

    private fun igMessage(ctx: Context, e: Throwable?, user: String?): String = when {
        e is IgStepsError -> ctx.getString(R.string.err_ig_story_steps, e.steps.joinToString(" · "))
        e is IgNoStories -> ctx.getString(R.string.err_ig_no_story, e.user)
        e is InstagramWeb.IgHttpError && e.code == 429 -> ctx.getString(R.string.err_ig_busy)
        e is InstagramWeb.IgHttpError && (e.code == 401 || e.code == 403 || "login_required" in (e.message ?: "")) ->
            ctx.getString(R.string.err_ig_session)
        e is InstagramWeb.IgHttpError && e.code == 404 -> ctx.getString(R.string.err_ig_gone) + "\n(post: HTTP 404)"
        else -> ctx.getString(R.string.err_ig_failed, (e?.message ?: "").take(160))
    }

    private fun cleanError(msg: String): String =
        msg.lines().firstOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()?.take(200)
            ?: msg.lines().first().take(200)
}
