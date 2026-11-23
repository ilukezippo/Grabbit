package com.ilukezippo.grabbit.core

import java.math.BigInteger

sealed interface IgTarget {
    data class Post(val shortcode: String) : IgTarget
    data class Story(val username: String, val mediaId: String?) : IgTarget
}

object InstagramIds {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val postRegex = Regex("""instagram\.com/(?:[^/?#]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val storyRegex = Regex("""instagram\.com/stories/([^/?#]+)(?:/(\d+))?""", RegexOption.IGNORE_CASE)

    fun parse(url: String): IgTarget? {
        storyRegex.find(url)?.let { m ->
            if (m.groupValues[1] != "highlights") return IgTarget.Story(m.groupValues[1], m.groupValues[2].ifEmpty { null })
        }
        postRegex.find(url)?.let { return IgTarget.Post(it.groupValues[1]) }
        return null
    }

    /** Private-post shortcodes carry a suffix after the first 11 chars; only the prefix encodes the id. */
    fun shortcodeToMediaId(shortcode: String): String {
        var id = BigInteger.ZERO
        val base = BigInteger.valueOf(64)
        shortcode.take(11).forEach { c -> id = id.multiply(base).add(BigInteger.valueOf(ALPHABET.indexOf(c).toLong())) }
        return id.toString()
    }
}
