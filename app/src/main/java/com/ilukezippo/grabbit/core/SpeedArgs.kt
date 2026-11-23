package com.ilukezippo.grabbit.core

/**
 * yt-dlp options that make downloads faster.
 * - aria2c opens 8 connections per file: YouTube and others limit the speed of each connection.
 * - -N 8 fetches 8 pieces at once for streams cut into pieces (HLS/DASH: Instagram, TikTok, Twitch…).
 * - Without aria2c, 10 MB chunks still avoid YouTube's per-connection slowdown.
 * Live recordings get none of this: they must be written in order as they arrive.
 */
object SpeedArgs {
    fun args(fast: Boolean, aria2Ready: Boolean, live: Boolean): List<String> {
        if (!fast || live) return emptyList()
        if (!aria2Ready) return listOf("-N", "8", "--http-chunk-size", "10M")
        return listOf(
            "-N", "8",
            "--downloader", "dash,m3u8:native",
            "--downloader", "libaria2c.so",
            "--external-downloader-args", "aria2c:-x 8 -s 8 -k 1M --file-allocation=none",
        )
    }
}
