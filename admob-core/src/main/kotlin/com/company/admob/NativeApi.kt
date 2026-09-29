package com.company.admob

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

/** Native ads. Obtain it from [AdsClient.native]. Most apps use the `admob-views` or `admob-compose` helpers. */
public interface NativeApi {
    /** Loads a native ad into the placement's small pool ahead of time. Never blocks. */
    public fun preload(placement: NativePlacement)

    /**
     * Loads a native ad for [placement], from the pool if one is ready. [onResult] is called exactly once, on the
     * main thread, and never synchronously inside this call.
     *
     * Ownership: pooled ads belong to the library until they're handed out. A handle delivered in
     * [NativeAdResult.Loaded] belongs to the caller, who must [NativeAdHandle.destroy] it when it's no longer shown
     * (the `admob-views` and `admob-compose` helpers do this for you). A failed load is not retried for this call;
     * call again later.
     */
    public fun load(placement: NativePlacement, onResult: (NativeAdResult) -> Unit)

    /**
     * Suspending form of [load]. The caller owns the returned handle and must destroy it. If the caller is cancelled
     * before the ad arrives, the ad is destroyed by the library.
     */
    public suspend fun load(placement: NativePlacement): NativeAdResult

    /**
     * Renders [handle] into [container] using your [layout], replacing the container's children, and returns the
     * ad view. [renderer] may style the bound views; it must not add click listeners. Main thread only.
     *
     * Rendering the same handle again (for example into a new container after a configuration change) first detaches
     * it from the view it was rendered into before.
     *
     * @throws IllegalArgumentException if [layout] has no view with [NativeAdLayout.headlineId].
     * @throws IllegalStateException if [handle] was destroyed or blocked.
     */
    public fun render(
        handle: NativeAdHandle,
        container: ViewGroup,
        layout: NativeAdLayout,
        renderer: NativeAdRenderer? = null,
    ): View

    /**
     * The style configured for [placement], remote override first: the look the `admob-views` and `admob-compose`
     * helpers use when the code passes no layout. [NativeStyle.DEFAULT] for a placement that isn't configured.
     * Callable from any thread.
     */
    public fun style(placement: NativePlacement): NativeStyle
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

    /** True once the ad was released, by [destroy] or because ads were blocked. */
    public val isDestroyed: Boolean

    /**
     * [NativeAdHandleState.Active] until [destroy], or until ads are blocked (ad-free, consent withdrawn), which
     * releases the ad and removes its rendered view. Emits on the main thread.
     */
    public val state: StateFlow<NativeAdHandleState>

    /** Releases the ad. Safe to call more than once. */
    public fun destroy()

    /** Same as [destroy]. */
    override fun close(): Unit = destroy()
}

/**
 * The life of a [NativeAdHandle].
 *
 * Stability: new states may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface NativeAdHandleState {
    /** The ad can be rendered. */
    public data object Active : NativeAdHandleState

    /** [NativeAdHandle.destroy] was called. */
    public data object Destroyed : NativeAdHandleState

    /** Ads were blocked for [reason]; the ad was released and its view removed. Hide the ad's slot. */
    public data class Blocked(public val reason: UnavailableReason) : NativeAdHandleState
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
 * Policy (Google's native ad guidance):
 * - Include a visible ad attribution label ([attributionId]): "Ad", "Advertisement" or "Sponsored", localized, at
 *   least 15px high and wide.
 * - Leave room in a corner for the AdChoices icon, which the SDK adds itself.
 * - Don't set click listeners on the asset views or on views over them.
 * - For video ads the media view must be at least 120x120 dp, and the window hardware accelerated.
 *
 * See https://developers.google.com/admob/android/next-gen/native/advanced and
 * https://support.google.com/admob/answer/6329638.
 *
 * [mediaContainerId] must be an empty `FrameLayout`; the media view (image or video) is added into it, center-cropped.
 * [headlineId] is required; any other asset view is optional, and hidden when the ad has no such asset.
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
