package io.github.thesobiaakbar.adroit.testing.sample

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdsClient
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.RewardResult
import io.github.thesobiaakbar.adroit.RewardedPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How a host app declares its placements. */
object Placements {
    val LevelEnd = InterstitialPlacement("level_end")
    val ExtraCoins = RewardedPlacement("extra_coins")
}

/**
 * A host-style ViewModel, as an app would write it: it depends only on the injected [AdsClient] (a plain class here;
 * in an app it would extend `androidx.lifecycle.ViewModel` and use `viewModelScope`).
 *
 * - After a level, it shows an interstitial and moves on to the next level whatever happened.
 * - "Watch an ad for coins" grants the reward's amount, idempotently by `rewardId`, including rewards delivered late
 *   through `unclaimedResults`.
 */
class LevelViewModel(private val ads: AdsClient, scope: CoroutineScope) {
    private val grantedRewardIds = mutableSetOf<String>()
    private val mutableCoins = MutableStateFlow(0)
    private val mutableLevel = MutableStateFlow(1)

    val coins: StateFlow<Int> = mutableCoins.asStateFlow()
    val level: StateFlow<Int> = mutableLevel.asStateFlow()

    /** True while the "watch ad" button should be enabled. */
    val rewardAvailable: Boolean get() = ads.rewarded.isReady(Placements.ExtraCoins)

    init {
        ads.preload(Placements.LevelEnd, Placements.ExtraCoins)
        scope.launch { ads.rewarded.unclaimedResults.collect(::grant) }
    }

    fun onLevelComplete(activity: Activity) {
        ads.showInterstitial(activity, Placements.LevelEnd) { result: FullScreenResult ->
            // Continue in every branch: an ad that wasn't shown never blocks the game.
            mutableLevel.value++
        }
    }

    fun onWatchAdForCoins(activity: Activity) {
        ads.showRewarded(activity, Placements.ExtraCoins) { result ->
            if (result is RewardResult.Earned) grant(result)
        }
    }

    private fun grant(reward: RewardResult.Earned) {
        if (!grantedRewardIds.add(reward.rewardId)) return
        mutableCoins.value += reward.reward.amount
    }
}
