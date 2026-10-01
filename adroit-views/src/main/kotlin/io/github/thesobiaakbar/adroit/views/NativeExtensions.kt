package io.github.thesobiaakbar.adroit.views

import io.github.thesobiaakbar.adroit.InternalAdroitApi
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.NativeAdHandle
import io.github.thesobiaakbar.adroit.NativeAdHandleState
import io.github.thesobiaakbar.adroit.NativeAdLayout
import io.github.thesobiaakbar.adroit.NativeAdRenderer
import io.github.thesobiaakbar.adroit.NativeAdResult
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.views.internal.ViewAdRetainer
import io.github.thesobiaakbar.adroit.views.internal.ViewsRuntime
import kotlinx.coroutines.launch

/**
 * Loads a native ad for [placement] and renders it into this container with [layout], replacing its children. With
 * no [layout], the template matching the placement's configured style is used ([NativeTemplates.forStyle]), so the
 * configuration (or Firebase) can change it. If the load fails, or ads become blocked, the container is GONE.
 * [onResult] is called once, on the main thread, after rendering.
 *
 * A container with an `android:id` keeps its ad for its screen (a ViewModel of the view tree's
 * `ViewModelStoreOwner`: the Fragment, or the Activity): when [owner] is destroyed (a Fragment's view, or the Activity
 * on rotation) the ad leaves its views, and calling this again for the new container shows the same ad, with no new
 * request. It's destroyed when the screen is cleared. Without an id, or a `ViewModelStoreOwner`, it's destroyed with
 * [owner].
 *
 * One ad per container and placement: another call shows the same ad and asks for nothing, unless [refresh] is true.
 * With [refresh], a new ad loads while the shown one stays, replaces it once loaded, and the old one is destroyed
 * then; if it fails, the shown one stays (and [onResult] isn't called). A refresh needs the container's id.
 *
 * In a Fragment, pass `viewLifecycleOwner`. In a `RecyclerView`, don't call this from `onBindViewHolder`: keep a
 * [NativeAdHolder] per ad position in the screen's ViewModel instead. Main thread only.
 */
@JvmOverloads
public fun ViewGroup.attachNativeAd(
    owner: LifecycleOwner,
    placement: NativePlacement,
    layout: NativeAdLayout? = null,
    renderer: NativeAdRenderer? = null,
    refresh: Boolean = false,
    onResult: (NativeAdResult) -> Unit = {},
) {
    val retainer = if (id != View.NO_ID) ViewAdRetainer.of(this) else null
    if (retainer != null) {
        val slot = ViewAdRetainer.slot(placement.key, "attach:$id")
        val holder = retainer.natives.getOrPut(slot) { NativeAdHolder(placement) }
        holder.onNextResult = onResult
        holder.bindTo(this, owner, layout, renderer)
        if (refresh) holder.load(refresh = true)
        return
    }
    if (refresh) ViewsRuntime.debug("attachNativeAd(${placement.key}): refresh needs the container's android:id; ignored")
    val container = this
    val attached = getTag(R.id.adroit_attached_native) as? AttachedNative
    if (attached != null && attached.placement == placement && attached.busy) {
        ViewsRuntime.debug("attachNativeAd(${placement.key}): this container already has its ad, or it's loading; not requesting another")
        return
    }
    val record = AttachedNative(placement).also { setTag(R.id.adroit_attached_native, it) }
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
 * Fills [container] with a shimmer shaped like [layout] (whatever layout: built from its views at runtime) while
 * [placement]'s ad loads, when the ad's `showLoading` is on. Rendering the ad replaces it; [removeNativeShimmer]
 * removes it on failure.
 */
@OptIn(InternalAdroitApi::class)
internal fun showNativeShimmer(container: ViewGroup, placement: NativePlacement, layout: NativeAdLayout) {
    if (!ViewsRuntime.showsLoading(placement.key)) return
    container.removeAllViews()
    container.addView(
        AdShimmerView(container.context).apply { showLayout(layout) },
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
    container.visibility = View.VISIBLE
}

/** Removes a shimmer left by [showNativeShimmer], if any. */
@OptIn(InternalAdroitApi::class)
internal fun removeNativeShimmer(container: ViewGroup) {
    for (i in container.childCount - 1 downTo 0) {
        if (container.getChildAt(i) is AdShimmerView) container.removeViewAt(i)
    }
}

/** The layout [placement]'s ads show in now: its registered app layout, or its style's template. */
internal fun styledLayout(placement: NativePlacement): NativeAdLayout = NativeTemplates.forPlacement(placement)

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
