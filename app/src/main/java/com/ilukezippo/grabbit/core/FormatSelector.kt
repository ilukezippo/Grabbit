package com.ilukezippo.grabbit.core

enum class VideoContainer(val ext: String) { MP4("mp4"), MKV("mkv"), WEBM("webm") }
enum class AudioFormat(val ext: String) { MP3("mp3"), M4A("m4a"), OPUS("opus") }

sealed interface Choice {
    /** height == null means best available. */
    data class Video(val height: Int?, val container: VideoContainer) : Choice
    data class Audio(val format: AudioFormat) : Choice
}

object FormatSelector {
    fun args(choice: Choice): List<String> = when (choice) {
        is Choice.Audio -> listOf("-f", "ba/b", "-x", "--audio-format", choice.format.ext, "--audio-quality", "0")
        is Choice.Video -> {
            val h = choice.height
            val f = if (h == null) "bv*+ba/b" else "bv*[height<=$h]+ba/b[height<=$h]/bv*+ba/b"
            val sort = when (choice.container) {
                VideoContainer.MP4 -> "vcodec:h264,acodec:m4a"
                VideoContainer.WEBM -> "vcodec:vp9,acodec:opus"
                VideoContainer.MKV -> "res"
            }
            listOf(
                "-f", f,
                "-S", sort,
                "--merge-output-format", choice.container.ext,
                "--remux-video", choice.container.ext,
            )
        }
    }

    fun heights(raw: List<Int>): List<Int> = raw.filter { it > 0 }.distinct().sortedDescending()
}
