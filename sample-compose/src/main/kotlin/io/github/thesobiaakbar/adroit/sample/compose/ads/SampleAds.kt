package io.github.thesobiaakbar.adroit.sample.compose.ads

import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.CollapsibleBannerPosition
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.config.AdProfile
import io.github.thesobiaakbar.adroit.config.AdSettings

/**
 * Every ad in this app, with its default settings (docs/AD_PROFILES.md). Each entry's name is its Firebase Remote
 * Config parameter (project `sample-ads-plugin`, shared with sample-xml): a JSON value there overrides these defaults,
 * field by field. The entries and their defaults are sample-xml's, so both apps read the same parameters
 * (FirebaseTemplateTest checks it).
 *
 * No entry lists ad unit IDs, so each uses Google's test ad unit for its format. A real app lists its IDs here (or
 * sets them in Firebase): `AdProfile.interstitial(ids = listOf("ca-app-pub-…/…"))`.
 *
 * The app refers to every ad through this enum: `AdSlots.showFullScreen(activity, SampleAds.LevelCompleteAd)`,
 * `SampleAds.ExtraLifeRewarded.rewardedPlacement()` for the format-specific APIs, and `AdSlot(SampleAds.HomeBanner)` for
 * every banner and native ad in composables: it shows whichever format the ad is. (`AdNative(placement, layout = …)`
 * only where one screen needs its own layout, as NativeStylesScreen does.)
 *
 * Ads load only when the app asks (`AdSlots.load`): the Home screen for its full-screen ads, `MainActivity` for the
 * app open ad, the splash screen for `SplashAd`, and each banner or native composable for its ad. `loadAgain = true`
 * loads the next ad (the next ID of the list) right after each show; the others wait for the next `AdSlots.load`.
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

    /**
     * On return to the app, never over the splash screen. Loaded again after each show, for the next return. The
     * splash is its own Activity so that it can be excluded here: a navigation destination can't be.
     */
    AppOpenAd(
        AdProfile.appOpen(
            timerSeconds = 240,
            excludedActivities = listOf("io.github.thesobiaakbar.adroit.sample.compose.SplashActivity"),
            loadAgain = true,
        ),
    ),

    /** Bottom of the home screen. */
    HomeBanner(AdProfile.banner(style = BannerSize.AnchoredAdaptive)),

    /** Bottom of the details screen. */
    DetailsBanner(AdProfile.banner(style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM))),

    /** In the feed, between articles: a banner in a lazy list, kept by the screen like the native ads. */
    FeedBanner(AdProfile.banner(style = BannerSize.MediumRectangle)),

    /** In the feed, every few articles: each ad shown is replaced, ready for the next slot. */
    FeedNative(AdProfile.native(style = NativeStyle.SMALL_BUTTON_END, loadAgain = true)),

    /** Inside an article. */
    ArticleNative(AdProfile.native(style = NativeStyle.LARGE_COMPACT)),
    ;

    companion object {
        /** The defaults of the Firebase `AdSettings` parameter. */
        val settings = AdSettings(minSecondsBetweenFullScreenAds = 30, splashSeconds = 11)
    }
}
