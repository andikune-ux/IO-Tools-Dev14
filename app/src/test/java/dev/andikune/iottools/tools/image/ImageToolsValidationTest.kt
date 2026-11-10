package dev.andikune.iottools.tools.image
import dev.andikune.iottools.tools.image.validateResizeDimensions
import dev.andikune.iottools.tools.image.calculateImageSampleSize
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageToolsValidationTest {
    @Test
    fun boundsDecodedImageDimensionsAndPixelCount() {
        val squareSample = calculateImageSampleSize(8192, 8192)
        val panoramaSample = calculateImageSampleSize(12000, 2000)
        assertTrue(8192 / squareSample <= 4096)
        assertTrue(8192L / squareSample * (8192L / squareSample) <= 16_000_000L)
        assertTrue(12000 / panoramaSample <= 4096)
        assertTrue(2000L / panoramaSample * (12000L / panoramaSample) <= 16_000_000L)
    }

    @Test
    fun acceptsSafeResizeDimensions() {
        assertNull(validateResizeDimensions(1920, 1080))
        assertNull(validateResizeDimensions(1, 1))
    }

    @Test
    fun rejectsInvalidOrMemoryHeavyResizeDimensions() {
        assertNotNull(validateResizeDimensions(0, 100))
        assertNotNull(validateResizeDimensions(-1, 100))
        assertNotNull(validateResizeDimensions(9000, 100))
        assertNotNull(validateResizeDimensions(5000, 4000))
    }
}
