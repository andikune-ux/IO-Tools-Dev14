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
            "https://user:pass@cdn.example.com/video.mp4"
        ).forEach { assertFalse(it, VideoDownloadHelper.isSecurePublicMediaUrl(it)) }
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
            "::ffff:127.0.0.1"
        ).forEach { assertFalse(it, VideoDownloadHelper.isPublicAddress(InetAddress.getByName(it))) }
        assertTrue(VideoDownloadHelper.isPublicAddress(InetAddress.getByName("1.1.1.1")))
        assertTrue(VideoDownloadHelper.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
}
