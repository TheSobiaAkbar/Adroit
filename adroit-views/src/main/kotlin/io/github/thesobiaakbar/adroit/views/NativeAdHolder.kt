package io.github.thesobiaakbar.adroit.views

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.thesobiaakbar.adroit.AdroitInternal
import io.github.thesobiaakbar.adroit.InternalAdroitApi
import io.github.thesobiaakbar.adroit.NativeAdHandle
import io.github.thesobiaakbar.adroit.NativeAdLayout
import io.github.thesobiaakbar.adroit.NativeAdRenderer
import io.github.thesobiaakbar.adroit.NativeAdResult
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.views.internal.ViewsRuntime

/**
 * Keeps one native ad across configuration changes. Create it in a ViewModel, call [bindTo] from the screen each
 * time its views are (re)created, and [clear] in `onCleared`:
 * ```kotlin
 * class FeedViewModel : ViewModel() {
 *     val ad = NativeAdHolder(Placements.FeedNative)
 *     override fun onCleared() = ad.clear()
 * }
 * // Fragment.onViewCreated:
 * viewModel.ad.bindTo(binding.adContainer, viewLifecycleOwner)
 * ```
 * After a rotation the same ad is rendered again, without a new load. Between binds the holder keeps no View or
 * Context: when the bound owner is destroyed, the ad is detached from its views but not destroyed. For a list, keep
 * one holder per ad position. Main thread only.
 */
public class NativeAdHolder(private val placement: NativePlacement) {
    /** Where to render: set by [bindTo], dropped when that owner is destroyed. */
    private class Target(
        val container: ViewGroup,
        val owner: LifecycleOwner,
        val layout: NativeAdLayout,
        val renderer: NativeAdRenderer?,
        val observer: LifecycleEventObserver,
    )

    private var handle: NativeAdHandle? = null
    private var target: Target? = null
    private var loading = false

    /** Bumped by [clear], so a load that completes afterwards is destroyed instead of kept. */
    private var generation = 0

    /** For `attachNativeAd`: called once with the next result shown (rendered, or failed), then dropped. */
    internal var onNextResult: ((NativeAdResult) -> Unit)? = null

    private fun deliver(result: NativeAdResult) {
        val listener = onNextResult ?: return
        onNextResult = null
        listener(result)
    }

    /**
     * Renders the held ad into [container], or loads one first if there's none. [container] is GONE while no ad can
     * be shown. With no [layout], the template matching the placement's configured style is used.
     */
    @JvmOverloads
    public fun bindTo(
        container: ViewGroup,
        owner: LifecycleOwner,
        layout: NativeAdLayout? = null,
        renderer: NativeAdRenderer? = null,
    ) {
        unbind()
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) onOwnerDestroyed() }
        val newTarget = Target(container, owner, layout ?: styledLayout(placement), renderer, observer)
        target = newTarget
        owner.lifecycle.addObserver(observer)

        val held = handle?.takeUnless { it.isDestroyed }
        if (held != null) {
            render(newTarget, held)
            if (handle != null) deliver(NativeAdResult.Loaded(held))
        } else {
            handle = null
            // Also after a rotation during the load: the new container shows the shimmer until the ad arrives.
            showNativeShimmer(container, placement, newTarget.layout)
            request(replacing = null)
        }
    }

    /** Destroys the held ad. The holder can be bound again afterwards, which loads a new ad. */
    public fun clear() {
        generation++
        loading = false
        unbind()
        handle?.destroy()
        handle = null
    }

    /**
     * Asks for an ad. It never requests a second one while the holder has an ad or one is loading: the call does
     * nothing then. It requests one when the holder has none (the last load failed, or [clear] was called); a bound
     * container shows it when it arrives.
     *
     * With [refresh], a held ad is replaced by a new one: the new ad loads in the background while the current one
     * stays on screen (no shimmer), and is rendered in its place once it arrives. If it fails, the current ad stays.
     * A refresh while an ad is loading does nothing. Main thread only.
     */
    @JvmOverloads
    public fun load(refresh: Boolean = false) {
        if (loading) return
        val held = handle?.takeUnless { it.isDestroyed }
        if (held != null && !refresh) return
        if (held == null) target?.let { showNativeShimmer(it.container, placement, it.layout) }
        request(replacing = held)
    }

    private fun request(replacing: NativeAdHandle?) {
        if (loading) return
        loading = true
        val loadGeneration = generation
        ViewsRuntime.client.native.load(placement) { result ->
            if (loadGeneration != generation) {
                (result as? NativeAdResult.Loaded)?.handle?.destroy()
                return@load
            }
            loading = false
            when (result) {
                is NativeAdResult.Loaded -> {
                    handle = result.handle
                    target?.let { render(it, result.handle) }
                    deliver(result)
                    // Only now: the old ad stayed on screen until its replacement arrived.
                    if (replacing != null && replacing !== result.handle) replacing.destroy()
                }
                is NativeAdResult.Failed -> if (replacing == null) {
                    target?.container?.let {
                        removeNativeShimmer(it)
                        it.visibility = View.GONE
                    }
                    deliver(result)
                } else {
                    ViewsRuntime.debug("NativeAdHolder(${placement.key}): refresh not loaded; keeping the current ad")
                }
            }
        }
    }

    private fun render(target: Target, ad: NativeAdHandle) {
        val result = renderInto(target.container, ad, target.owner, target.layout, target.renderer, destroyWithOwner = false)
        if (result is NativeAdResult.Failed) handle = null
    }

    /** The screen is gone: keep the ad, but drop its views and every reference to them. */
    @OptIn(InternalAdroitApi::class)
    private fun onOwnerDestroyed() {
        handle?.let(AdroitInternal::detachRenderedView)
        unbind()
    }

    private fun unbind() {
        val current = target ?: return
        target = null
        current.owner.lifecycle.removeObserver(current.observer)
    }
}
