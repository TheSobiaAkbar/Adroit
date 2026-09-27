package com.company.admob.internal.google

import android.content.Context
import android.os.Bundle
import com.company.admob.BannerSize
import com.company.admob.CollapsibleBannerPosition
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

    /**
     * A banner request. [RequestExtras.collapsible] adds Google's `collapsible` extra
     * (https://developers.google.com/admob/android/next-gen/banner/collapsible) before the host's customizer runs.
     */
    fun bannerAdRequest(adUnitId: String, adSize: AdSize, extras: RequestExtras): BannerAdRequest =
        customized(
            extras,
            {
                BannerAdRequest.Builder(adUnitId, adSize).apply {
                    extras.collapsible?.let { setGoogleExtrasBundle(collapsibleExtras(it)) }
                }
            },
            { it.build() },
        )

    @OptIn(GoogleSdkEscapeHatch::class)
    private inline fun <B : Any, R> customized(extras: RequestExtras, newBuilder: () -> B, build: (B) -> R): R {
        val customizer = extras.customizer ?: return build(newBuilder())
        return try {
            val builder = newBuilder()
            customizer.customize(extras.placement, builder)
            build(builder)
        } catch (e: Exception) {
            withoutCustomizer(extras, e, newBuilder, build)
        } catch (e: LinkageError) {
            // A customizer compiled against another Google SDK version (NoSuchMethodError, NoClassDefFoundError).
            withoutCustomizer(extras, e, newBuilder, build)
        }
    }

    private inline fun <B : Any, R> withoutCustomizer(
        extras: RequestExtras,
        error: Throwable,
        newBuilder: () -> B,
        build: (B) -> R,
    ): R {
        log.e(error) { "GoogleRequestCustomizer failed for ${extras.placement}; using the request without it" }
        return build(newBuilder())
    }

    companion object {
        /** Google's size for [size] at [widthDp]. Anchored adaptive uses the current, non-deprecated "large" API. */
        fun adSize(context: Context, size: BannerSize, widthDp: Int): AdSize = when (size) {
            BannerSize.AnchoredAdaptive, is BannerSize.Collapsible ->
                AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
            is BannerSize.InlineAdaptive -> AdSize.getInlineAdaptiveBannerAdSize(widthDp, size.maxHeightDp)
            BannerSize.MediumRectangle -> AdSize.MEDIUM_RECTANGLE
            BannerSize.Standard -> AdSize.BANNER
        }

        /** Google's extras for a collapsible banner; the value names the edge the expanded ad aligns to. */
        fun collapsibleExtras(position: CollapsibleBannerPosition): Bundle = Bundle().apply {
            putString(
                COLLAPSIBLE_KEY,
                when (position) {
                    CollapsibleBannerPosition.TOP -> "top"
                    CollapsibleBannerPosition.BOTTOM -> "bottom"
                },
            )
        }

        const val COLLAPSIBLE_KEY: String = "collapsible"
    }
}
