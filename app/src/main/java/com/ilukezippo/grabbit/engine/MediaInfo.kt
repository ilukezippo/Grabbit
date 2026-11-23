package com.ilukezippo.grabbit.engine

import com.ilukezippo.grabbit.core.FormatSelector
import org.json.JSONObject

/** A single direct file to fetch (used for images and for Instagram/X API results). */
data class DirectItem(val url: String, val isVideo: Boolean, val thumb: String? = null)

data class MediaInfo(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val durationSec: Int,
    val uploader: String?,
    val site: String,
    val heights: List<Int>,
    val isLive: Boolean,
    val entryCount: Int,
    /** When non-empty, the media is fetched directly instead of through yt-dlp. */
    val direct: List<DirectItem> = emptyList(),
) {
    val isDirect get() = direct.isNotEmpty()
    val onlyImages get() = isDirect && direct.none { it.isVideo }

    companion object {
        fun fromJson(j: JSONObject, url: String): MediaInfo {
            val heights = mutableListOf<Int>()
            j.optJSONArray("formats")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val f = arr.getJSONObject(i)
                    if (f.optString("vcodec") != "none") heights += f.optInt("height", 0)
                }
            }
            if (heights.isEmpty()) heights += j.optInt("height", 0)
            val entries = j.optJSONArray("entries")
            var thumb = j.optString("thumbnail").ifBlank { null }
            if (thumb == null && entries != null && entries.length() > 0) {
                val e = entries.getJSONObject(0)
                thumb = e.optString("thumbnail").ifBlank { null }
                    ?: e.optJSONArray("thumbnails")?.let { t -> if (t.length() > 0) t.getJSONObject(t.length() - 1).optString("url") else null }
            }
            if (thumb == null) j.optJSONArray("thumbnails")?.let { t ->
                if (t.length() > 0) thumb = t.getJSONObject(t.length() - 1).optString("url")
            }
            return MediaInfo(
                url = j.optString("webpage_url").ifBlank { url },
                title = j.optString("title").ifBlank { j.optString("id", "video") },
                thumbnail = thumb,
                durationSec = j.optDouble("duration", 0.0).toInt(),
                uploader = j.optString("uploader").ifBlank { j.optString("channel").ifBlank { null } },
                site = j.optString("extractor_key").ifBlank { j.optString("extractor", "web") },
                heights = FormatSelector.heights(heights),
                isLive = j.optBoolean("is_live", false) || j.optString("live_status") == "is_live",
                entryCount = entries?.length() ?: 1,
            )
        }
    }
}
