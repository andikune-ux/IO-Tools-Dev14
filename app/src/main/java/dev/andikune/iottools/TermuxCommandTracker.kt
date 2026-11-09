package dev.andikune.iottools

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Matches asynchronous Termux callbacks to the command that created them.
 * A callback for an unknown or expired request is intentionally ignored.
 */
internal class TermuxCommandTracker {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()

    fun register(requestId: String): CompletableDeferred<String> {
        require(requestId.isNotBlank()) { "requestId must not be blank" }
        val completion = CompletableDeferred<String>()
        check(pending.putIfAbsent(requestId, completion) == null) {
            "A Termux request with this ID is already pending"
        }
        return completion
    }

    fun complete(requestId: String?, message: String): Boolean {
        if (requestId.isNullOrBlank()) return false
        val completion = pending.remove(requestId) ?: return false
        return completion.complete(message)
    }

    fun remove(requestId: String) {
        pending.remove(requestId)
    }
}
