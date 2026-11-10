package dev.andikune.iottools.tools.pdf
import dev.andikune.iottools.tools.pdf.PdfEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PdfEngineValidationTest {
    @Test
    fun parsesPageRangesInDocumentOrder() {
        assertEquals(listOf(0..2, 4..4), PdfEngine.parseRanges("1-3, 5", 8))
        assertEquals(listOf(0..0, 1..1, 2..2), PdfEngine.parseRanges("", 3))
    }

    @Test
    fun rejectsInvalidPageRanges() {
        assertNotNull(runCatching { PdfEngine.parseRanges("0-2", 8) }.exceptionOrNull())
        assertNotNull(runCatching { PdfEngine.parseRanges("5-2", 8) }.exceptionOrNull())
        assertNotNull(runCatching { PdfEngine.parseRanges("1-9", 8) }.exceptionOrNull())
        assertNotNull(runCatching { PdfEngine.parseRanges("abc", 8) }.exceptionOrNull())
    }

    @Test
    fun boundsPdfSplitRangeAllocation() {
        assertNotNull(runCatching { PdfEngine.parseRanges("", 201) }.exceptionOrNull())
        assertNotNull(runCatching { PdfEngine.parseRanges("1-5001", 6000) }.exceptionOrNull())
        assertNotNull(runCatching { PdfEngine.parseRanges("1,".repeat(1100), 2000) }.exceptionOrNull())
    }

    @Test
    fun validatesRotationAndProtectionPasswords() {
        assertNull(PdfEngine.validateRotationDegrees(90))
        assertNull(PdfEngine.validateRotationDegrees(180))
        assertNotNull(PdfEngine.validateRotationDegrees(45))
        assertNull(PdfEngine.validateProtectionPasswords("password1", "ownerpass"))
        assertNotNull(PdfEngine.validateProtectionPasswords("short", ""))
        assertNotNull(PdfEngine.validateProtectionPasswords("password1", "x".repeat(129)))
    }

    @Test
    fun boundsPdfCompressionAndRasterDimensions() {
        assertNull(PdfEngine.validateCompressionSettings(120, 45))
        assertNotNull(PdfEngine.validateCompressionSettings(0, 45))
        assertNotNull(PdfEngine.validateCompressionSettings(120, 100))
        assertEquals(1020 to 1320, PdfEngine.calculateRenderDimensions(612, 792, 120))
        assertNotNull(runCatching { PdfEngine.calculateRenderDimensions(20000, 20000, 300) }.exceptionOrNull())
    }
}
