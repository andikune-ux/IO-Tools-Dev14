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
    fun classifiesVideoAndImageFormatsWithoutTreatingUnknownExtensionsAsPhotos() {
        assertTrue(InstagramDownloaderService.isVideoMediaFormat(null, "mp4"))
        assertTrue(InstagramDownloaderService.isVideoMediaFormat("h264", ""))
        assertFalse(InstagramDownloaderService.isVideoMediaFormat("none", "mp4"))
        assertTrue(InstagramDownloaderService.isImageMediaFormat("jpg"))
        assertTrue(InstagramDownloaderService.isImageMediaFormat("webp"))
        assertFalse(InstagramDownloaderService.isImageMediaFormat(""))
        assertFalse(InstagramDownloaderService.isImageMediaFormat("m3u8"))
    }

    @Test
    fun onlyAcceptsHttpsInstagramMediaCdnUrls() {
        assertTrue(InstagramDownloaderService.isAllowedMediaUrl("https://scontent.cdninstagram.com/media/video.mp4"))
        assertTrue(InstagramDownloaderService.isAllowedMediaUrl("https://video.xx.fbcdn.net/media/video.mp4"))
        assertFalse(InstagramDownloaderService.isAllowedMediaUrl("http://scontent.cdninstagram.com/media/video.mp4"))
        assertFalse(InstagramDownloaderService.isAllowedMediaUrl("https://cdninstagram.com.evil.example/video.mp4"))
        assertFalse(InstagramDownloaderService.isAllowedMediaUrl("https://evil.example/video.mp4"))
        assertFalse(InstagramDownloaderService.isAllowedMediaUrl("https://user:pass@scontent.cdninstagram.com/video.mp4"))
        assertFalse(InstagramDownloaderService.isAllowedMediaUrl("https://scontent..cdninstagram.com/video.mp4"))
        assertFalse(InstagramDownloaderService.isAllowedMediaUrl("https://scontent.cdninstagram.com:8443/video.mp4"))
        assertFalse(InstagramDownloaderService.isAllowedMediaUrl("https://127.0.0.1/video.mp4"))
    }
}
