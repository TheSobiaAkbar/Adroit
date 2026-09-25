package com.company.admob

/**
 * Last-resort hook to modify Google ad requests, for example to add mediation network extras. Register it with
 * `adMobConfig { advanced { googleRequestCustomizer = ... } }`.
 *
 * [builder] is the Next-Gen SDK's request builder for the placement's format (`AdRequest.Builder`,
 * `BannerAdRequest.Builder`, or `NativeAdRequest.Builder`); cast it yourself. It is typed as [Any] so that no
 * Google type appears in CompanyAdMob's binary API. Called on the main thread before each load. Exceptions are
 * caught and logged, and the request continues unmodified.
 */
@GoogleSdkEscapeHatch
public fun interface GoogleRequestCustomizer {
    /** Customises the request [builder] for [placement]. */
    public fun customize(placement: AdPlacement, builder: Any)
}
