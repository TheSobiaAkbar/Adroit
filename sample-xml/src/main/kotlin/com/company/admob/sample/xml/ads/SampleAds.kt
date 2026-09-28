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
 *
 * The app refers to every ad through this enum: `AdSlots.showFullScreen(activity, SampleAds.LevelCompleteAd)`,
 * `SampleAds.ExtraLifeRewarded.rewardedPlacement()` for the format-specific APIs, or the entry name in XML
 * (`app:company_admob_placement="HomeBanner"`).
 *
 * Ads load only when the app asks (`AdSlots.load`): the Home screen for its full-screen ads, `MainActivity` for the
 * app open ad, the splash screen for `SplashAd`, and each native view for its ad. `loadAgain = true` loads the next
 * ad (the next ID of the list) right after each show; the others wait for the next `AdSlots.load`.
 */
enum class SampleAds(override val defaults: AdProfile) : AdDefinition {
    /** Shown on the splash screen when it loads before the splash timer ends (docs/SPLASH.md). */
    SplashAd(AdProfile.interstitial(splash = true)),

    /** Home screen: "Level complete". At most one per minute. */
    LevelCompleteAd(AdProfile.interstitial(timerSeconds = 60, loadAgain = true)),

    /** Home screen: "Extra life". */
    ExtraLifeRewarded(AdProfile.rewarded(loadAgain = true)),

    /** Home screen: "Bonus round". */
    BonusRewardedInterstitial(AdProfile.rewardedInterstitial(loadAgain = true)),

    /** On return to the app, never over the splash screen. Loaded again after each show, for the next return. */
    AppOpenAd(
        AdProfile.appOpen(
            timerSeconds = 240,
            excludedActivities = listOf("com.company.admob.sample.xml.SplashActivity"),
            loadAgain = true,
        ),
    ),

    /** Bottom of the home screen. */
    HomeBanner(AdProfile.banner(style = BannerSize.AnchoredAdaptive)),

    /** Bottom of the details screen. */
    DetailsBanner(AdProfile.banner(style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM))),

    /** In the feed, every few articles: each ad shown is replaced, ready for the next slot. */
    FeedNative(AdProfile.native(style = NativeStyle.SMALL, loadAgain = true)),

    /** Inside an article. */
    ArticleNative(AdProfile.native(style = NativeStyle.LARGE)),
    ;

    companion object {
        /** The defaults of the Firebase `AdSettings` parameter. */
        val settings = AdSettings(minSecondsBetweenFullScreenAds = 30, splashSeconds = 11)
    }
}
