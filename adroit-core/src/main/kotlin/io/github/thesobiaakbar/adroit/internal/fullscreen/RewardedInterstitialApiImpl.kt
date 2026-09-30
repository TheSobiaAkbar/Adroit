package io.github.thesobiaakbar.adroit.internal.fullscreen

import io.github.thesobiaakbar.adroit.RewardedInterstitialApi
import io.github.thesobiaakbar.adroit.RewardedInterstitialPlacement

/**
 * [RewardedInterstitialApi] over the rewarded interstitial [FullScreenAdEngine]. The library renders no intro screen:
 * the host must show one, announcing the reward with an opt-out, before calling `show`.
 */
internal class RewardedInterstitialApiImpl(engine: FullScreenAdEngine, unclaimed: UnclaimedRewards) :
    RewardedFormatApi<RewardedInterstitialPlacement>(engine, unclaimed), RewardedInterstitialApi
