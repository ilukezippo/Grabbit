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
