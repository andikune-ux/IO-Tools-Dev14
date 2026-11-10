package dev.andikune.iottools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxCommandTrackerTest {
    @Test
    fun deliversEachCallbackOnlyToItsMatchingCommand() = runBlocking {
        val tracker = TermuxCommandTracker()
        val first = tracker.register("request-1")
        val second = tracker.register("request-2")

        assertTrue(tracker.complete("request-2", "second result"))
        assertTrue(tracker.complete("request-1", "first result"))

        assertEquals("first result", first.await())
        assertEquals("second result", second.await())
    }

    @Test
    fun ignoresUnknownAndExpiredCallbacks() = runBlocking {
        val tracker = TermuxCommandTracker()
        val pending = tracker.register("active")

        assertFalse(tracker.complete("unknown", "unrelated"))
        tracker.remove("active")
        assertFalse(tracker.complete("active", "late result"))
        assertFalse(pending.isCompleted)
    }

    @Test
    fun rejectsDuplicateRequestIds() {
        val tracker = TermuxCommandTracker()
        tracker.register("duplicate")

        try {
            tracker.register("duplicate")
            throw AssertionError("Expected duplicate request ID to be rejected")
        } catch (_: IllegalStateException) {
            // Expected: one callback must never be able to complete two commands.
        }
    }
}
