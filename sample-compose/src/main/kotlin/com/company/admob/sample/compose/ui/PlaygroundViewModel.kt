package com.company.admob.sample.compose.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.admob.AdsClient
import com.company.admob.FullScreenResult
import com.company.admob.RewardResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * State shared by the playground's screens, kept across rotation and navigation (Activity scope).
 *
 * Rewards are granted here, at most once per `rewardId`, including rewards delivered late through
 * `unclaimedResults` (the screen that showed the ad was gone when it closed).
 */
class PlaygroundViewModel(private val ads: AdsClient) : ViewModel() {
    private val grantedRewardIds = mutableSetOf<String>()
    private val mutableCoins = MutableStateFlow(0)
    private val mutableUnclaimedGrants = MutableStateFlow(0)
    private val mutableLastInterstitial = MutableStateFlow<FullScreenResult?>(null)
    private val mutableLastReward = MutableStateFlow<RewardResult?>(null)
    private val mutableAdFree = MutableStateFlow(false)
    private val mutableAppOpenEnabled = MutableStateFlow(true)

    val coins: StateFlow<Int> = mutableCoins.asStateFlow()

    /** How many rewards arrived through `unclaimedResults` rather than a show callback. */
    val unclaimedGrants: StateFlow<Int> = mutableUnclaimedGrants.asStateFlow()

    val lastInterstitial: StateFlow<FullScreenResult?> = mutableLastInterstitial.asStateFlow()
    val lastReward: StateFlow<RewardResult?> = mutableLastReward.asStateFlow()

    /** What this app last passed to `setAdFree`: the library has no getter for it. */
    val adFree: StateFlow<Boolean> = mutableAdFree.asStateFlow()

    /** What this app last passed to `appOpen.setEnabled`: the library has no getter for it. */
    val appOpenEnabled: StateFlow<Boolean> = mutableAppOpenEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            merge(ads.rewarded.unclaimedResults, ads.rewardedInterstitial.unclaimedResults).collect { reward ->
                if (grant(reward)) mutableUnclaimedGrants.value++
            }
        }
    }

    fun onInterstitialResult(result: FullScreenResult) {
        mutableLastInterstitial.value = result
    }

    fun onRewardResult(result: RewardResult) {
        mutableLastReward.value = result
        if (result is RewardResult.Earned) grant(result)
    }

    fun setAdFree(adFree: Boolean) {
        mutableAdFree.value = adFree
        ads.setAdFree(adFree)
    }

    fun setAppOpenEnabled(enabled: Boolean) {
        mutableAppOpenEnabled.value = enabled
        ads.appOpen.setEnabled(enabled)
    }

    /** Grants [reward] unless its `rewardId` was already granted. */
    private fun grant(reward: RewardResult.Earned): Boolean {
        if (!grantedRewardIds.add(reward.rewardId)) return false
        mutableCoins.value += reward.reward.amount
        return true
    }
}
