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
    fun replacesPathSeparatorsAndCapsTheOutputName() {
        val result = buildBatchRenameName("video.mp4", "../prefix", "/suffix", 12L, 0)
        assertEquals(false, result.contains('/'))
        assertEquals(false, result.contains('\\'))
        assertEquals(true, result.endsWith(".mp4"))
        assertEquals(true, result.length <= 200)
    }
}
