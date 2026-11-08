package dev.andikune.iottools

import dev.andikune.iottools.tools.instagram.InstagramDownloaderService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramExtractorValidationTest {
    @Test
    fun normalizesSupportedInstagramPostAndReelUrls() {
        assertEquals(
            "https://www.instagram.com/reel/ABC123/",
            InstagramDownloaderService.normalizeInstagramUrl("https://www.instagram.com/reels/ABC123/?igsh=token")
        )
        assertEquals(
            "https://www.instagram.com/p/ABC123/",
            InstagramDownloaderService.normalizeInstagramUrl("https://instagr.am/p/ABC123/")
        )
        assertEquals("ABC123", InstagramDownloaderService.extractShortcode("https://www.instagram.com/p/ABC123/"))
    }

    @Test
    fun rejectsUntrustedInstagramPageHosts() {
        assertEquals(null, InstagramDownloaderService.normalizeInstagramUrl("https://instagram.com.evil.example/p/ABC123/"))
        assertEquals(null, InstagramDownloaderService.normalizeInstagramUrl("ftp://www.instagram.com/p/ABC123/"))
        assertEquals(null, InstagramDownloaderService.normalizeInstagramUrl("https://user:pass@www.instagram.com/p/ABC123/"))
    }

    @Test
    fun onlyAcceptsHttpsInstagramMediaCdnHosts() {
        assertTrue(InstagramDownloaderService.isAllowedMediaUrl("https://scontent.cdninstagram.com/v/t51/video.mp4"))
        assertTrue(InstagramDownloaderService.isAllowedMediaUrl("https://scontent-lax3-1.fbcdn.net/photo.jpg"))
        assertFalse(InstagramDownloaderService.isAllowedMediaUrl("http://scontent.cdninstagram.com/video.mp4"))
        assertFalse(InstagramDownloaderService.isAllowedMediaUrl("https://cdninstagram.com.evil.example/video.mp4"))
    }

    @Test
    fun classifiesImageFormatsWithoutMistakingMissingCodecForVideo() {
        assertFalse(InstagramDownloaderService.isVideoMediaFormat(null, "jpg"))
        assertFalse(InstagramDownloaderService.isVideoMediaFormat("none", "webp"))
        assertTrue(InstagramDownloaderService.isVideoMediaFormat("h264", "mp4"))
        assertTrue(InstagramDownloaderService.isVideoMediaFormat(null, "mp4"))
    }
}
