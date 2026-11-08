package dev.andikune.iottools.tools.common
import dev.andikune.iottools.tools.common.AppDownloadManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadManagerValidationTest {
    @Test
    fun acceptsOnlySecurePublicDownloadUrls() {
        assertTrue(AppDownloadManager.isSecureDownloadUrl("https://cdn.example.com/video.mp4"))
        assertFalse(AppDownloadManager.isSecureDownloadUrl("http://cdn.example.com/video.mp4"))
        assertFalse(AppDownloadManager.isSecureDownloadUrl("file:///sdcard/video.mp4"))
        assertFalse(AppDownloadManager.isSecureDownloadUrl("https://user:pass@cdn.example.com/video.mp4"))
        assertFalse(AppDownloadManager.isSecureDownloadUrl("https://localhost/video.mp4"))
        assertFalse(AppDownloadManager.isSecureDownloadUrl("https://localhost./video.mp4"))
        assertFalse(AppDownloadManager.isSecureDownloadUrl("https://127.1/video.mp4"))
        assertFalse(AppDownloadManager.isSecureDownloadUrl("https://192.168.1.1/video.mp4"))
        assertFalse(AppDownloadManager.isSecureDownloadUrl("https://cdn.home.arpa/video.mp4"))
    }

    @Test
    fun rejectsUnexpectedContentTypesForRequestedMedia() {
        assertTrue(AppDownloadManager.isCompatibleContentType("video/mp4", "video/mp4"))
        assertFalse(AppDownloadManager.isCompatibleContentType("video/mp4", "video/webm"))
        assertFalse(AppDownloadManager.isCompatibleContentType("video/mp4", "video/quicktime"))
        assertTrue(AppDownloadManager.isCompatibleContentType("video/mp4", "video/mp4; codecs=avc1"))
        assertTrue(AppDownloadManager.isCompatibleContentType("video/mp4", "application/octet-stream"))
        assertFalse(AppDownloadManager.isCompatibleContentType("video/mp4", "application/json"))
        assertFalse(AppDownloadManager.isCompatibleContentType("image/jpeg", "text/html"))
    }

    @Test
    fun alignsOutputExtensionWithExpectedMimeType() {
        assertTrue(AppDownloadManager.ensureExtension("clip.webm", "video/mp4").endsWith(".mp4"))
        assertTrue(AppDownloadManager.ensureExtension("clip.mp4", "video/webm").endsWith(".webm"))
        assertTrue(AppDownloadManager.ensureExtension("clip.mp4", "application/octet-stream").endsWith(".mp4"))
    }

    @Test
    fun validatesCommonFileSignatures() {
        val mp4Header = byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        val webmHeader = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val htmlHeader = "<!doctype html>".toByteArray()

        assertTrue(AppDownloadManager.matchesFileSignature("video/mp4", mp4Header))
        assertTrue(AppDownloadManager.matchesFileSignature("video/webm", webmHeader))
        assertTrue(AppDownloadManager.matchesFileSignature("image/jpeg", jpegHeader))
        assertFalse(AppDownloadManager.matchesFileSignature("video/mp4", htmlHeader))
        assertFalse(AppDownloadManager.matchesFileSignature("image/jpeg", htmlHeader))
        assertTrue(AppDownloadManager.matchesFileSignature("application/octet-stream", byteArrayOf(1, 2, 3)))
        assertFalse(AppDownloadManager.matchesFileSignature("video/mp4", byteArrayOf()))
    }
}
