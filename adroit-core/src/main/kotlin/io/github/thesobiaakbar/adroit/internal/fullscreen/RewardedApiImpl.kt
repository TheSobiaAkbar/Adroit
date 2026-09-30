package io.github.thesobiaakbar.adroit.internal.fullscreen

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdAvailability
import io.github.thesobiaakbar.adroit.AdPlacement
import io.github.thesobiaakbar.adroit.RewardResult
import io.github.thesobiaakbar.adroit.RewardedApi
import io.github.thesobiaakbar.adroit.RewardedPlacement
import io.github.thesobiaakbar.adroit.RewardedShowOptions
import io.github.thesobiaakbar.adroit.internal.threading.runOnMain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The shared implementation of the two rewarded formats over a [FullScreenAdEngine]. Callable from any thread;
 * engine work runs on the main thread.
 */
internal abstract class RewardedFormatApi<P : AdPlacement>(
    private val engine: FullScreenAdEngine,
    private val unclaimed: UnclaimedRewards,
) {
    fun preload(placement: P) = runOnMain { engine.preload(placement) }

    fun availability(placement: P): StateFlow<AdAvailability> = engine.availability(placement)

    fun isReady(placement: P): Boolean = engine.isReady(placement)

    fun clear(placement: P) = runOnMain { engine.clear(placement) }

    val unclaimedResults: Flow<RewardResult.Earned> get() = unclaimed.flow

    fun show(activity: Activity, placement: P, options: RewardedShowOptions, onResult: (RewardResult) -> Unit) =
        runOnMain { engine.showRewarded(activity, placement, options.serverSideVerificationCustomData, onResult) }

    suspend fun showAndAwait(activity: Activity, placement: P, options: RewardedShowOptions): RewardResult =
        unclaimed.await { deliver -> show(activity, placement, options, deliver) }
}

/** [RewardedApi] over the rewarded [FullScreenAdEngine]. */
internal class RewardedApiImpl(engine: FullScreenAdEngine, unclaimed: UnclaimedRewards) :
    RewardedFormatApi<RewardedPlacement>(engine, unclaimed), RewardedApi
