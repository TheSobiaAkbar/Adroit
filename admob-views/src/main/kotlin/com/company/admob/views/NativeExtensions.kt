package com.company.admob.views

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
 * Loads a native ad for [placement] and renders it into this container with [layout], replacing its children. The
 * ad is destroyed when [owner] is destroyed; if the load fails, or ads become blocked, the container is GONE.
 * [onResult] is called once, on the main thread, after rendering.
 *
 * In a Fragment, pass `viewLifecycleOwner`. In a `RecyclerView`, don't call this from `onBindViewHolder`: keep a
 * [NativeAdHolder] per ad position in the screen's ViewModel instead, so scrolling and rotation don't reload.
 * Main thread only.
 */
@JvmOverloads
public fun ViewGroup.attachNativeAd(
    owner: LifecycleOwner,
    placement: NativePlacement,
    layout: NativeAdLayout = NativeTemplates.Medium,
    renderer: NativeAdRenderer? = null,
    onResult: (NativeAdResult) -> Unit = {},
) {
    val container = this
    ViewsRuntime.client.native.load(placement) { result ->
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            (result as? NativeAdResult.Loaded)?.handle?.destroy()
            return@load
        }
        val shown = when (result) {
            is NativeAdResult.Loaded -> renderInto(container, result.handle, owner, layout, renderer)
            is NativeAdResult.Failed -> {
                container.visibility = View.GONE
                result
            }
        }
        onResult(shown)
    }
}

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
