package com.ilukezippo.grabbit.core

object LinkExtractor {
    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val tweetRegex = Regex("""(?:twitter\.com|x\.com)/[^/]+/status(?:es)?/(\d+)""", RegexOption.IGNORE_CASE)

    /** First http(s) URL inside arbitrary shared text, without trailing punctuation. */
    fun extract(text: String?): String? {
        val raw = urlRegex.find(text ?: return null)?.value ?: return null
        return raw.trimEnd('.', ',', ')', ']', '!', '?', ';', ':', '»', '"')
    }

    fun tweetId(url: String): String? = tweetRegex.find(url)?.groupValues?.get(1)
}
