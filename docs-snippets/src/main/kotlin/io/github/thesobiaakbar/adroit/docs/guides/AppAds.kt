package io.github.thesobiaakbar.adroit.docs.guides

import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.CollapsibleBannerPosition
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.config.AdProfile
import io.github.thesobiaakbar.adroit.config.AdSettings
import io.github.thesobiaakbar.adroit.config.SplashAdMode

// The ads enum of docs/guides/ADS_*.md and ADS_IAP_*.md, step 4.

// region guide-ads-enum
/** Every ad in the app. Each entry's name is also its Firebase Remote Config parameter. */
enum class AppAds(override val defaults: AdProfile) : AdDefinition {
    // Full-screen ads
    SplashAd(AdProfile.interstitial(splash = true)),
    LevelCompleteAd(AdProfile.interstitial(counter = 2, loadAgain = true)),
    ExtraLifeRewarded(AdProfile.rewarded(loadAgain = true)),
    AppOpenAd(AdProfile.appOpen(excludedActivities = listOf("com.example.myapp.SplashActivity"), loadAgain = true)),

    // Banners and native ads
    HomeBanner(AdProfile.banner(style = BannerSize.AnchoredAdaptive)),
    DetailsBanner(AdProfile.banner(style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM))),
    HomeNative(AdProfile.native(style = NativeStyle.LARGE_BUTTON_BOTTOM)),
    FeedNative(AdProfile.native(style = NativeStyle.SMALL_BUTTON_END, loadAgain = true)),
    ;

    companion object {
        /** What every ad shares: the Firebase parameter "AdSettings". */
        val settings = AdSettings(splashSeconds = 11, minSecondsBetweenFullScreenAds = 30, splashAdMode = SplashAdMode.SHOW)
    }
}
// endregion
