package io.github.thesobiaakbar.adroit.internal.reliability

import io.github.thesobiaakbar.adroit.internal.callbacks.OnceCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * One `show()` call: its result is delivered exactly once ([onResult] is a [OnceCallback], safe under races), and a
 * watchdog resolves it with [timeoutResult] if the ad neither shows nor fails within [watchdogTimeout] (architecture
 * §13), so a lost SDK callback can't leave the coordinator stuck. [onTimeout] runs only when the watchdog wins, for
 * cleanup such as releasing the [FullScreenCoordinator].
 *
 * The watchdog starts on construction; create the session right before calling the SDK's show.
 */
internal class ShowSession<R>(
    private val onResult: OnceCallback<R>,
    scope: CoroutineScope,
    watchdogTimeout: Duration,
    private val timeoutResult: () -> R,
    private val onTimeout: () -> Unit = {},
) {
    @Volatile
    var isShown: Boolean = false
        private set

    val isResolved: Boolean get() = onResult.isInvoked

    private val watchdog: Job = scope.launch {
        delay(watchdogTimeout)
        if (onResult(timeoutResult())) onTimeout()
    }

    /** The ad is on screen: the watchdog is no longer needed. The result comes later, on dismiss. */
    fun markShown() {
        isShown = true
        watchdog.cancel()
    }

    /** Delivers [result] if nothing was delivered yet. Returns false when it was ignored as late or repeated. */
    fun resolve(result: R): Boolean {
        watchdog.cancel()
        return onResult(result)
    }
}
