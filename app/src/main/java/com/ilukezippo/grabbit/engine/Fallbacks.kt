package com.ilukezippo.grabbit.engine

import com.ilukezippo.grabbit.core.LinkExtractor
import org.json.JSONObject

/** Image/video sources yt-dlp can't handle: X photo posts, direct file links, og:image/og:video pages. */
object Fallbacks {
    fun twitter(url: String): MediaInfo? {
        val id = LinkExtractor.tweetId(url) ?: return null
        val tweet = JSONObject(Http.get("https://api.fxtwitter.com/status/$id")).optJSONObject("tweet") ?: return null
        val all = tweet.optJSONObject("media")?.optJSONArray("all") ?: return null
        val items = (0 until all.length()).map { all.getJSONObject(it) }.map {
            val type = it.optString("type")
            DirectItem(it.optString("url"), isVideo = type != "photo", thumb = it.optString("thumbnail_url").ifBlank { it.optString("url") })
        }.filter { it.url.isNotBlank() }
        if (items.isEmpty()) return null
        val author = tweet.optJSONObject("author")?.optString("screen_name")
        return MediaInfo(url, tweet.optString("text").take(80).ifBlank { "X post $id" }, items.first().thumb, 0, author, "X", emptyList(), false, items.size, items)
    }

    fun directFile(url: String): MediaInfo? {
        val ct = Http.contentType(url)?.lowercase() ?: return null
        val isImg = ct.startsWith("image/")
        val isVid = ct.startsWith("video/") || ct.startsWith("audio/")
        if (!isImg && !isVid) return null
        val name = url.substringBefore('?').substringAfterLast('/').ifBlank { "file" }
        return MediaInfo(url, name, if (isImg) url else null, 0, null, "Direct", emptyList(), false, 1, listOf(DirectItem(url, isVid, if (isImg) url else null)))
    }

    private val metaRegex = Regex("""<meta[^>]+(?:property|name)=["'](og:video:secure_url|og:video:url|og:video|og:image:secure_url|og:image|twitter:image)["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val contentRegex = Regex("""content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val titleRegex = Regex("""<meta[^>]+property=["']og:title["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    fun openGraph(url: String): MediaInfo? {
        val html = runCatching { Http.get(url) }.getOrNull() ?: return null
        val found = linkedMapOf<String, Boolean>()
        metaRegex.findAll(html).forEach { m ->
            val content = contentRegex.find(m.value)?.groupValues?.get(1)?.replace("&amp;", "&") ?: return@forEach
            if (content.startsWith("http")) found.putIfAbsent(content, m.groupValues[1].contains("video", true))
        }
        if (found.isEmpty()) return null
        val vids = found.filterValues { it }.keys
        val items = if (vids.isNotEmpty()) listOf(DirectItem(vids.first(), true, found.filterValues { !it }.keys.firstOrNull()))
        else listOf(found.keys.first().let { DirectItem(it, false, it) })
        val title = titleRegex.find(html)?.groupValues?.get(1) ?: url.substringAfter("://").substringBefore('/')
        return MediaInfo(url, title.take(80), items.first().thumb, 0, null, "Web", emptyList(), false, 1, items)
    }
}
