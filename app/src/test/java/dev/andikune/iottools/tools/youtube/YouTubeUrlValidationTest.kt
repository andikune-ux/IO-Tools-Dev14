package dev.andikune.iottools.tools.youtube
import dev.andikune.iottools.tools.youtube.YouTubeDownloaderService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun buildsSafeAudioVideoFormatSelectors() {
        assertEquals("muxed_22", YouTubeDownloaderService.buildFormatSelector("muxed_22", hasAudio = true))
        assertEquals("137+bestaudio/137", YouTubeDownloaderService.buildFormatSelector("137", hasAudio = false))
        assertNull(YouTubeDownloaderService.buildFormatSelector("137+140", hasAudio = false))
        assertNull(YouTubeDownloaderService.buildFormatSelector("bestvideo;best", hasAudio = false))
    }

    @Test
    fun onlyOffersFormatsWithKnownMediaStoreMimeMappings() {
        assertTrue(YouTubeDownloaderService.isSupportedFormatExtension("mp4", audioOnly = false))
        assertTrue(YouTubeDownloaderService.isSupportedFormatExtension("webm", audioOnly = false))
        assertFalse(YouTubeDownloaderService.isSupportedFormatExtension("3gp", audioOnly = false))
        assertTrue(YouTubeDownloaderService.isSupportedFormatExtension("m4a", audioOnly = true))
        assertTrue(YouTubeDownloaderService.isSupportedFormatExtension("webm", audioOnly = true))
        assertFalse(YouTubeDownloaderService.isSupportedFormatExtension("unknown", audioOnly = true))
    }

    @Test
    fun rejectsUntrustedHostsAndMalformedIds() {
        assertNull(YouTubeDownloaderService.extractVideoId("https://youtube.com.evil.example/watch?v=abcdefghijk"))
        assertNull(YouTubeDownloaderService.extractVideoId("https://evil.youtube.com/watch?v=abcdefghijk"))
        assertNull(YouTubeDownloaderService.extractVideoId("https://user:pass@www.youtube.com/watch?v=abcdefghijk"))
        assertNull(YouTubeDownloaderService.extractVideoId("https://www.youtube.com:444/watch?v=abcdefghijk"))
        assertNull(YouTubeDownloaderService.extractVideoId("https://evil.example/watch?v=abcdefghijk"))
        assertNull(YouTubeDownloaderService.extractVideoId("ftp://youtube.com/watch?v=abcdefghijk"))
        assertNull(YouTubeDownloaderService.extractVideoId("https://youtu.be/short"))
    }
}
