package com.company.admob.sample.compose.ads

import com.company.admob.AdPlacement
import com.company.admob.AppOpenPlacement
import com.company.admob.BannerPlacement
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement

/** Every placement the playground exercises. The keys are what the Configuration screen overrides. */
object Placements {
    val AnchoredBanner = BannerPlacement("playground_banner_anchored")
    val InlineBanner = BannerPlacement("playground_banner_inline")
    val RectangleBanner = BannerPlacement("playground_banner_mrec")
    val Interstitial = InterstitialPlacement("playground_interstitial")
    val Rewarded = RewardedPlacement("playground_rewarded")
    val RewardedInterstitial = RewardedInterstitialPlacement("playground_rewarded_interstitial")
    val AppOpen = AppOpenPlacement("playground_app_open")
    val TemplatesNative = NativePlacement("playground_native_templates")
    val FeedNative = NativePlacement("playground_native_feed")
    val CustomNative = NativePlacement("playground_native_custom")

    /** The placements that cache an ad, and so have an availability worth showing. */
    val fullScreen: List<AdPlacement> = listOf(Interstitial, Rewarded, RewardedInterstitial, AppOpen)
}
