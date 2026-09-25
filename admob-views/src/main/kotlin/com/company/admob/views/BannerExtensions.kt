package com.company.admob.views

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
    handle.bindTo(owner)
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            handle.state.collect { state ->
                if (collapseWhenEmpty) handle.view.visibility = if (state.hasNoAd()) View.GONE else View.VISIBLE
                onState(state)
            }
        }
    }
    return handle
}

internal fun BannerState.hasNoAd(): Boolean = this is BannerState.Hidden || this is BannerState.Failed
