package io.github.thesobiaakbar.adroit.sample.xml.home

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.thesobiaakbar.adroit.AdSlots
import io.github.thesobiaakbar.adroit.AdsClient
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.RewardResult
import io.github.thesobiaakbar.adroit.config.rewardedInterstitialPlacement
import io.github.thesobiaakbar.adroit.config.rewardedPlacement
import io.github.thesobiaakbar.adroit.sample.xml.ads.SampleAds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Coins, and the ads that change them. Shared by the Home screen and the bonus intro dialog (Activity scope).
 *
 * Rewards are granted here, not in a Fragment, and at most once per `rewardId`: a reward delivered late through
 * `unclaimedResults` (its screen was gone when the ad closed) is granted the same way, never twice. A real app would
 * persist the balance and the granted IDs, or verify rewards server-side.
 */
class CoinsViewModel(private val ads: AdsClient = Adroit.client) : ViewModel() {
    private val grantedRewardIds = mutableSetOf<String>()
    private val mutableCoins = MutableStateFlow(0)
    private val navigation = Channel<Unit>(Channel.BUFFERED)

    val coins: StateFlow<Int> = mutableCoins.asStateFlow()

    /** One event per finished "Level complete": the screen navigates to the details, whether an ad showed or not. */
    val openDetails: Flow<Unit> = navigation.receiveAsFlow()

    init {
        viewModelScope.launch {
            merge(ads.rewarded.unclaimedResults, ads.rewardedInterstitial.unclaimedResults).collect(::grant)
        }
    }

    /**
     * The Home screen is in front: load its full-screen ads, so each is ready when its button is tapped. Ads load
     * only when asked, and a failed load isn't retried; this runs on every resume, so it also asks again after a
     * failure. It does nothing for an ad already loaded or loading, such as one `loadAgain` reloaded after its show.
     */
    fun loadAds() {
        AdSlots.load(SampleAds.LevelCompleteAd)
        AdSlots.load(SampleAds.ExtraLifeRewarded)
        AdSlots.load(SampleAds.BonusRewardedInterstitial)
        // The Details screen's banner and native ad, loaded ahead: they show the moment Details opens (its
        // attachBanner and AdNativeView take them), or when they arrive if they're still loading.
        AdSlots.load(SampleAds.DetailsBanner)
        AdSlots.load(SampleAds.ArticleNative)
    }

    fun onLevelComplete(activity: Activity) {
        // Both branches continue: an ad that wasn't ready, was capped, or failed never blocks the user. AdSlots shows
        // LevelCompleteAd in its current format: an interstitial, or an app open ad if Firebase swapped it.
        AdSlots.showFullScreen(activity, SampleAds.LevelCompleteAd) { navigation.trySend(Unit) }
    }

    fun onExtraLife(activity: Activity) {
        ads.showRewarded(activity, SampleAds.ExtraLifeRewarded.rewardedPlacement()) { result ->
            if (result is RewardResult.Earned) grant(result)
        }
    }

    /** Only after the intro screen offered the reward with a way to opt out (Google's rewarded interstitial rule). */
    fun onBonusAccepted(activity: Activity) {
        ads.rewardedInterstitial.show(activity, SampleAds.BonusRewardedInterstitial.rewardedInterstitialPlacement()) { result ->
            if (result is RewardResult.Earned) grant(result)
        }
    }

    private fun grant(reward: RewardResult.Earned) {
        if (!grantedRewardIds.add(reward.rewardId)) return
        mutableCoins.value += reward.reward.amount
    }
}
