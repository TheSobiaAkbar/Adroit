package io.github.thesobiaakbar.adroit.internal.google

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.port.BannerListener
import io.github.thesobiaakbar.adroit.internal.port.PreloadedBanner
import io.github.thesobiaakbar.adroit.internal.port.RequestExtras
import io.github.thesobiaakbar.adroit.internal.port.SdkBannerView
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRefreshCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlin.math.roundToInt

/**
 * A Google `AdView` created with the Activity, as the SDK requires. `loadAd` is a View call, so it runs on the main
 * thread; the SDK does the network work itself and calls back on a background thread, which is posted to main.
 */
internal class NextGenBannerView(
    activity: Activity,
    private val requestFactory: GoogleRequestFactory,
    private val log: AdLog,
) : SdkBannerView {
    private val adView = AdView(activity)
    private var listener: MainThreadBannerListener? = null
    private var destroyed = false

    override val view: View
        get() = adView

    override fun load(adUnitId: String, size: BannerSize, widthDp: Int, extras: RequestExtras, listener: BannerListener) {
        if (destroyed) return
        this.listener?.clear()
        val mainListener = MainThreadBannerListener(listener)
        this.listener = mainListener
        try {
            val adSize = GoogleRequestFactory.adSize(adView.context, size, widthDp)
            val request = requestFactory.bannerAdRequest(adUnitId, adSize, extras)
            adView.loadAd(request, BannerEvents(mainListener, log))
        } catch (e: Exception) {
            log.e(e) { "Loading a banner failed" }
            mainListener.onFailedToLoad(AdError.Internal("Loading the banner failed", e), retryable = true, isRefresh = false)
        }
    }

    override fun expectedHeightPx(size: BannerSize, widthDp: Int): Int {
        val context = adView.context
        val height = try {
            GoogleRequestFactory.adSize(context, size, widthDp).getHeightInPixels(context)
        } catch (e: Exception) {
            log.w(e) { "Reading the banner size failed" }
            0
        }
        if (height > 0) return height
        // Inline adaptive heights are only known after the ad loads; reserve the maximum.
        val maxHeightDp = (size as? BannerSize.InlineAdaptive)?.maxHeightDp ?: 0
        return (maxHeightDp * context.resources.displayMetrics.density).roundToInt()
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        listener?.clear()
        listener = null
        (adView.parent as? ViewGroup)?.removeView(adView)
        try {
            adView.destroy()
        } catch (e: Exception) {
            log.w(e) { "Destroying a banner failed" }
        }
    }

    override fun detach(): PreloadedBanner? {
        if (destroyed) return null
        val ad = unregister(adView, log)
        destroy()
        return ad?.let { NextGenPreloadedBanner(it, log) }
    }
}

/** The loaded ad of [adView], taken out of it (the SDK's `unregisterBannerAd`), or null. */
private fun unregister(adView: AdView, log: AdLog): BannerAd? = try {
    adView.unregisterBannerAd()
} catch (e: Exception) {
    log.w(e) { "Taking the ad out of a banner view failed" }
    null
}

/**
 * A banner loaded without a view (`BannerAd.load`). [attach] gets its view for the Activity that shows it, and from
 * then on its events go to that banner's listener.
 */
internal class NextGenPreloadedBanner(private val ad: BannerAd, private val log: AdLog) : PreloadedBanner {
    override fun attach(activity: Activity, listener: BannerListener): SdkBannerView {
        val mainListener = MainThreadBannerListener(listener)
        BannerEvents(mainListener, log).adopt(ad)
        val adView = AdView(activity)
        adView.registerBannerAd(ad, activity)
        return AttachedBanner(adView, ad, mainListener, log)
    }

    override fun destroy() {
        try {
            ad.destroy()
        } catch (e: Exception) {
            log.w(e) { "Destroying a preloaded banner failed" }
        }
    }
}

/** The view of an attached preloaded banner. It's loaded already, and the SDK refreshes it; it never loads again. */
private class AttachedBanner(
    private val adView: AdView,
    private val ad: BannerAd,
    private val listener: MainThreadBannerListener,
    private val log: AdLog,
) : SdkBannerView {
    private var destroyed = false

    override val view: View
        get() = adView

    override fun load(adUnitId: String, size: BannerSize, widthDp: Int, extras: RequestExtras, listener: BannerListener) {
        // Never called: a banner needing a new load (a width change, a retry) replaces this view with a new one.
        log.w { "A preloaded banner doesn't load again" }
    }

    override fun expectedHeightPx(size: BannerSize, widthDp: Int): Int = try {
        ad.getAdSize().getHeightInPixels(view.context)
    } catch (e: Exception) {
        log.w(e) { "Reading the preloaded banner's size failed" }
        0
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        listener.clear()
        (adView.parent as? ViewGroup)?.removeView(adView)
        try {
            adView.destroy()
            ad.destroy()
        } catch (e: Exception) {
            log.w(e) { "Destroying a banner failed" }
        }
    }

    override fun detach(): PreloadedBanner? {
        if (destroyed) return null
        destroyed = true
        listener.clear()
        (adView.parent as? ViewGroup)?.removeView(adView)
        unregister(adView, log)
        try {
            adView.destroy()
        } catch (e: Exception) {
            log.w(e) { "Destroying a banner view failed" }
        }
        return NextGenPreloadedBanner(ad, log)
    }
}

/** Maps Google's banner load, refresh and event callbacks, which arrive on background threads. */
private class BannerEvents(
    private val listener: MainThreadBannerListener,
    private val log: AdLog,
) :
    AdLoadCallback<BannerAd>,
    BannerAdEventCallback,
    BannerAdRefreshCallback {
    @Volatile
    private var bannerAd: BannerAd? = null

    /** Takes the events of [ad], loaded already (a preloaded banner being attached). */
    fun adopt(ad: BannerAd) {
        bannerAd = ad
        try {
            ad.adEventCallback = this
            ad.bannerAdRefreshCallback = this
        } catch (e: Exception) {
            log.w(e) { "Handling a preloaded banner failed; its events may be missing" }
        }
    }

    override fun onAdLoaded(ad: BannerAd) {
        bannerAd = ad
        val source = try {
            ad.adEventCallback = this
            ad.bannerAdRefreshCallback = this
            GoogleResponseInfoMapper.map(ad.getResponseInfo())
        } catch (e: Exception) {
            log.w(e) { "Handling a loaded banner failed; its events may be missing" }
            null
        }
        listener.onLoaded(source, isRefresh = false)
    }

    override fun onAdFailedToLoad(adError: LoadAdError) {
        val failure = GoogleErrorMapper.map(adError)
        listener.onFailedToLoad(failure.error, failure.retryable, isRefresh = false)
    }

    override fun onAdRefreshed() {
        listener.onLoaded(GoogleResponseInfoMapper.map(bannerAd?.getResponseInfo()), isRefresh = true)
    }

    override fun onAdFailedToRefresh(adError: LoadAdError) {
        val failure = GoogleErrorMapper.map(adError)
        listener.onFailedToLoad(failure.error, failure.retryable, isRefresh = true)
    }

    override fun onAdImpression() = listener.onImpression()

    override fun onAdClicked() = listener.onClicked()

    override fun onAdPaid(value: AdValue) = listener.onPaid(GoogleRevenueMapper.map(value))
}
