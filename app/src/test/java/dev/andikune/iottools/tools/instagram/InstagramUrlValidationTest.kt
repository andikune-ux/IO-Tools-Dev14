package dev.andikune.iottools.tools.instagram
import dev.andikune.iottools.tools.instagram.InstagramDownloaderService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramUrlValidationTest {
    @Test
    fun extractsShortcodeFromSupportedPublicUrlForms() {
        assertEquals("ABC_123", InstagramDownloaderService.extractShortcode("https://www.instagram.com/p/ABC_123/"))
        assertEquals("ABC-123", InstagramDownloaderService.extractShortcode("https://instagram.com/reel/ABC-123/?igsh=abc"))
        assertEquals("ABC123", InstagramDownloaderService.extractShortcode("https://m.instagram.com/tv/ABC123/"))
    }

    @Test
    fun rejectsUntrustedInstagramHostsAndMalformedUrls() {
        assertNull(InstagramDownloaderService.extractShortcode("https://instagram.com.evil.example/p/ABC123/"))
        assertNull(InstagramDownloaderService.extractShortcode("https://evil.example/p/ABC123/"))
        assertNull(InstagramDownloaderService.extractShortcode("ftp://instagram.com/p/ABC123/"))
        assertNull(InstagramDownloaderService.extractShortcode("https://instagram.com/accounts/login/"))
    }

    @Test
    fun onlyAcceptsHttpsInstagramMediaCdnUrls() {
        assertTrue(InstagramDownloaderService.isAllowedMediaUrl("https://scontent.cdninstagram.com/media/video.mp4"))
        assertTrue(InstagramDownloaderService.isAllowedMediaUrl("https://video.xx.fbcdn.net/media/video.mp4"))
        assertFalse(InstagramDownloaderService.isAllowedMediaUrl("http://scontent.cdninstagram.com/media/video.mp4"))
        assertFalse(InstagramDownloaderService.isAllowedMediaUrl("https://cdninstagram.com.evil.example/video.mp4"))
        assertFalse(InstagramDownloaderService.isAllowedMediaUrl("https://evil.example/video.mp4"))
    }
}
