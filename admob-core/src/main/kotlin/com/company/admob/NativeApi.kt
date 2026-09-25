package com.company.admob

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import java.io.Closeable

/** Native ads. Obtain it from [AdsClient.native]. Most apps use the `admob-views` or `admob-compose` helpers. */
public interface NativeApi {
    /** Loads a native ad into the placement's small pool ahead of time. Never blocks. */
    public fun preload(placement: NativePlacement)

    /**
     * Loads a native ad for [placement], from the pool if one is ready. [onResult] is called exactly once, on the
     * main thread. The caller owns the returned handle and must destroy it.
     */
    public fun load(placement: NativePlacement, onResult: (NativeAdResult) -> Unit)

    /** Suspending form of [load]. The caller owns the returned handle and must destroy it. */
    public suspend fun load(placement: NativePlacement): NativeAdResult

    /**
     * Renders [handle] into [container] using your [layout], replacing the container's children, and returns the
     * ad view. [renderer] may style the bound views; it must not add click listeners. Main thread only.
     */
    public fun render(
        handle: NativeAdHandle,
        container: ViewGroup,
        layout: NativeAdLayout,
        renderer: NativeAdRenderer? = null,
    ): View
}

/**
 * A loaded native ad. Main thread only. Whoever holds it must call [destroy] when it's no longer shown
 * (`close()` does the same).
 */
public interface NativeAdHandle : Closeable {
    /** The placement it was loaded for. */
    public val placement: NativePlacement

    /** The ad's content. */
    public val assets: NativeAdAssets

    /** True after [destroy]. */
    public val isDestroyed: Boolean

    /** Releases the ad. Safe to call more than once. */
    public fun destroy()

    /** Same as [destroy]. */
    override fun close(): Unit = destroy()
}

/**
 * The content of a native ad. [headline] is always present; other assets depend on the ad.
 * Immutable, with identity equality. New properties may be added in minor versions.
 */
public class NativeAdAssets(
    public val headline: String,
    public val body: String?,
    public val callToAction: String?,
    public val advertiser: String?,
    public val starRating: Double?,
    public val price: String?,
    public val store: String?,
    public val icon: Drawable?,
    public val hasVideo: Boolean,
    public val mediaAspectRatio: Float?,
) {
    override fun toString(): String =
        "NativeAdAssets(headline=$headline, body=$body, callToAction=$callToAction, advertiser=$advertiser, " +
            "starRating=$starRating, price=$price, store=$store, icon=${icon != null}, hasVideo=$hasVideo, " +
            "mediaAspectRatio=$mediaAspectRatio)"
}

/**
 * Your native ad layout and the IDs of its asset views. The layout uses ordinary views only; CompanyAdMob wraps it
 * in Google's native ad view and binds the assets. An ID of 0 means "not in this layout". Immutable.
 *
 * Policy: include a visible ad attribution label ([attributionId]), leave room in a corner for the AdChoices icon
 * that the SDK adds, and don't set click listeners on the asset views. [mediaContainerId] must be an empty
 * `FrameLayout`; the media view (image or video) is added into it.
 */
public class NativeAdLayout(
    @param:LayoutRes public val layoutRes: Int,
    @param:IdRes public val headlineId: Int,
    @param:IdRes public val bodyId: Int = 0,
    @param:IdRes public val callToActionId: Int = 0,
    @param:IdRes public val iconId: Int = 0,
    @param:IdRes public val mediaContainerId: Int = 0,
    @param:IdRes public val advertiserId: Int = 0,
    @param:IdRes public val starRatingId: Int = 0,
    @param:IdRes public val priceId: Int = 0,
    @param:IdRes public val storeId: Int = 0,
    @param:IdRes public val attributionId: Int = 0,
) {
    private val ids: List<Int>
        get() = listOf(
            layoutRes, headlineId, bodyId, callToActionId, iconId, mediaContainerId,
            advertiserId, starRatingId, priceId, storeId, attributionId,
        )

    override fun equals(other: Any?): Boolean = other is NativeAdLayout && other.ids == ids

    override fun hashCode(): Int = ids.hashCode()

    override fun toString(): String = "NativeAdLayout(layoutRes=$layoutRes, headlineId=$headlineId)"
}

/** Customizes a rendered native ad after the standard binding, for example to style views. Main thread. */
public fun interface NativeAdRenderer {
    /** [root] is the rendered ad view. Don't add click listeners. */
    public fun onBind(root: View, assets: NativeAdAssets)
}
