package com.company.admob.sample.xml.ads

import com.company.admob.BannerSize
import com.company.admob.CollapsibleBannerPosition
import com.company.admob.NativeStyle
import com.company.admob.config.AdDefinition
import com.company.admob.config.AdProfile
import com.company.admob.config.AdSettings

/**
 * Every ad in this app, with its default settings (docs/AD_PROFILES.md). Each entry's name is its Firebase Remote
 * Config parameter (project `sample-ads-plugin`): a JSON value there overrides these defaults, field by field.
 *
 * No entry lists ad unit IDs, so each uses Google's test ad unit for its format. A real app lists its IDs here (or
 * sets them in Firebase): `AdProfile.interstitial(ids = listOf("ca-app-pub-…/…"))`.
 */
enum class SampleAds(override val defaults: AdProfile) : AdDefinition {
    /** Shown on the splash screen; off by default. */
    SplashAd(AdProfile.interstitial(enabled = false, splash = true, splashTimeoutSeconds = 8)),

    /** Home screen: "Level complete". At most one per minute. */
    LevelCompleteAd(AdProfile.interstitial(timerSeconds = 60)),

    /** Home screen: "Extra life". */
    ExtraLifeRewarded(AdProfile.rewarded()),

    /** Home screen: "Bonus round". */
    BonusRewardedInterstitial(AdProfile.rewardedInterstitial()),

    /** On return to the app, never over the splash screen. */
    AppOpenAd(
        AdProfile.appOpen(timerSeconds = 240, excludedActivities = listOf("com.company.admob.sample.xml.SplashActivity")),
    ),

    /** Bottom of the home screen. */
    HomeBanner(AdProfile.banner(style = BannerSize.AnchoredAdaptive)),

    /** Bottom of the details screen. */
    DetailsBanner(AdProfile.banner(style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM))),

    /** In the feed, every few articles. */
    FeedNative(AdProfile.native(style = NativeStyle.SMALL)),

    /** Inside an article. */
    ArticleNative(AdProfile.native(style = NativeStyle.LARGE)),
    ;

    companion object {
        /** The defaults of the Firebase `AdSettings` parameter. */
        val settings = AdSettings(minSecondsBetweenFullScreenAds = 30)
    }
}
