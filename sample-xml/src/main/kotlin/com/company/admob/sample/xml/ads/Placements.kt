package com.company.admob.sample.xml.ads

import com.company.admob.config.appOpenPlacement
import com.company.admob.config.bannerPlacement
import com.company.admob.config.interstitialPlacement
import com.company.admob.config.nativePlacement
import com.company.admob.config.rewardedInterstitialPlacement
import com.company.admob.config.rewardedPlacement

/**
 * The typed placements of [SampleAds], for the format-specific APIs (`CompanyAdMob.interstitial.show(…)`). XML
 * attributes use the entry name directly: `app:company_admob_placement="HomeBanner"`.
 */
object Placements {
    val SplashInterstitial = SampleAds.SplashAd.interstitialPlacement()
    val HomeBanner = SampleAds.HomeBanner.bannerPlacement()
    val DetailsBanner = SampleAds.DetailsBanner.bannerPlacement()
    val LevelCompleteInterstitial = SampleAds.LevelCompleteAd.interstitialPlacement()
    val ExtraLifeRewarded = SampleAds.ExtraLifeRewarded.rewardedPlacement()
    val BonusRewardedInterstitial = SampleAds.BonusRewardedInterstitial.rewardedInterstitialPlacement()
    val AppOpen = SampleAds.AppOpenAd.appOpenPlacement()
    val FeedNative = SampleAds.FeedNative.nativePlacement()
    val ArticleNative = SampleAds.ArticleNative.nativePlacement()
}
