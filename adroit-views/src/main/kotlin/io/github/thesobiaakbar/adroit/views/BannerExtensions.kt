package io.github.thesobiaakbar.adroit.views

import io.github.thesobiaakbar.adroit.InternalAdroitApi
import android.widget.FrameLayout
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.thesobiaakbar.adroit.AdroitInternal
import io.github.thesobiaakbar.adroit.BannerAdHandle
import io.github.thesobiaakbar.adroit.BannerOptions
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.views.internal.ViewsRuntime
import io.github.thesobiaakbar.adroit.views.internal.findActivity
import kotlinx.coroutines.launch

/**
 * Creates a banner for [placement], adds its view to this container, and destroys it when [owner] is destroyed.
 * The banner's view is GONE while there's no ad (Hidden or Failed), so the container collapses.
 *
 * In a Fragment, pass `viewLifecycleOwner`, so the banner goes away in `onDestroyView`:
 * `binding.adContainer.attachBanner(viewLifecycleOwner, Placements.HomeBanner)`.
 *
 * Banners belong in fixed containers; don't put them in a `RecyclerView`. Main thread only.
 *
 * One banner per container and placement: calling this again while the banner this container got for [placement]
 * is loading, shown, or hidden while ads are blocked returns that banner instead of requesting another. After a
 * failed load, the failed banner is destroyed and a new one requested. To replace a shown banner with a new one, use
 * [AdBannerView.load] with `refresh = true`.
 *
 * @throws IllegalStateException if this container isn't inside an Activity.
 */
@JvmOverloads
public fun ViewGroup.attachBanner(
    owner: LifecycleOwner,
    placement: BannerPlacement,
    options: BannerOptions = BannerOptions(),
): BannerAdHandle {
    val attached = getTag(R.id.adroit_attached_banner) as? AttachedBanner
    if (attached != null && attached.placement == placement) {
        when (attached.handle.state.value) {
            BannerState.Hidden(null) -> Unit
            is BannerState.Failed -> attached.handle.destroy()
            else -> {
                ViewsRuntime.debug("attachBanner(${placement.key}): this container already has its banner; not requesting another")
                return attached.handle
            }
        }
    }
    return attachBanner(owner, placement, options, collapseWhenEmpty = true).also {
        setTag(R.id.adroit_attached_banner, AttachedBanner(placement, it))
    }
}

/** The banner [attachBanner] put in a container, so a second call for the same placement reuses it. */
private class AttachedBanner(val placement: BannerPlacement, val handle: BannerAdHandle)

/**
 * Shows [existing] (a banner its screen keeps, put back on screen with its ad) or a new banner in this container.
 * With [keep], [owner]'s destruction only takes the banner off screen (parked, its ad kept) for its screen to show
 * again; otherwise it destroys the banner.
 */
@OptIn(InternalAdroitApi::class)
internal fun ViewGroup.attachBanner(
    owner: LifecycleOwner,
    placement: BannerPlacement,
    options: BannerOptions,
    collapseWhenEmpty: Boolean,
    shimmer: Boolean = ViewsRuntime.showsLoading(placement.key),
    existing: BannerAdHandle? = null,
    keep: Boolean = false,
    onState: (BannerState) -> Unit = {},
): BannerAdHandle {
    val activity = checkNotNull(context.findActivity()) { "attachBanner(): the container isn't inside an Activity" }
    val handle = existing?.also { AdroitInternal.showBannerIn(it, activity) }
        ?: ViewsRuntime.client.banner.create(activity, placement, options)
    (handle.view.parent as? ViewGroup)?.removeView(handle.view)
    addView(handle.view)
    val shimmerView = if (shimmer) addBannerShimmer(handle) else null
    if (keep) {
        owner.lifecycle.addObserver(
            object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    if (event != Lifecycle.Event.ON_DESTROY) return
                    source.lifecycle.removeObserver(this)
                    AdroitInternal.parkBanner(handle)
                }
            },
        )
    } else {
        handle.bindTo(owner)
    }
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            handle.state.collect { state ->
                if (collapseWhenEmpty) handle.view.visibility = if (state.hasNoAd()) View.GONE else View.VISIBLE
                shimmerView?.visibility = if (state == BannerState.Loading) View.VISIBLE else View.GONE
                onState(state)
            }
        }
    }
    return handle
}

internal fun BannerState.hasNoAd(): Boolean = this is BannerState.Hidden || this is BannerState.Failed

/**
 * A shimmer over [handle]'s view while its banner loads (the ad's `showLoading`): inside the banner's own container,
 * so it's removed with the banner, and the banner stays visible, as its load needs. Sized like the banner once its
 * height is known (after its first layout), a usual banner height until then. Null if the view can't hold it.
 */
@OptIn(InternalAdroitApi::class)
internal fun addBannerShimmer(handle: BannerAdHandle): AdShimmerView? {
    val container = handle.view as? FrameLayout ?: return null
    val fallback = (AdShimmerView.DEFAULT_BANNER_HEIGHT_DP * container.resources.displayMetrics.density).toInt()
    val shimmer = AdShimmerView(container.context)
    shimmer.showBanner(handle.expectedHeightPx.takeIf { it > 0 } ?: fallback)
    container.addView(shimmer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    container.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        val height = handle.expectedHeightPx
        // Posted: a layout change inside a layout pass would be ignored.
        if (height > 0) view.post { shimmer.showBanner(height) }
    }
    return shimmer
}
