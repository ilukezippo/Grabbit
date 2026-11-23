package com.ilukezippo.grabbit.instagram

import android.content.Context
import com.ilukezippo.grabbit.core.IgTarget
import com.ilukezippo.grabbit.core.InstagramIds
import com.ilukezippo.grabbit.engine.DirectItem
import com.ilukezippo.grabbit.engine.MediaInfo
import org.json.JSONObject

class IgNoStories(val user: String) : Exception("No active stories for @$user")

/** Every way of reading a story failed; [steps] says what Instagram answered at each. */
class IgStepsError(val steps: List<String>) : Exception(steps.joinToString(" · "))

/** Uses the signed-in web session to read posts, carousels (photos + videos) and stories. */
class InstagramClient(private val ctx: Context) {
    private suspend fun api(path: String) = InstagramWeb.api(ctx, path)

    suspend fun resolve(target: IgTarget, url: String): MediaInfo = when (target) {
        is IgTarget.Post -> post(target.shortcode, url)
        is IgTarget.Story -> story(target.username, target.mediaId, url)
    }

    private suspend fun post(shortcode: String, url: String): MediaInfo {
        val item = api("media/${InstagramIds.shortcodeToMediaId(shortcode)}/info/").getJSONArray("items").getJSONObject(0)
        val carousel = item.optJSONArray("carousel_media")
        val items = if (carousel != null) (0 until carousel.length()).map { toDirect(carousel.getJSONObject(it)) } else listOf(toDirect(item))
        val user = item.optJSONObject("user")?.optString("username")
        val caption = item.optJSONObject("caption")?.optString("text")?.lines()?.firstOrNull()?.take(70)
        return MediaInfo(url, caption?.ifBlank { null } ?: "${user ?: "instagram"}_$shortcode", items.first().thumb,
            item.optDouble("video_duration", 0.0).toInt(), user, "Instagram", emptyList(), false, items.size, items)
    }

    private suspend fun story(username: String, mediaId: String?, url: String): MediaInfo {
        val steps = mutableListOf<String>()
        fun note(step: String, e: Throwable) {
            steps += "$step: " + ((e as? InstagramWeb.IgHttpError)?.code?.let { "HTTP $it" } ?: e.message?.take(60) ?: e.javaClass.simpleName)
        }
        // 1. A link to one story item: ask for that item directly.
        if (mediaId != null) {
            try {
                val items = api("media/$mediaId/info/").getJSONArray("items")
                val list = (0 until items.length()).map { toDirect(items.getJSONObject(it)) }
                if (list.isNotEmpty()) return storyInfo(username, url, list)
            } catch (e: Throwable) { note("item", e) }
        }
        // 2. The user's whole current story: user id, then their reel.
        val userId = try {
            api("users/web_profile_info/?username=$username").getJSONObject("data").getJSONObject("user").getString("id")
        } catch (e: Throwable) {
            note("profile", e)
            try {
                val html = InstagramWeb.page(ctx, "stories/$username/")
                Regex(""""(?:profile_id|owner_id|user_id)"\s*:\s*"(\d+)"""").find(html)?.groupValues?.get(1)
                    ?: Regex(""""user"\s*:\s*\{[^{}]*?"pk"\s*:\s*"?(\d+)""").find(html)?.groupValues?.get(1)
                    ?: error("no user id in page")
            } catch (e2: Throwable) { note("page", e2); throw IgStepsError(steps) }
        }
        val reels = try { api("feed/reels_media/?reel_ids=$userId") } catch (e: Throwable) { note("reel", e); throw IgStepsError(steps) }
        val reel = reels.optJSONObject("reels")?.optJSONObject(userId) ?: reels.optJSONArray("reels_media")?.optJSONObject(0)
            ?: throw IgNoStories(username)
        val arr = reel.getJSONArray("items")
        var list = (0 until arr.length()).map { arr.getJSONObject(it) }
        if (mediaId != null) list = list.filter { it.optString("pk") == mediaId || it.optString("id").startsWith(mediaId) }.ifEmpty { list }
        val items = list.map { toDirect(it) }
        if (items.isEmpty()) throw IgNoStories(username)
        return storyInfo(username, url, items)
    }

    private fun storyInfo(username: String, url: String, items: List<DirectItem>) =
        MediaInfo(url, "${username}_story", items.first().thumb, 0, username, "Instagram Story", emptyList(), false, items.size, items)

    private fun toDirect(m: JSONObject): DirectItem {
        val img = m.optJSONObject("image_versions2")?.optJSONArray("candidates")?.optJSONObject(0)?.optString("url")
        val vid = m.optJSONArray("video_versions")?.optJSONObject(0)?.optString("url")
        return if (!vid.isNullOrBlank()) DirectItem(vid, true, img) else DirectItem(img ?: error("no media"), false, img)
    }
}
