package com.company.admob.internal.port

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * The Google-specific steps of rendering a native ad (architecture §17), so the binder that inflates and fills the
 * host layout stays Google-free and testable. Main thread only.
 */
internal interface NativeRenderPort {
    /** The ad's root view (Google's `NativeAdView`), which must be the ancestor of every asset view. */
    fun createAdView(context: Context): ViewGroup

    /** A media view (Google's `MediaView`) for the main image or video, center-cropped. */
    fun createMediaView(context: Context): View

    /** Tells the SDK which views show which asset, then registers [ad] with [adView]. */
    fun register(adView: ViewGroup, ad: LoadedNativeAd, views: NativeAssetViews, mediaView: View?)

    /** Releases [adView] once it no longer shows the ad. Safe to call more than once. */
    fun release(adView: ViewGroup)
}

/** The host layout's asset views, found by ID. Only [headline] is required. */
internal class NativeAssetViews(
    val headline: View,
    val body: View?,
    val callToAction: View?,
    val icon: View?,
    val advertiser: View?,
    val starRating: View?,
    val price: View?,
    val store: View?,
)
