package dev.andikune.iottools.tools.privacy
import dev.andikune.iottools.tools.privacy.Base32
import dev.andikune.iottools.tools.privacy.VaultCrypto
import dev.andikune.iottools.tools.privacy.generateTotp
import dev.andikune.iottools.tools.privacy.generatePassword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

@RunWith(RobolectricTestRunner::class)
class PrivacyCryptoTest {
    @Test
    fun base32RoundTripsAndRejectsNonCanonicalInput() {
        val source = "IO Tools TOTP".toByteArray()
        val encoded = Base32.encode(source)
        assertEquals(source.toList(), Base32.decode(encoded).toList())
        assertEquals("foo", String(Base32.decode("MZXW6===")))
        assertNotNull(runCatching { Base32.decode("MZXW7") }.exceptionOrNull())
        assertNotNull(runCatching { Base32.decode("MZXW6=") }.exceptionOrNull())
        assertNotNull(runCatching { Base32.decode("A") }.exceptionOrNull())
        assertNotNull(runCatching { Base32.decode("A".repeat(257)) }.exceptionOrNull())
    }

    @Test
    fun passwordGeneratorHonorsSelectedCharacterGroups() {
        val password = generatePassword(64, true, true, true, true)
        assertEquals(64, password.length)
        assertTrue(password.any { it.isUpperCase() })
        assertTrue(password.any { it.isLowerCase() })
        assertTrue(password.any { it.isDigit() })
        assertTrue(password.any { "!@#\$%^&*()-_=+[]{}".contains(it) })
        assertEquals("", generatePassword(16, false, false, false, false))
        assertNotNull(runCatching { generatePassword(7, true, false, false, false) }.exceptionOrNull())
        assertNotNull(runCatching { generatePassword(65, true, false, false, false) }.exceptionOrNull())
    }

    @Test
    fun totpMatchesRfc6238Sha1TestVector() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals("287082", generateTotp(secret, 1L))
    }

    @Test
    fun decryptsLegacyV1NotesUsingSha1Kdf() {
        val plaintext = "Legacy encrypted note"
        val passphrase = "legacy passphrase"
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 120_000, 256)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        spec.clearPassword()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = listOf(
            "v1",
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(iv, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        ).joinToString(":")

        assertEquals(plaintext, VaultCrypto.decrypt(payload, passphrase))
    }

    @Test
    fun secureNotesUseVersionedKdfAndRejectTampering() {
        val plaintext = "Catatan rahasia yang harus tetap privat."
        val passphrase = "Correct horse battery staple"
        val payload = VaultCrypto.encrypt(plaintext, passphrase)
        assertTrue(payload.startsWith("v2:"))
        assertNotNull(runCatching { VaultCrypto.encrypt("x".repeat(100_001), passphrase) }.exceptionOrNull())
        assertNotNull(runCatching { VaultCrypto.decrypt("x".repeat(100_001), passphrase) }.exceptionOrNull())
        assertEquals(plaintext, VaultCrypto.decrypt(payload, passphrase))
        assertNotEquals(plaintext, runCatching { VaultCrypto.decrypt(payload, "wrong passphrase") }.getOrNull())
        val tampered = payload.dropLast(2) + "AA"
        assertNotNull(runCatching { VaultCrypto.decrypt(tampered, passphrase) }.exceptionOrNull())
    }
}
