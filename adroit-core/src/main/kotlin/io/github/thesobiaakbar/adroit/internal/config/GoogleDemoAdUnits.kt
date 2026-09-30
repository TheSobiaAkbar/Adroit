package io.github.thesobiaakbar.adroit.internal.config

import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.BannerSize

/**
 * Google's demo ad unit IDs for the Next-Gen SDK: the only definition in the codebase.
 * Source: https://developers.google.com/admob/android/next-gen/test-ads (docs/architecture/01_SDK_RESEARCH.md §1.5).
 */
internal object GoogleDemoAdUnits {
    const val DEMO_PUBLISHER_PREFIX = "ca-app-pub-3940256099942544"
    const val SAMPLE_APP_ID = "ca-app-pub-3940256099942544~3347511713"

    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    const val ADAPTIVE_BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val FIXED_SIZE_BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
    const val REWARDED_INTERSTITIAL = "ca-app-pub-3940256099942544/5354046379"
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val NATIVE_VIDEO = "ca-app-pub-3940256099942544/1044960115"

    /** The demo ad unit for [format]. [bannerSize] picks the adaptive or fixed-size banner unit. */
    fun forFormat(format: AdFormat, bannerSize: BannerSize? = null): String = when (format) {
        AdFormat.BANNER -> when (bannerSize) {
            BannerSize.Standard, BannerSize.MediumRectangle -> FIXED_SIZE_BANNER
            else -> ADAPTIVE_BANNER
        }
        AdFormat.INTERSTITIAL -> INTERSTITIAL
        AdFormat.REWARDED -> REWARDED
        AdFormat.REWARDED_INTERSTITIAL -> REWARDED_INTERSTITIAL
        AdFormat.APP_OPEN -> APP_OPEN
        AdFormat.NATIVE -> NATIVE
    }

    /** True when [id] (an app ID or ad unit ID) belongs to Google's demo publisher account. */
    fun isDemo(id: String): Boolean = id.startsWith(DEMO_PUBLISHER_PREFIX)
}
