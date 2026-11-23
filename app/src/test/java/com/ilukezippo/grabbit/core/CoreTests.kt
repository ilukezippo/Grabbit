package com.ilukezippo.grabbit.core

import org.junit.Assert.*
import org.junit.Test

class LinkExtractorTest {
    @Test fun extractsUrlFromSharedText() {
        assertEquals(
            "https://www.instagram.com/reel/Cx1AbC/?igsh=abc",
            LinkExtractor.extract("Check this out! https://www.instagram.com/reel/Cx1AbC/?igsh=abc")
        )
    }
    @Test fun stripsTrailingPunctuation() {
        assertEquals("https://youtu.be/dQw4w9WgXcQ", LinkExtractor.extract("(see https://youtu.be/dQw4w9WgXcQ)."))
    }
    @Test fun returnsNullWithoutUrl() {
        assertNull(LinkExtractor.extract("no links here"))
    }
    @Test fun tweetId() {
        assertEquals("1790000000000000001", LinkExtractor.tweetId("https://x.com/user/status/1790000000000000001?s=20"))
        assertEquals("42", LinkExtractor.tweetId("https://twitter.com/a/status/42"))
        assertNull(LinkExtractor.tweetId("https://youtube.com/watch?v=1"))
    }
}

class FormatSelectorTest {
    @Test fun videoWithHeightMp4() {
        val args = FormatSelector.args(Choice.Video(720, VideoContainer.MP4))
        assertEquals("bv*[height<=720]+ba/b[height<=720]/bv*+ba/b", args[args.indexOf("-f") + 1])
        assertEquals("mp4", args[args.indexOf("--merge-output-format") + 1])
        assertEquals("mp4", args[args.indexOf("--remux-video") + 1])
        assertTrue(args.contains("-S"))
    }
    @Test fun bestVideoHasNoHeightFilter() {
        val args = FormatSelector.args(Choice.Video(null, VideoContainer.MKV))
        assertEquals("bv*+ba/b", args[args.indexOf("-f") + 1])
        assertEquals("mkv", args[args.indexOf("--merge-output-format") + 1])
    }
    @Test fun audioMp3() {
        val args = FormatSelector.args(Choice.Audio(AudioFormat.MP3))
        assertEquals(listOf("-f", "ba/b", "-x", "--audio-format", "mp3", "--audio-quality", "0"), args)
    }
    @Test fun heightsSortedDistinctPositive() {
        assertEquals(listOf(2160, 1080, 720, 360), FormatSelector.heights(listOf(360, 0, 1080, 720, 1080, 2160, -1)))
    }
}

class NetscapeCookiesTest {
    @Test fun convertsHeader() {
        val txt = NetscapeCookies.fromHeader(".instagram.com", "sessionid=abc%3A1; csrftoken=xyz", 2000000000L)
        val lines = txt.trim().lines()
        assertEquals("# Netscape HTTP Cookie File", lines[0])
        assertEquals(".instagram.com\tTRUE\t/\tTRUE\t2000000000\tsessionid\tabc%3A1", lines[1])
        assertEquals(".instagram.com\tTRUE\t/\tTRUE\t2000000000\tcsrftoken\txyz", lines[2])
    }
    @Test fun valueLookup() {
        assertEquals("xyz", NetscapeCookies.value("a=1; csrftoken=xyz", "csrftoken"))
        assertNull(NetscapeCookies.value("a=1", "sessionid"))
        assertNull(NetscapeCookies.value(null, "sessionid"))
    }
}

class InstagramIdsTest {
    @Test fun shortcodeMath() {
        assertEquals("1", InstagramIds.shortcodeToMediaId("B"))
        assertEquals("64", InstagramIds.shortcodeToMediaId("BA"))
        assertEquals("4095", InstagramIds.shortcodeToMediaId("__"))
    }
    @Test fun parsesPostsReelsStories() {
        assertEquals(IgTarget.Post("Cx1AbC"), InstagramIds.parse("https://www.instagram.com/p/Cx1AbC/?img_index=1"))
        assertEquals(IgTarget.Post("Cx1AbC"), InstagramIds.parse("https://instagram.com/reel/Cx1AbC"))
        assertEquals(IgTarget.Post("Cx1AbC"), InstagramIds.parse("https://www.instagram.com/someone/p/Cx1AbC/"))
        assertEquals(IgTarget.Story("nasa", "3456"), InstagramIds.parse("https://www.instagram.com/stories/nasa/3456/?utm=1"))
        assertEquals(IgTarget.Story("nasa", null), InstagramIds.parse("https://www.instagram.com/stories/nasa/"))
        assertNull(InstagramIds.parse("https://www.youtube.com/watch?v=1"))
    }
}

class FileNamesTest {
    @Test fun sanitizes() {
        assertEquals("a_b_c_ d", FileNames.sanitize("a/b:c?  d"))
        assertEquals("مرحبا بالعالم", FileNames.sanitize("مرحبا بالعالم"))
        assertEquals("grabbit", FileNames.sanitize("  "))
        assertEquals(80, FileNames.sanitize("x".repeat(200)).length)
    }
}

class VersionsTest {
    @Test fun comparesVersions() {
        assertTrue(Versions.isNewer("v1.0.2", "1.0.1"))
        assertTrue(Versions.isNewer("1.1", "1.0.9"))
        assertTrue(Versions.isNewer("2.0.0", "1.9.9"))
        assertFalse(Versions.isNewer("v1.0.1", "1.0.1"))
        assertFalse(Versions.isNewer("1.0.0", "1.0.1"))
        assertFalse(Versions.isNewer("1.0", "1.0.0"))
        assertFalse(Versions.isNewer("garbage", "1.0.1"))
    }
    @Test fun picksApkForAbi() {
        val names = listOf("Grabbit-1.0.2-armv7.apk", "Grabbit-1.0.2-arm64.apk", "source.zip")
        assertEquals("Grabbit-1.0.2-arm64.apk", Versions.pickApk(names, "arm64-v8a"))
        assertEquals("Grabbit-1.0.2-armv7.apk", Versions.pickApk(names, "armeabi-v7a"))
        assertEquals("only.apk", Versions.pickApk(listOf("only.apk"), "armeabi-v7a"))
        assertNull(Versions.pickApk(listOf("notes.txt"), "arm64-v8a"))
    }
}

class DefaultChoiceTest {
    private val hs = listOf(2160, 1080, 720, 360)
    @Test fun picksSavedHeightOrNextLower() {
        assertEquals(Choice.Video(1080, VideoContainer.MP4), DefaultChoice.pick(false, 1080, VideoContainer.MP4, AudioFormat.MP3, hs))
        assertEquals(Choice.Video(720, VideoContainer.MKV), DefaultChoice.pick(false, 900, VideoContainer.MKV, AudioFormat.MP3, hs))
        assertEquals(Choice.Video(360, VideoContainer.MP4), DefaultChoice.pick(false, 240, VideoContainer.MP4, AudioFormat.MP3, hs))
    }
    @Test fun bestWhenZeroOrUnknownHeights() {
        assertEquals(Choice.Video(null, VideoContainer.MP4), DefaultChoice.pick(false, 0, VideoContainer.MP4, AudioFormat.MP3, hs))
        assertEquals(Choice.Video(null, VideoContainer.WEBM), DefaultChoice.pick(false, 1080, VideoContainer.WEBM, AudioFormat.MP3, emptyList()))
    }
    @Test fun audioMode() {
        assertEquals(Choice.Audio(AudioFormat.OPUS), DefaultChoice.pick(true, 1080, VideoContainer.MP4, AudioFormat.OPUS, hs))
    }
    @Test fun labels() {
        assertEquals("1080p MP4", DefaultChoice.label(Choice.Video(1080, VideoContainer.MP4)))
        assertEquals("4K MKV", DefaultChoice.label(Choice.Video(2160, VideoContainer.MKV)))
        assertEquals("Best MP4", DefaultChoice.label(Choice.Video(null, VideoContainer.MP4)))
        assertEquals("MP3", DefaultChoice.label(Choice.Audio(AudioFormat.MP3)))
    }
}

class SinglePagePdfTest {
    @Test fun writesAValidLookingPdf() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val pdf = String(SinglePagePdf.withJpeg(jpeg, 640, 480), Charsets.ISO_8859_1)
        assertTrue(pdf.startsWith("%PDF-1.4"))
        assertTrue(pdf.contains("/MediaBox [0 0 640 480]"))
        assertTrue(pdf.contains("/Filter /DCTDecode /Length 7"))
        assertTrue(pdf.trimEnd().endsWith("%%EOF"))
        // every xref offset must point at "N 0 obj"
        val xref = pdf.substring(pdf.indexOf("xref\n"))
        val offsets = Regex("""(\d{10}) 00000 n""").findAll(xref).map { it.groupValues[1].toInt() }.toList()
        assertEquals(5, offsets.size)
        offsets.forEachIndexed { i, off -> assertTrue(pdf.startsWith("${i + 1} 0 obj", off)) }
        val start = Regex("""startxref\n(\d+)""").find(pdf)!!.groupValues[1].toInt()
        assertTrue(pdf.startsWith("xref", start))
    }
}

class SpeedArgsTest {
    @Test fun fastUsesAria2ForPlainFilesAndParallelFragments() {
        val a = SpeedArgs.args(fast = true, aria2Ready = true, live = false)
        assertEquals("8", a[a.indexOf("-N") + 1])
        assertTrue(a.containsAll(listOf("--downloader", "dash,m3u8:native", "libaria2c.so")))
        assertTrue(a.any { it.startsWith("aria2c:") && "-x 8" in it && "-s 8" in it })
    }
    @Test fun fastWithoutAria2StillSplitsFragments() {
        val a = SpeedArgs.args(fast = true, aria2Ready = false, live = false)
        assertEquals(listOf("-N", "8", "--http-chunk-size", "10M"), a)
    }
    @Test fun offOrLiveAddsNothing() {
        assertTrue(SpeedArgs.args(fast = false, aria2Ready = true, live = false).isEmpty())
        assertTrue(SpeedArgs.args(fast = true, aria2Ready = true, live = true).isEmpty())
    }
}
