package com.company.admob.internal.fullscreen

import com.company.admob.RewardedInterstitialApi
import com.company.admob.RewardedInterstitialPlacement

/**
 * [RewardedInterstitialApi] over the rewarded interstitial [FullScreenAdEngine]. The library renders no intro screen:
 * the host must show one, announcing the reward with an opt-out, before calling `show`.
 */
internal class RewardedInterstitialApiImpl(engine: FullScreenAdEngine, unclaimed: UnclaimedRewards) :
    RewardedFormatApi<RewardedInterstitialPlacement>(engine, unclaimed), RewardedInterstitialApi
