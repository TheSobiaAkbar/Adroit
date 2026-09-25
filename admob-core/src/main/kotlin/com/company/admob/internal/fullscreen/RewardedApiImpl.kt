package com.company.admob.internal.fullscreen

import android.app.Activity
import com.company.admob.AdAvailability
import com.company.admob.AdPlacement
import com.company.admob.RewardResult
import com.company.admob.RewardedApi
import com.company.admob.RewardedPlacement
import com.company.admob.RewardedShowOptions
import com.company.admob.internal.threading.runOnMain
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
