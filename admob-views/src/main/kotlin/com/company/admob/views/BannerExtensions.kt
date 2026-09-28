package com.company.admob.views

import com.company.admob.InternalCompanyAdMobApi
import android.widget.FrameLayout
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.company.admob.BannerAdHandle
import com.company.admob.BannerOptions
import com.company.admob.BannerPlacement
import com.company.admob.BannerState
import com.company.admob.views.internal.ViewsRuntime
import com.company.admob.views.internal.findActivity
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
 * @throws IllegalStateException if this container isn't inside an Activity.
 */
@JvmOverloads
public fun ViewGroup.attachBanner(
    owner: LifecycleOwner,
    placement: BannerPlacement,
    options: BannerOptions = BannerOptions(),
): BannerAdHandle = attachBanner(owner, placement, options, collapseWhenEmpty = true)

@OptIn(InternalCompanyAdMobApi::class)
internal fun ViewGroup.attachBanner(
    owner: LifecycleOwner,
    placement: BannerPlacement,
    options: BannerOptions,
    collapseWhenEmpty: Boolean,
    onState: (BannerState) -> Unit = {},
): BannerAdHandle {
    val activity = checkNotNull(context.findActivity()) { "attachBanner(): the container isn't inside an Activity" }
    val handle = ViewsRuntime.client.banner.create(activity, placement, options)
    addView(handle.view)
    val shimmer = if (ViewsRuntime.showsLoading(placement.key)) addBannerShimmer(handle) else null
    handle.bindTo(owner)
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            handle.state.collect { state ->
                if (collapseWhenEmpty) handle.view.visibility = if (state.hasNoAd()) View.GONE else View.VISIBLE
                shimmer?.visibility = if (state == BannerState.Loading) View.VISIBLE else View.GONE
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
@OptIn(InternalCompanyAdMobApi::class)
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
