package com.company.admob.internal.google

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.company.admob.AdError
import com.company.admob.BannerSize
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.BannerListener
import com.company.admob.internal.port.RequestExtras
import com.company.admob.internal.port.SdkBannerView
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
