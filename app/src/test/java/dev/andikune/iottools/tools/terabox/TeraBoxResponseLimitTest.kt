package dev.andikune.iottools.tools.terabox

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TeraBoxResponseLimitTest {
    @Test
    fun readsStreamsThatTemporarilyReturnZeroBytes() {
        val bytes = "worker-response".toByteArray()
        val input = object : InputStream() {
            private val delegate = ByteArrayInputStream(bytes)
            private var returnZero = true

            override fun read(): Int = delegate.read()

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (returnZero) {
                    returnZero = false
                    return 0
                }
                return delegate.read(buffer, offset, length)
            }
        }

        assertArrayEquals(bytes, TeraBoxDownloaderService.readLimited(input, 1024))
    }

    @Test
    fun acceptsResponseExactlyAtLimitButRejectsLargerBody() {
        assertArrayEquals(
            byteArrayOf(1, 2, 3),
            TeraBoxDownloaderService.readLimited(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3)
        )
        assertThrows(IllegalStateException::class.java) {
            TeraBoxDownloaderService.readLimited(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3)
        }
    }

    @Test
    fun rejectsNonPositiveResponseLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            TeraBoxDownloaderService.readLimited(ByteArrayInputStream(byteArrayOf(1)), 0)
        }
    }
}
