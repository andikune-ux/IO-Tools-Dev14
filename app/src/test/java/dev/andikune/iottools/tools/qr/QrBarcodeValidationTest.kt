package dev.andikune.iottools.tools.qr
import com.google.zxing.BarcodeFormat
import dev.andikune.iottools.tools.qr.isValidEan13Input
import dev.andikune.iottools.tools.qr.validateBarcodeInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrBarcodeValidationTest {
    @Test
    fun validatesEan13DataAndCheckDigits() {
        assertTrue(isValidEan13Input("899123456789"))
        assertTrue(isValidEan13Input("5901234123457"))
        assertFalse(isValidEan13Input("5901234123458"))
        assertFalse(isValidEan13Input("12345"))
        assertFalse(isValidEan13Input("59012341234567"))
    }

    @Test
    fun validatesBarcodeCharacterSets() {
        assertNull(validateBarcodeInput("CODE-39 / 123", BarcodeFormat.CODE_39))
        assertTrue(validateBarcodeInput("lowercase", BarcodeFormat.CODE_39) != null)
        assertNull(validateBarcodeInput("ABC-123", BarcodeFormat.CODE_128))
        assertTrue(validateBarcodeInput("Halo 😀", BarcodeFormat.CODE_128) != null)
        assertEquals(
            "EAN-13 membutuhkan 12 digit data, atau 13 digit dengan digit pemeriksa yang valid.",
            validateBarcodeInput("5901234123458", BarcodeFormat.EAN_13)
        )
    }
}
