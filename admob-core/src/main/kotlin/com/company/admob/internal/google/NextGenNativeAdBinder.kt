package com.company.admob.internal.google

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.LoadedNativeAd
import com.company.admob.internal.port.NativeAssetViews
import com.company.admob.internal.port.NativeRenderPort
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/** [NativeRenderPort] over Google's `NativeAdView` and `MediaView`, both created in code. */
internal class NextGenNativeAdBinder(private val log: AdLog) : NativeRenderPort {
    override fun createAdView(context: Context): ViewGroup = NativeAdView(context)

    override fun createMediaView(context: Context): View =
        MediaView(context).apply { imageScaleType = ImageView.ScaleType.CENTER_CROP }

    override fun register(adView: ViewGroup, ad: LoadedNativeAd, views: NativeAssetViews, mediaView: View?) {
        val nativeAdView = adView as NativeAdView
        nativeAdView.headlineView = views.headline
        nativeAdView.bodyView = views.body
        nativeAdView.callToActionView = views.callToAction
        nativeAdView.iconView = views.icon
        nativeAdView.advertiserView = views.advertiser
        nativeAdView.starRatingView = views.starRating
        nativeAdView.priceView = views.price
        nativeAdView.storeView = views.store
        // A null media view is allowed: the ad renders without one.
        nativeAdView.registerNativeAd(ad.bindInternal() as NativeAd, mediaView as MediaView?)
    }

    override fun release(adView: ViewGroup) {
        try {
            (adView as NativeAdView).destroy()
        } catch (e: Exception) {
            log.w(e) { "Releasing a native ad view failed" }
        }
    }
}
