package dev.andikune.iottools.tools.data
import dev.andikune.iottools.tools.data.buildBatchRenameName
import org.junit.Assert.assertEquals
import org.junit.Test

class DataHashToolsValidationTest {
    @Test
    fun buildsSafeNumberedNamesAndPreservesExtension() {
        assertEquals(
            "BACKUP_007_final.zip",
            buildBatchRenameName("archive.zip", "BACKUP_", "_final", 7L, 0)
        )
    }

    @Test
    fun hashStreamReaderHandlesTemporaryZeroByteReads() {
        val source = byteArrayOf(10, 20, 30)
        val input = object : java.io.InputStream() {
            private val delegate = java.io.ByteArrayInputStream(source)
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
        val buffer = ByteArray(8)
        assertEquals(1, readFileHashChunk(input, buffer))
        assertEquals(10, buffer[0].toInt())
        assertEquals(2, readFileHashChunk(input, buffer))
        assertEquals(30, buffer[1].toInt())
        assertEquals(-1, readFileHashChunk(input, buffer))
    }

    @Test
    fun replacesPathSeparatorsAndCapsTheOutputName() {
        val result = buildBatchRenameName("video.mp4", "../prefix", "/suffix", 12L, 0)
        assertEquals(false, result.contains('/'))
        assertEquals(false, result.contains('\\'))
        assertEquals(true, result.endsWith(".mp4"))
        assertEquals(true, result.length <= 200)
    }
}
