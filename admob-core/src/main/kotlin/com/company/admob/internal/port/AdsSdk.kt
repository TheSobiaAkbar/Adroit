package com.company.admob.internal.port

import android.app.Activity
import android.content.Context
import android.view.View
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.AdPlacement
import com.company.admob.BannerSize
import com.company.admob.CollapsibleBannerPosition
import com.company.admob.GoogleRequestCustomizer
import com.company.admob.GoogleSdkEscapeHatch
import com.company.admob.NativeAdAssets
import com.company.admob.Reward
import com.company.admob.config.AgeTreatment
import com.company.admob.config.MaxAdContentRating
import com.company.admob.events.AdRevenue
import com.company.admob.events.AdSourceInfo

/**
 * The engines' only view of the Google Mobile Ads SDK (architecture §9). Parameters and results are Google-free, so
 * engines run in JVM tests against a fake.
 *
 * Threading: methods are called on the main thread. Every callback, result and listener call is delivered on the
 * main thread, always posted (never synchronous), and at most once per operation.
 */
internal interface AdsSdk {
    /** Google SDK version, or null when it can't be read. */
    val sdkVersion: String?

    /**
     * Initializes the SDK off the main thread. [beforeInit] runs first, on that same background thread, for work that
     * must finish before the SDK starts (disk writes the SDK reads). [onComplete] runs on the main thread exactly once.
     */
    fun initialize(
        appContext: Context,
        appId: String,
        request: SdkRequestConfig,
        disableCrashReporting: Boolean,
        beforeInit: () -> Unit,
        onComplete: (SdkInitResult) -> Unit,
    )

    /** Replaces the SDK's whole request configuration. */
    fun updateRequestConfig(request: SdkRequestConfig)

    /** Loads one interstitial, rewarded, rewarded interstitial or app open ad. */
    fun loadFullScreen(
        format: AdFormat,
        adUnitId: String,
        extras: RequestExtras,
        onResult: (SdkLoadResult<LoadedFullScreenAd>) -> Unit,
    )

    /** Loads one native ad. */
    fun loadNative(adUnitId: String, extras: RequestExtras, onResult: (SdkLoadResult<LoadedNativeAd>) -> Unit)

    /** Creates a banner view bound to [activity]. Main thread only. */
    fun createBannerView(activity: Activity): SdkBannerView

    /**
     * Loads a banner without a view, to show later in a banner container ([PreloadedBanner.attach]): a banner loaded
     * ahead of its screen. [widthDp] sizes adaptive banners. [onResult] may be called on any thread.
     */
    fun preloadBanner(
        context: Context,
        adUnitId: String,
        size: BannerSize,
        widthDp: Int,
        extras: RequestExtras,
        onResult: (SdkLoadResult<PreloadedBanner>) -> Unit,
    )

    /** Opens Google's Ad Inspector. [onClosed] receives null on a normal close. */
    fun openAdInspector(onClosed: (AdError?) -> Unit)
}

/** Everything that goes into the SDK's global request configuration, which is always replaced as a whole. */
internal data class SdkRequestConfig(
    val testDeviceIds: List<String>,
    val ageTreatment: AgeTreatment,
    val maxAdContentRating: MaxAdContentRating?,
)

/**
 * Per-request inputs besides the ad unit ID. [collapsible] asks for a collapsible banner at that edge (banners only,
 * first load of a banner view only).
 */
@OptIn(GoogleSdkEscapeHatch::class)
internal data class RequestExtras(
    val placement: AdPlacement,
    val customizer: GoogleRequestCustomizer?,
    val collapsible: CollapsibleBannerPosition? = null,
)

internal sealed interface SdkInitResult {
    data object Success : SdkInitResult

    data class Failure(val error: AdError) : SdkInitResult
}

internal sealed interface SdkLoadResult<out T> {
    data class Success<T>(val ad: T, val source: AdSourceInfo?) : SdkLoadResult<T>

    /** [retryable] is false for configuration and request errors, which won't succeed on retry (architecture §25). */
    data class Failure(val error: AdError, val retryable: Boolean) : SdkLoadResult<Nothing>
}

/** A loaded, single-use full-screen ad. */
internal interface LoadedFullScreenAd {
    val format: AdFormat
    val source: AdSourceInfo?

    /** Sets the server-side verification custom data. Rewarded formats only; a no-op otherwise. */
    fun setServerSideVerification(customData: String?)

    /** Shows the ad. Main thread only; [listener] calls are delivered on the main thread. */
    fun show(activity: Activity, listener: FullScreenAdListener)

    /** Drops every reference to the SDK ad and its listener. Safe to call more than once. */
    fun release()
}

internal interface FullScreenAdListener {
    fun onShown()

    fun onImpression()

    fun onClicked()

    fun onPaid(revenue: AdRevenue)

    fun onRewardEarned(reward: Reward)

    fun onDismissed()

    fun onFailedToShow(error: AdError)
}

/** A loaded native ad. The owner must call [destroy]. */
internal interface LoadedNativeAd {
    val assets: NativeAdAssets
    val source: AdSourceInfo?

    /** Sets or clears the listener. Its calls are delivered on the main thread. */
    fun setListener(listener: NativeAdListener?)

    fun destroy()

    /**
     * The underlying Google `NativeAd`, typed as [Any] so this port stays Google-free. Only the internal native
     * binder in core (Prompt 12) may use it, to register the ad with Google's `NativeAdView`.
     */
    fun bindInternal(): Any
}

internal interface NativeAdListener {
    fun onImpression()

    fun onClicked()

    fun onPaid(revenue: AdRevenue)
}

/** A banner view. Every method is main-thread only. */
internal interface SdkBannerView {
    /** The view to add to the host's hierarchy. */
    val view: View

    /** Loads an ad. The SDK refreshes it while visible, reporting each refresh to [listener]. */
    fun load(adUnitId: String, size: BannerSize, widthDp: Int, extras: RequestExtras, listener: BannerListener)

    /** The height the banner will take, so space can be reserved before the ad loads. */
    fun expectedHeightPx(size: BannerSize, widthDp: Int): Int

    /** Destroys the SDK view and drops the listener. Safe to call more than once. */
    fun destroy()

    /**
     * Takes the loaded ad out of this view, to show it in another later ([PreloadedBanner.attach]), and destroys the
     * view and its listener: nothing keeps the Activity. Null, with the view destroyed, when no ad is loaded.
     */
    fun detach(): PreloadedBanner?
}

/** A banner loaded without a view ([AdsSdk.preloadBanner]), or taken out of one ([SdkBannerView.detach]). Main thread only. */
internal interface PreloadedBanner {
    /**
     * Shows it in [activity]: a banner view whose SDK events (impressions, clicks, refreshes, revenue) go to
     * [listener] from now on. Called at most once; the returned view owns the ad and destroys it.
     */
    fun attach(activity: Activity, listener: BannerListener): SdkBannerView

    /** Destroys an ad that was never attached. */
    fun destroy()
}

internal interface BannerListener {
    /** An ad loaded. [isRefresh] is true for SDK refreshes after the first load. */
    fun onLoaded(source: AdSourceInfo?, isRefresh: Boolean)

    fun onFailedToLoad(error: AdError, retryable: Boolean, isRefresh: Boolean)

    fun onImpression()

    fun onClicked()

    fun onPaid(revenue: AdRevenue)
}
