package dev.andikune.iottools

import dev.andikune.iottools.tools.privacy.Base32
import dev.andikune.iottools.tools.privacy.VaultCrypto
import dev.andikune.iottools.tools.privacy.generateTotp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyCryptoTest {
    @Test
    fun base32RoundTripsAndRejectsNonCanonicalInput() {
        val source = "IO Tools TOTP".toByteArray()
        val encoded = Base32.encode(source)
        assertEquals(source.toList(), Base32.decode(encoded).toList())
        assertEquals("foo", String(Base32.decode("MZXW6===")))
        assertNotNull(runCatching { Base32.decode("MZXW7") }.exceptionOrNull())
        assertNotNull(runCatching { Base32.decode("A") }.exceptionOrNull())
    }

    @Test
    fun totpMatchesRfc6238Sha1TestVector() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals("287082", generateTotp(secret, 1L))
    }

    @Test
    fun secureNotesUseVersionedKdfAndRejectTampering() {
        val plaintext = "Catatan rahasia yang harus tetap privat."
        val passphrase = "Correct horse battery staple"
        val payload = VaultCrypto.encrypt(plaintext, passphrase)
        assertTrue(payload.startsWith("v2:"))
        assertEquals(plaintext, VaultCrypto.decrypt(payload, passphrase))
        assertNotEquals(plaintext, runCatching { VaultCrypto.decrypt(payload, "wrong passphrase") }.getOrNull())
        val tampered = payload.dropLast(2) + "AA"
        assertNotNull(runCatching { VaultCrypto.decrypt(tampered, passphrase) }.exceptionOrNull())
    }
}
