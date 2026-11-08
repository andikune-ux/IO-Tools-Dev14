package dev.andikune.iottools

import dev.andikune.iottools.tools.media.validateFrameTime
import dev.andikune.iottools.tools.media.validateMediaTimeRange
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class MediaToolsValidationTest {
    @Test
    fun acceptsTrimRangesWithinMediaDuration() {
        assertNull(validateMediaTimeRange(0L, 15_000L, 60_000L))
        assertNull(validateMediaTimeRange(10_000L, 60_000L, 60_000L))
    }

    @Test
    fun rejectsInvalidTrimRanges() {
        assertNotNull(validateMediaTimeRange(-1L, 10_000L, 60_000L))
        assertNotNull(validateMediaTimeRange(10_000L, 10_000L, 60_000L))
        assertNotNull(validateMediaTimeRange(50_000L, 61_000L, 60_000L))
        assertNotNull(validateMediaTimeRange(0L, 1_000L, 0L))
    }

    @Test
    fun validatesFrameTimeAgainstDuration() {
        assertNull(validateFrameTime(0L, 60_000L))
        assertNull(validateFrameTime(59L, 60_000L))
        assertNotNull(validateFrameTime(60L, 60_000L))
        assertNotNull(validateFrameTime(-1L, 60_000L))
        assertNotNull(validateFrameTime(1L, 0L))
    }
}
