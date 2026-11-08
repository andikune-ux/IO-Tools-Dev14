package dev.andikune.iottools.tools.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreSaverValidationTest {
    @Test
    fun sanitizesPathSeparatorsAndControlCharacters() {
        val name = MediaStoreSaver.sanitizeFileName("../folder\\video?.mp4")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains('?'))
        assertFalse(name.any { it.isISOControl() })
    }

    @Test
    fun boundsOutputFileNamesAndUsesFallbackForEmptyNames() {
        assertTrue(MediaStoreSaver.sanitizeFileName("x".repeat(1000)).length <= 180)
        assertTrue(MediaStoreSaver.sanitizeFileName("\\u0000\\r\\n").isNotBlank())
    }
}
