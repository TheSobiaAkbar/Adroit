package com.company.admob.internal.banner

import android.app.Activity
import android.os.Build
import com.company.admob.BannerSize
import kotlin.math.abs

/** What a banner request needs besides the ad unit: the size kind and the width it's computed for, in dp. */
internal data class BannerSizeSpec(val size: BannerSize, val widthDp: Int) {
    val isAdaptive: Boolean
        get() = size is BannerSize.AnchoredAdaptive || size is BannerSize.InlineAdaptive || size is BannerSize.Collapsible
}

/**
 * Banner width rules (architecture §16), pure so they're unit-tested without views. The Google `AdSize` is made from
 * the resulting spec by the SDK adapter, with non-deprecated APIs only.
 */
internal object BannerSizeResolver {
    /** A width change at least this large (in dp) reloads an adaptive banner. */
    const val RELOAD_THRESHOLD_DP: Int = 8

    const val STANDARD_WIDTH_DP: Int = 320
    const val MEDIUM_RECTANGLE_WIDTH_DP: Int = 300

    /**
     * The spec for [size] in a container [containerWidthPx] wide, falling back to [windowWidthPx] when the container
     * has no width yet. Fixed sizes ignore both.
     */
    fun resolve(size: BannerSize, containerWidthPx: Int, density: Float, windowWidthPx: Int): BannerSizeSpec {
        val widthDp = when (size) {
            BannerSize.Standard -> STANDARD_WIDTH_DP
            BannerSize.MediumRectangle -> MEDIUM_RECTANGLE_WIDTH_DP
            BannerSize.AnchoredAdaptive, is BannerSize.InlineAdaptive, is BannerSize.Collapsible -> {
                val widthPx = if (containerWidthPx > 0) containerWidthPx else windowWidthPx
                if (density > 0f) (widthPx.coerceAtLeast(0) / density).toInt() else 0
            }
        }
        return BannerSizeSpec(size, widthDp)
    }

    /** Whether [loaded] should be replaced after its container became [newWidthDp] wide. */
    fun needsReload(loaded: BannerSizeSpec, newWidthDp: Int): Boolean =
        loaded.isAdaptive && newWidthDp > 0 && abs(newWidthDp - loaded.widthDp) >= RELOAD_THRESHOLD_DP

    /** The Activity window's width: `WindowMetrics` on API 30+, the display metrics below. */
    fun windowWidthPx(activity: Activity): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.windowManager.currentWindowMetrics.bounds.width()
        } else {
            activity.resources.displayMetrics.widthPixels
        }
}
