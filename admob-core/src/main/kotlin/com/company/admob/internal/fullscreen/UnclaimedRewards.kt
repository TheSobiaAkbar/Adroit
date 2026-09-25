package com.company.admob.internal.fullscreen

import androidx.annotation.VisibleForTesting
import com.company.admob.RewardResult
import com.company.admob.internal.logging.AdLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Earned rewards whose `showAndAwait` caller was cancelled before the ad finished (architecture §14), one per
 * format. Unbounded and never persisted. [flow] is single-consumer: each reward goes to exactly one collector.
 */
internal class UnclaimedRewards(private val log: AdLog) {
    private val channel = Channel<RewardResult.Earned>(Channel.UNLIMITED)

    val flow: Flow<RewardResult.Earned> = channel.receiveAsFlow()

    /** Takes one pending unclaimed reward without suspending, for tests. */
    @VisibleForTesting
    fun poll(): RewardResult.Earned? = channel.tryReceive().getOrNull()

    /** A result whose caller was cancelled: an earned reward is kept; anything else is safe to drop. */
    fun onCallerCancelled(result: RewardResult) {
        if (result is RewardResult.Earned) {
            channel.trySend(result)
            log.i { "Reward ${result.rewardId} delivered to unclaimedResults: its caller was cancelled" }
        } else {
            log.d { "showAndAwait caller cancelled; dropping $result" }
        }
    }

    /**
     * Suspends until [start]'s callback delivers a result. If the caller is cancelled first, the ad isn't cancelled:
     * the result goes to [onCallerCancelled] instead. `resume` with an `onCancellation` handler makes the choice
     * atomically, including a cancellation that lands after resume but before the caller runs (prompt
     * cancellation), so a result reaches the caller or this class, never both and never neither.
     */
    suspend fun await(start: ((RewardResult) -> Unit) -> Unit): RewardResult =
        suspendCancellableCoroutine { continuation ->
            start { result ->
                continuation.resume(result) { _, value, _ -> onCallerCancelled(value) }
            }
        }
}
