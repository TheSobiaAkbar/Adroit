package com.company.admob.internal.google

import android.content.Context
import com.company.admob.BannerSize
import com.company.admob.GoogleSdkEscapeHatch
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.RequestExtras
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest

/**
 * Builds Google ad requests and applies the host's escape-hatch customizer. A customizer that throws is logged and
 * the request is rebuilt without it, so a host bug never blocks ads.
 */
internal class GoogleRequestFactory(private val log: AdLog) {
    fun adRequest(adUnitId: String, extras: RequestExtras): AdRequest =
        customized(extras, { AdRequest.Builder(adUnitId) }, { it.build() })

    fun nativeAdRequest(adUnitId: String, extras: RequestExtras): NativeAdRequest =
        customized(extras, { NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE)) }, { it.build() })

    fun bannerAdRequest(adUnitId: String, adSize: AdSize, extras: RequestExtras): BannerAdRequest =
        customized(extras, { BannerAdRequest.Builder(adUnitId, adSize) }, { it.build() })

    @OptIn(GoogleSdkEscapeHatch::class)
    private inline fun <B : Any, R> customized(extras: RequestExtras, newBuilder: () -> B, build: (B) -> R): R {
        val customizer = extras.customizer ?: return build(newBuilder())
        return try {
            val builder = newBuilder()
            customizer.customize(extras.placement, builder)
            build(builder)
        } catch (e: Exception) {
            log.e(e) { "GoogleRequestCustomizer failed for ${extras.placement}; using the request without it" }
            build(newBuilder())
        }
    }

    companion object {
        /** Google's size for [size] at [widthDp]. Anchored adaptive uses the current, non-deprecated "large" API. */
        fun adSize(context: Context, size: BannerSize, widthDp: Int): AdSize = when (size) {
            BannerSize.AnchoredAdaptive -> AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
            is BannerSize.InlineAdaptive -> AdSize.getInlineAdaptiveBannerAdSize(widthDp, size.maxHeightDp)
            BannerSize.MediumRectangle -> AdSize.MEDIUM_RECTANGLE
            BannerSize.Standard -> AdSize.BANNER
        }
    }
}
