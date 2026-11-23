package com.ilukezippo.grabbit.core

/** The download choice made from the user's saved defaults. */
object DefaultChoice {
    /** [height] 0 = best; otherwise the saved height if offered, else the next lower one, else the lowest. */
    fun pick(audio: Boolean, height: Int, container: VideoContainer, audioFormat: AudioFormat, heights: List<Int>): Choice {
        if (audio) return Choice.Audio(audioFormat)
        if (height == 0 || heights.isEmpty()) return Choice.Video(null, container)
        return Choice.Video(heights.firstOrNull { it <= height } ?: heights.last(), container)
    }

    fun label(c: Choice): String = when (c) {
        is Choice.Audio -> c.format.name
        is Choice.Video -> (c.height?.let { h -> if (h >= 2160) "4K" else if (h >= 1440) "2K" else "${h}p" } ?: "Best") + " " + c.container.name
    }
}
