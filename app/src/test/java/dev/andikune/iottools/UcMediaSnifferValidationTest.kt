package dev.andikune.iottools

import dev.andikune.iottools.tools.ucbrowser.UcMediaSnifferService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UcMediaSnifferValidationTest {
    @Test
    fun normalizesOnlyHttpAndHttpsPublicPageUrls() {
        assertEquals("https://example.com/watch", UcMediaSnifferService.normalizeTargetUrl("example.com/watch"))
        assertEquals("http://example.com/watch", UcMediaSnifferService.normalizeTargetUrl("http://example.com/watch"))
        assertNull(UcMediaSnifferService.normalizeTargetUrl("javascript:alert(1)"))
        assertNull(UcMediaSnifferService.normalizeTargetUrl("https://user:pass@example.com/watch"))
        assertNull(UcMediaSnifferService.normalizeTargetUrl("https://localhost/watch"))
        assertNull(UcMediaSnifferService.normalizeTargetUrl("https://media.local/watch"))
        assertNull(UcMediaSnifferService.normalizeTargetUrl("https://127.0.0.1/watch"))
        assertNull(UcMediaSnifferService.normalizeTargetUrl("https://192.168.1.1/watch"))
    }

    @Test
    fun classifiesProgressiveAndAdaptiveMediaUrls() {
        val mp4 = UcMediaSnifferService.classifyMediaUrl("https://cdn.example.com/video.mp4?token=abc")
        val hls = UcMediaSnifferService.classifyMediaUrl("https://cdn.example.com/master.m3u8")
        val dash = UcMediaSnifferService.classifyMediaUrl("https://cdn.example.com/manifest.mpd")

        assertEquals("mp4", mp4?.fileExtension)
        assertFalse(mp4?.isStreamHls ?: true)
        assertTrue(hls?.isStreamHls == true)
        assertTrue(dash?.isDash == true)
    }

    @Test
    fun rejectsBlobUnsupportedAndTrackingUrls() {
        assertNull(UcMediaSnifferService.classifyMediaUrl("blob:https://example.com/1234"))
        assertNull(UcMediaSnifferService.classifyMediaUrl("https://cdn.example.com/image.jpg"))
        assertNull(UcMediaSnifferService.classifyMediaUrl("https://ads.example.com/analytics/video.mp4"))
        assertNull(UcMediaSnifferService.classifyMediaUrl("javascript:alert(1)"))
    }
}
