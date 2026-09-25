package com.company.admob.fakes

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.company.admob.internal.port.LoadedNativeAd
import com.company.admob.internal.port.NativeAssetViews
import com.company.admob.internal.port.NativeRenderPort

/** A [NativeRenderPort] with plain views in place of Google's, recording each registration and release. */
internal class FakeNativeRenderPort : NativeRenderPort {
    class Registration(val adView: ViewGroup, val ad: LoadedNativeAd, val views: NativeAssetViews, val mediaView: View?)

    /** Stands in for Google's `NativeAdView`. */
    class FakeAdView(context: Context) : FrameLayout(context)

    /** Stands in for Google's `MediaView`. */
    class FakeMediaView(context: Context) : View(context)

    val registrations = mutableListOf<Registration>()
    val released = mutableListOf<ViewGroup>()

    override fun createAdView(context: Context): ViewGroup = FakeAdView(context)

    override fun createMediaView(context: Context): View = FakeMediaView(context)

    override fun register(adView: ViewGroup, ad: LoadedNativeAd, views: NativeAssetViews, mediaView: View?) {
        registrations += Registration(adView, ad, views, mediaView)
    }

    override fun release(adView: ViewGroup) {
        released += adView
    }
}
