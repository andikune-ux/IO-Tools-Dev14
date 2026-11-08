package dev.andikune.iottools

import dev.andikune.iottools.tools.youtube.YouTubeDownloaderService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlValidationTest {
    @Test
    fun acceptsSupportedYouTubeUrlForms() {
        assertEquals("abcdefghijk", YouTubeDownloaderService.extractVideoId("https://www.youtube.com/watch?v=abcdefghijk"))
        assertEquals("abcdefghijk", YouTubeDownloaderService.extractVideoId("https://youtu.be/abcdefghijk"))
        assertEquals("abcdefghijk", YouTubeDownloaderService.extractVideoId("https://m.youtube.com/shorts/abcdefghijk"))
        assertEquals("abcdefghijk", YouTubeDownloaderService.extractVideoId("abcdefghijk"))
    }

    @Test
    fun rejectsUntrustedHostsAndMalformedIds() {
        assertNull(YouTubeDownloaderService.extractVideoId("https://youtube.com.evil.example/watch?v=abcdefghijk"))
        assertNull(YouTubeDownloaderService.extractVideoId("https://evil.example/watch?v=abcdefghijk"))
        assertNull(YouTubeDownloaderService.extractVideoId("ftp://youtube.com/watch?v=abcdefghijk"))
        assertNull(YouTubeDownloaderService.extractVideoId("https://youtu.be/short"))
    }
}
