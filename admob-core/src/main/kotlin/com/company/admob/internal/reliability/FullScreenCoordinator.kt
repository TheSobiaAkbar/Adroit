package com.company.admob.internal.reliability

import com.company.admob.internal.time.Clock

/**
 * Ensures only one full-screen ad shows at a time, across every format. [tryAcquire] hands out a [Token]; only that
 * token can [release], so a late release from an old show session can't free a newer one. Also records when the last
 * full-screen ad ended and when one was last clicked, for the app-open suppression window (architecture §15, gate 4).
 * Main thread only.
 */
internal class FullScreenCoordinator(private val clock: Clock) {
    /** Proof of ownership. Compared by identity. */
    class Token internal constructor(val owner: String) {
        override fun toString(): String = "Token($owner)"
    }

    private var current: Token? = null

    val isShowing: Boolean get() = current != null

    /** The owner of the ad showing now, for diagnostics. */
    val currentOwner: String? get() = current?.owner

    var lastFullScreenEndedAtElapsed: Long? = null
        private set

    var lastClickAtElapsed: Long? = null
        private set

    /** Returns a token for [owner], or null if another full-screen ad is showing. */
    fun tryAcquire(owner: String): Token? {
        if (current != null) return null
        return Token(owner).also { current = it }
    }

    /** Releases [token] if it's still the current one. Returns false for a stale or repeated release. */
    fun release(token: Token): Boolean {
        if (current !== token) return false
        current = null
        lastFullScreenEndedAtElapsed = clock.elapsedRealtimeMillis()
        return true
    }

    fun markClicked() {
        lastClickAtElapsed = clock.elapsedRealtimeMillis()
    }
}
