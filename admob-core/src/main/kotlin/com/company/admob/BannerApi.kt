package com.company.admob

import android.app.Activity
import android.view.View
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow

/**
 * Banner ads. Obtain it from [AdsClient.banner]. Most apps use the `admob-views` or `admob-compose` helpers,
 * which call this for you and handle the lifecycle.
 */
public interface BannerApi {
    /**
     * Creates a banner for [placement]. Add [BannerAdHandle.view] to your layout and call
     * [BannerAdHandle.bindTo] (or call [BannerAdHandle.destroy] yourself). Main thread only.
     * The banner loads as soon as the library is ready.
     */
    public fun create(
        activity: Activity,
        placement: BannerPlacement,
        options: BannerOptions = BannerOptions(),
    ): BannerAdHandle
}

/**
 * Options for a banner. Immutable.
 *
 * @property size the banner size. Defaults to Google's (large) anchored adaptive size.
 * @property reserveSpace reserve the banner's expected height before it loads, to avoid layout shifts.
 */
public class BannerOptions(
    public val size: BannerSize = BannerSize.AnchoredAdaptive,
    public val reserveSpace: Boolean = true,
) {
    override fun equals(other: Any?): Boolean =
        other is BannerOptions && other.size == size && other.reserveSpace == reserveSpace

    override fun hashCode(): Int = 31 * size.hashCode() + reserveSpace.hashCode()

    override fun toString(): String = "BannerOptions(size=$size, reserveSpace=$reserveSpace)"
}

/** Banner sizes. New sizes may be added in minor versions. */
public sealed interface BannerSize {
    /** Full-width anchored adaptive banner (Google's large anchored adaptive size). Recommended default. */
    public data object AnchoredAdaptive : BannerSize

    /** Full-width inline adaptive banner for scrolling content, at most [maxHeightDp] tall. */
    public data class InlineAdaptive(public val maxHeightDp: Int) : BannerSize

    /** 300x250 medium rectangle. */
    public data object MediumRectangle : BannerSize

    /** 320x50 standard banner. */
    public data object Standard : BannerSize
}

/** A banner created by [BannerApi.create]. Main thread only. */
public interface BannerAdHandle {
    /** The view to add to your layout. It contains the ad once it loads. */
    public val view: View

    /** Current banner state. Emits on the main thread. */
    public val state: StateFlow<BannerState>

    /** Expected banner height in pixels, known before the ad loads (0 until the width is known). */
    public val expectedHeightPx: Int

    /** Destroys the banner automatically when [owner] is destroyed. In a Fragment, pass `viewLifecycleOwner`. */
    public fun bindTo(owner: LifecycleOwner)

    /** Removes the banner from its parent and releases it. Safe to call more than once. */
    public fun destroy()
}

/** State of a banner. New states may be added in minor versions. */
public sealed interface BannerState {
    /** Waiting for the library to be ready or for an ad. */
    public data object Loading : BannerState

    /** An ad is showing. */
    public data object Loaded : BannerState

    /** The last load failed with [error]. The SDK or library may retry. */
    public data class Failed(public val error: AdError) : BannerState

    /** No ad is shown on purpose, for example ad-free or consent withdrawn. [reason] is null after `destroy()`. */
    public data class Hidden(public val reason: UnavailableReason?) : BannerState
}
