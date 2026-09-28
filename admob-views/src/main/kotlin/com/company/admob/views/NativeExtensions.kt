package com.company.admob.views

import com.company.admob.InternalCompanyAdMobApi
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.company.admob.AdError
import com.company.admob.NativeAdHandle
import com.company.admob.NativeAdHandleState
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdRenderer
import com.company.admob.NativeAdResult
import com.company.admob.NativePlacement
import com.company.admob.views.internal.ViewsRuntime
import kotlinx.coroutines.launch

/**
 * Loads a native ad for [placement] and renders it into this container with [layout], replacing its children. With
 * no [layout], the template matching the placement's configured style is used ([NativeTemplates.forStyle]), so the
 * configuration (or Firebase) can change it. The
 * ad is destroyed when [owner] is destroyed; if the load fails, or ads become blocked, the container is GONE.
 * [onResult] is called once, on the main thread, after rendering.
 *
 * In a Fragment, pass `viewLifecycleOwner`. In a `RecyclerView`, don't call this from `onBindViewHolder`: keep a
 * [NativeAdHolder] per ad position in the screen's ViewModel instead, so scrolling and rotation don't reload.
 *
 * One ad per container and placement: while this container shows an ad from an earlier call for [placement], or one
 * is loading, another call does nothing (and [onResult] isn't called). To replace a shown ad with a new one, use
 * [AdNativeView.load] or [NativeAdHolder.load] with `refresh = true`. Main thread only.
 */
@JvmOverloads
public fun ViewGroup.attachNativeAd(
    owner: LifecycleOwner,
    placement: NativePlacement,
    layout: NativeAdLayout? = null,
    renderer: NativeAdRenderer? = null,
    onResult: (NativeAdResult) -> Unit = {},
) {
    val container = this
    val attached = getTag(R.id.company_admob_attached_native) as? AttachedNative
    if (attached != null && attached.placement == placement && attached.busy) {
        ViewsRuntime.debug("attachNativeAd(${placement.key}): this container already has its ad, or it's loading; not requesting another")
        return
    }
    val record = AttachedNative(placement).also { setTag(R.id.company_admob_attached_native, it) }
    val resolvedLayout = layout ?: styledLayout(placement)
    showNativeShimmer(container, placement, resolvedLayout)
    ViewsRuntime.client.native.load(placement) { result ->
        record.loading = false
        record.handle = (result as? NativeAdResult.Loaded)?.handle
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            (result as? NativeAdResult.Loaded)?.handle?.destroy()
            return@load
        }
        val shown = when (result) {
            is NativeAdResult.Loaded -> renderInto(container, result.handle, owner, resolvedLayout, renderer)
            is NativeAdResult.Failed -> {
                removeNativeShimmer(container)
                container.visibility = View.GONE
                result
            }
        }
        onResult(shown)
    }
}

/** What [attachNativeAd] put in a container, so a second call for the same placement doesn't request another ad. */
private class AttachedNative(val placement: NativePlacement) {
    var loading = true
    var handle: NativeAdHandle? = null

    /** Loading, or showing a live ad. */
    val busy: Boolean get() = loading || handle?.isDestroyed == false
}

/**
 * Fills [container] with a shimmer shaped like [layout] while [placement]'s ad loads, when the ad's `showLoading` is
 * on (PROMPT 29C). Rendering the ad replaces it; [removeNativeShimmer] removes it on failure.
 */
@OptIn(InternalCompanyAdMobApi::class)
internal fun showNativeShimmer(container: ViewGroup, placement: NativePlacement, layout: NativeAdLayout) {
    if (!ViewsRuntime.showsLoading(placement.key)) return
    val style = NativeTemplates.styleOf(layout) ?: ViewsRuntime.client.native.style(placement)
    container.removeAllViews()
    container.addView(
        AdShimmerView(container.context).apply { showNative(style) },
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
    container.visibility = View.VISIBLE
}

/** Removes a shimmer left by [showNativeShimmer], if any. */
@OptIn(InternalCompanyAdMobApi::class)
internal fun removeNativeShimmer(container: ViewGroup) {
    for (i in container.childCount - 1 downTo 0) {
        if (container.getChildAt(i) is AdShimmerView) container.removeViewAt(i)
    }
}

/** The template for [placement]'s configured style. */
internal fun styledLayout(placement: NativePlacement): NativeAdLayout =
    NativeTemplates.forStyle(ViewsRuntime.client.native.style(placement))

/**
 * Renders [handle] into [container], destroys it on [owner]'s ON_DESTROY, and hides [container] if ads become
 * blocked. A layout without the required headline view is a programmer error, but it isn't allowed to crash from an
 * async callback: it's logged and reported as `Failed(InvalidConfiguration)`.
 */
internal fun renderInto(
    container: ViewGroup,
    handle: NativeAdHandle,
    owner: LifecycleOwner,
    layout: NativeAdLayout,
    renderer: NativeAdRenderer?,
    destroyWithOwner: Boolean = true,
): NativeAdResult {
    try {
        ViewsRuntime.client.native.render(handle, container, layout, renderer)
    } catch (e: IllegalArgumentException) {
        ViewsRuntime.error("Native ad ${handle.placement.key} not rendered: ${e.message}")
        handle.destroy()
        container.visibility = View.GONE
        return NativeAdResult.Failed(AdError.InvalidConfiguration(e.message ?: "invalid native ad layout"))
    }
    container.visibility = View.VISIBLE
    if (destroyWithOwner) {
        owner.lifecycle.addObserver(
            object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    if (event != Lifecycle.Event.ON_DESTROY) return
                    source.lifecycle.removeObserver(this)
                    handle.destroy()
                }
            },
        )
    }
    owner.lifecycleScope.launch {
        handle.state.collect { if (it is NativeAdHandleState.Blocked) container.visibility = View.GONE }
    }
    return NativeAdResult.Loaded(handle)
}
