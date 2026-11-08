package dev.andikune.iottools

import dev.andikune.iottools.tools.image.validateResizeDimensions
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImageToolsValidationTest {
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
