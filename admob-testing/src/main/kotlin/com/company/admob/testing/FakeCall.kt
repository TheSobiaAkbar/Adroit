package com.company.admob.testing

/**
 * A call a [FakeAdsClient] received, in [FakeAdsClient.recordedCalls]. Placements are recorded by key. New call types
 * may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed class FakeCall {
    /** `start(activity)`. */
    public data object Start : FakeCall()

    /** `setAdFree(value)`. */
    public data class SetAdFree(val value: Boolean) : FakeCall()

    /** A `preload` of these placements, from `AdsClient.preload` or a format API. */
    public data class Preload(val placementKeys: List<String>) : FakeCall()

    /** A format API's `clear(placement)`. */
    public data class Clear(val placementKey: String) : FakeCall()

    /** An interstitial `show` or `showAndAwait`. */
    public data class ShowInterstitial(val placementKey: String) : FakeCall()

    /** An interstitial `showOnSplash`. It takes the next queued interstitial result, like a show. */
    public data class ShowInterstitialOnSplash(val placementKey: String) : FakeCall()

    /** A rewarded `show` or `showAndAwait`, with its server-side verification data. */
    public data class ShowRewarded(val placementKey: String, val customData: String? = null) : FakeCall()

    /** A rewarded interstitial `show` or `showAndAwait`, with its server-side verification data. */
    public data class ShowRewardedInterstitial(val placementKey: String, val customData: String? = null) : FakeCall()

    /** `appOpen.showOnColdStart(...)`. */
    public data object ShowAppOpenOnColdStart : FakeCall()

    /** `appOpen.skipNextForeground()`. */
    public data object SkipNextAppOpen : FakeCall()

    /** `appOpen.suppressWhile(owner)`. */
    public data object SuppressAppOpen : FakeCall()

    /** `appOpen.setEnabled(enabled)`. */
    public data class SetAppOpenEnabled(val enabled: Boolean) : FakeCall()

    /** `banner.create(...)`. */
    public data class CreateBanner(val placementKey: String) : FakeCall()

    /** A `native.load(...)`. */
    public data class LoadNative(val placementKey: String) : FakeCall()

    /** `native.render(...)`. */
    public data class RenderNative(val placementKey: String) : FakeCall()

    /** `privacy.showPrivacyOptions(...)`. */
    public data object ShowPrivacyOptions : FakeCall()

    /** `privacy.resetConsentForTesting()`. */
    public data object ResetConsent : FakeCall()

    /** `diagnostics.openAdInspector(...)`. */
    public data object OpenAdInspector : FakeCall()
}
