package dev.andikune.iottools.tools.ucbrowser
import dev.andikune.iottools.tools.ucbrowser.UcMediaSnifferService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
        assertNull(UcMediaSnifferService.normalizeTargetUrl("https://localhost./watch"))
        assertNull(UcMediaSnifferService.normalizeTargetUrl("https://127.1/watch"))
        assertNull(UcMediaSnifferService.normalizeTargetUrl("https://2130706433/watch"))
        assertNull(UcMediaSnifferService.normalizeTargetUrl("https://0x7f000001/watch"))
        assertNull(UcMediaSnifferService.normalizeTargetUrl("https://printer.home.arpa/watch"))
        assertNull(UcMediaSnifferService.normalizeTargetUrl("https://example.com:70000/watch"))
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
    fun detectsExtensionlessMediaFromResponseMimeType() {
        val mp4 = UcMediaSnifferService.classifyMediaUrl(
            "https://cdn.example.com/session/stream?id=123",
            "https://player.example.com/watch",
            "video/mp4"
        )
        val hls = UcMediaSnifferService.classifyMediaUrl(
            "https://cdn.example.com/session/master?token=abc",
            "https://player.example.com/watch",
            "application/vnd.apple.mpegurl"
        )
        val dash = UcMediaSnifferService.classifyMediaUrl(
            "https://cdn.example.com/session/manifest?token=abc",
            "https://player.example.com/watch",
            "application/dash+xml"
        )

        assertEquals("mp4", mp4?.fileExtension)
        assertTrue(hls?.isStreamHls == true)
        assertTrue(dash?.isDash == true)
        assertNull(
            UcMediaSnifferService.classifyMediaUrl(
                "https://cdn.example.com/session/data",
                contentType = "application/json"
            )
        )
    }

    @Test
    fun stripsSensitiveRefererDetailsBeforePassingHeadersToDownloaders() {
        assertEquals(
            "https://player.example.com/",
            UcMediaSnifferService.sanitizeReferer(
                "https://cdn.example.net/video.mp4",
                "https://player.example.com/watch?token=private#fragment"
            )
        )
        assertEquals(
            "",
            UcMediaSnifferService.sanitizeReferer(
                "https://cdn.example.net/video.mp4",
                "https://user:secret@player.example.com/watch?token=private"
            )
        )
        assertEquals(
            "https://cdn.example.com/watch",
            UcMediaSnifferService.sanitizeReferer(
                "https://cdn.example.com/video.mp4",
                "https://cdn.example.com/watch?token=private"
            )
        )
        assertEquals(
            "",
            UcMediaSnifferService.sanitizeReferer(
                "https://cdn.example.com/video.mp4",
                "http://player.example.com/watch"
            )
        )
    }

    @Test
    fun readsHtmlWithZeroByteReadsAndRejectsOversizedResponses() {
        val source = "small-html".toByteArray()
        val input = object : java.io.InputStream() {
            private val delegate = java.io.ByteArrayInputStream(source)
            private var returnZero = true
            override fun read(): Int = delegate.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (returnZero) {
                    returnZero = false
                    return 0
                }
                return delegate.read(buffer, offset, length)
            }
        }
        assertTrue(UcMediaSnifferService.readLimited(input, 64).contentEquals(source))
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            UcMediaSnifferService.readLimited(java.io.ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3)
        }
    }

    @Test
    fun rejectsBlobUnsupportedAndTrackingUrls() {
        assertNull(UcMediaSnifferService.classifyMediaUrl("blob:https://example.com/1234"))
        assertNull(UcMediaSnifferService.classifyMediaUrl("https://cdn.example.com/image.jpg"))
        assertNull(UcMediaSnifferService.classifyMediaUrl("https://ads.example.com/analytics/video.mp4"))
        assertNull(UcMediaSnifferService.classifyMediaUrl("javascript:alert(1)"))
        assertNull(UcMediaSnifferService.classifyMediaUrl("http://127.0.0.1/private.mp4"))
    }
}
