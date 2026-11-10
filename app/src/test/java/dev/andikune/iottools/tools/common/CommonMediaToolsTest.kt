package dev.andikune.iottools.tools.common
import dev.andikune.iottools.tools.common.MediaStoreSaver
import dev.andikune.iottools.tools.common.VideoDownloadHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonMediaToolsTest {
    @Test
    fun sanitizesOutputNamesAndFallbacks() {
        assertEquals("_evil_name_.mp4", MediaStoreSaver.sanitizeFileName("../evil\nname?.mp4"))
        assertEquals("_safe.txt", MediaStoreSaver.sanitizeFileName("...", "../safe.txt"))
    }

    @Test
    fun streamProbeAcceptsOnlyPublicHttpsMediaUrls() {
        assertTrue(VideoDownloadHelper.isSecurePublicMediaUrl("https://cdn.example.com/video.mp4"))
        assertFalse(VideoDownloadHelper.isSecurePublicMediaUrl("http://cdn.example.com/video.mp4"))
        assertFalse(VideoDownloadHelper.isSecurePublicMediaUrl("https://localhost/video.mp4"))
        assertFalse(VideoDownloadHelper.isSecurePublicMediaUrl("https://192.168.1.10/video.mp4"))
        assertFalse(VideoDownloadHelper.isSecurePublicMediaUrl("https://user:pass@cdn.example.com/video.mp4"))
    }
}
