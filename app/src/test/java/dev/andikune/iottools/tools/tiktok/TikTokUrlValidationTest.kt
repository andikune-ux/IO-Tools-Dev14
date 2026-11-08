package dev.andikune.iottools.tools.tiktok
import dev.andikune.iottools.tools.tiktok.TikTokDownloaderService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TikTokUrlValidationTest {
    @Test
    fun acceptsTikTokAndDouyinPageUrls() {
        assertEquals(
            "https://www.tiktok.com/@creator/video/123456789",
            TikTokDownloaderService.normalizeTikTokUrl("https://www.tiktok.com/@creator/video/123456789")
        )
        assertTrue(TikTokDownloaderService.normalizeTikTokUrl("https://vt.tiktok.com/shortcode/") != null)
        assertTrue(TikTokDownloaderService.normalizeTikTokUrl("https://vm.tiktok.com/shortcode/") != null)
        assertTrue(TikTokDownloaderService.normalizeTikTokUrl("https://m.tiktok.com/@creator/video/123456789") != null)
        assertTrue(TikTokDownloaderService.normalizeTikTokUrl("https://www.douyin.com/video/123456789") != null)
    }

    @Test
    fun rejectsUntrustedHostsSchemesAndCredentials() {
        assertNull(TikTokDownloaderService.normalizeTikTokUrl("https://tiktok.com.evil.example/@creator/video/123"))
        assertNull(TikTokDownloaderService.normalizeTikTokUrl("https://evil.example/@creator/video/123"))
        assertNull(TikTokDownloaderService.normalizeTikTokUrl("ftp://www.tiktok.com/@creator/video/123"))
        assertNull(TikTokDownloaderService.normalizeTikTokUrl("https://user:pass@www.tiktok.com/@creator/video/123"))
        assertNull(TikTokDownloaderService.normalizeTikTokUrl("https://www.tiktok.com:444/@creator/video/123"))
        assertNull(TikTokDownloaderService.normalizeTikTokUrl("https://evil.tiktok.com/@creator/video/123"))
        assertNull(TikTokDownloaderService.normalizeTikTokUrl("https://sub.evil.douyin.com/video/123"))
    }

    @Test
    fun buildsSafeAudioVideoFormatSelectors() {
        assertEquals(
            "play_addr+bestaudio/play_addr",
            TikTokDownloaderService.buildVideoFormatSelector("play_addr")
        )
        assertEquals(
            "muxed_format",
            TikTokDownloaderService.buildVideoFormatSelector("muxed_format", hasAudio = true)
        )
        assertNull(TikTokDownloaderService.buildVideoFormatSelector("bad/id"))
        assertNull(TikTokDownloaderService.buildVideoFormatSelector("format;best"))
    }

    @Test
    fun onlyAcceptsHttpsTikTokMediaCdnHosts() {
        assertTrue(TikTokDownloaderService.isAllowedMediaUrl("https://v16-webapp.tiktokcdn.com/video.mp4"))
        assertTrue(TikTokDownloaderService.isAllowedMediaUrl("https://p16-sign-va.tiktokcdn-us.com/video.mp4"))
        assertFalse(TikTokDownloaderService.isAllowedMediaUrl("http://v16-webapp.tiktokcdn.com/video.mp4"))
        assertFalse(TikTokDownloaderService.isAllowedMediaUrl("https://tiktokcdn.com.evil.example/video.mp4"))
        assertFalse(TikTokDownloaderService.isAllowedMediaUrl("https://evil.example/video.mp4"))
        assertFalse(TikTokDownloaderService.isAllowedMediaUrl("https://v16-webapp.tiktokcdn.com:444/video.mp4"))
        assertFalse(TikTokDownloaderService.isAllowedMediaUrl("https://v16..tiktokcdn.com/video.mp4"))
        assertFalse(TikTokDownloaderService.isAllowedMediaUrl("https://127.0.0.1/video.mp4"))
        assertFalse(TikTokDownloaderService.isAllowedMediaUrl("https://user:pass@v16-webapp.tiktokcdn.com/video.mp4"))
    }
}
