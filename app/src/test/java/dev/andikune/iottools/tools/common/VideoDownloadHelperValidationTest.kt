package dev.andikune.iottools.tools.common

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDownloadHelperValidationTest {
    @Test
    fun acceptsOnlyHttpsPublicMediaHostnames() {
        assertTrue(VideoDownloadHelper.isSecurePublicMediaUrl("https://cdn.example.com/video.mp4"))
        listOf(
            "http://cdn.example.com/video.mp4",
            "https://localhost/video.mp4",
            "https://localhost./video.mp4",
            "https://127.1/video.mp4",
            "https://192.168.1.1/video.mp4",
            "https://[::1]/video.mp4",
            "https://cdn.local/video.mp4",
            "https://cdn.home.arpa/video.mp4",
            "https://user:pass@cdn.example.com/video.mp4",
            "https://cdn.example.com:70000/video.mp4",
            "https://cdn..example.com/video.mp4",
            "https://cdn.example.com./video.mp4", 
            "https://2130706433/video.mp4",
            "https://0x7f000001/video.mp4"
        ).forEach { assertFalse(it, VideoDownloadHelper.isSecurePublicMediaUrl(it)) }
    }

    @Test
    fun acceptsOnlyKnownMediaSignatures() {
        val mp4 = byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        val webm = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        val hls = "#EXTM3U\n#EXT-X-VERSION:3".toByteArray()
        val dash = "<?xml version=\"1.0\"><MPD></MPD>".toByteArray()
        val unknown = "not actually a video".toByteArray()

        assertTrue(VideoDownloadHelper.isValidMediaSignature("video/mp4", mp4))
        assertTrue(VideoDownloadHelper.isValidMediaSignature("video/webm", webm))
        assertTrue(VideoDownloadHelper.isValidMediaSignature("application/vnd.apple.mpegurl", hls))
        assertTrue(VideoDownloadHelper.isValidMediaSignature("application/dash+xml", dash))
        assertFalse(VideoDownloadHelper.isValidMediaSignature("video/x-msvideo", unknown))
        assertFalse(VideoDownloadHelper.isValidMediaSignature("application/octet-stream", unknown))
        assertFalse(VideoDownloadHelper.isValidMediaSignature("video/mp4", "<html>".toByteArray()))
    }

    @Test
    fun rejectsPrivateAndReservedDnsAnswers() {
        listOf(
            "10.0.0.1",
            "172.16.0.1",
            "192.168.0.1",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.1.1",
            "fc00::1",
            "fe80::1",
            "2001:db8::1",
            "::ffff:127.0.0.1",
            "::127.0.0.1",
            "2001:db8::1",
            "2001::1"
        ).forEach { assertFalse(it, VideoDownloadHelper.isPublicAddress(InetAddress.getByName(it))) }
        assertTrue(VideoDownloadHelper.isPublicAddress(InetAddress.getByName("1.1.1.1")))
        assertTrue(VideoDownloadHelper.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
}
