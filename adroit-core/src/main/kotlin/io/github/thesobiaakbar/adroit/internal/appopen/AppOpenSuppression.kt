package io.github.thesobiaakbar.adroit.internal.appopen

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.thesobiaakbar.adroit.internal.reliability.FullScreenCoordinator
import io.github.thesobiaakbar.adroit.internal.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The host-controlled and time-based reasons to hold back an app open ad (architecture §15, gates 4–6):
 * - [skipNextForeground]: a one-shot flag the next warm foreground consumes.
 * - [suppressWhile]: lifecycle-scoped suppressors, active while their owner is at least STARTED. Each owner is
 *   referenced only until its ON_DESTROY, when its observer removes it.
 * - [afterFullScreenRemaining]: the window after another full-screen ad ended or was clicked.
 *
 * Main thread only.
 */
internal class AppOpenSuppression(private val clock: Clock) {
    private var skipNext = false
    private val suppressors = mutableMapOf<LifecycleOwner, LifecycleEventObserver>()

    /** The number of registered suppressors, active or not. */
    val suppressorCount: Int get() = suppressors.size

    /** True while any suppressor's owner is at least STARTED. */
    val isSuppressed: Boolean
        get() = suppressors.keys.any { it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }

    fun skipNextForeground() {
        skipNext = true
    }

    /** Returns whether a skip was requested, and clears the request. */
    fun consumeSkip(): Boolean = skipNext.also { skipNext = false }

    fun suppressWhile(owner: LifecycleOwner) {
        val lifecycle = owner.lifecycle
        if (lifecycle.currentState == Lifecycle.State.DESTROYED || owner in suppressors) return
        val observer = LifecycleEventObserver { source, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                suppressors.remove(source)?.let { source.lifecycle.removeObserver(it) }
            }
        }
        suppressors[owner] = observer
        lifecycle.addObserver(observer)
    }

    /**
     * How much longer app open stays held back after the last full-screen ad ended or was clicked, or null when
     * [window] has passed since both. A click-through leaves the app while the ad is still showing, so returning
     * from it is covered by the coordinator's showing state and then by this window.
     */
    fun afterFullScreenRemaining(coordinator: FullScreenCoordinator, window: Duration): Duration? {
        val last = listOfNotNull(coordinator.lastFullScreenEndedAtElapsed, coordinator.lastClickAtElapsed).maxOrNull()
            ?: return null
        val remaining = window - (clock.elapsedRealtimeMillis() - last).milliseconds
        return remaining.takeIf { it.isPositive() }
    }
}
