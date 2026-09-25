package com.company.admob.views

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.company.admob.CompanyAdMobInternal
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.NativeAdHandle
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdRenderer
import com.company.admob.NativeAdResult
import com.company.admob.NativePlacement
import com.company.admob.views.internal.ViewsRuntime

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

    /**
     * Renders the held ad into [container], or loads one first if there's none. [container] is GONE while no ad can
     * be shown.
     */
    @JvmOverloads
    public fun bindTo(
        container: ViewGroup,
        owner: LifecycleOwner,
        layout: NativeAdLayout = NativeTemplates.Medium,
        renderer: NativeAdRenderer? = null,
    ) {
        unbind()
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) onOwnerDestroyed() }
        val newTarget = Target(container, owner, layout, renderer, observer)
        target = newTarget
        owner.lifecycle.addObserver(observer)

        val held = handle?.takeUnless { it.isDestroyed }
        if (held != null) {
            render(newTarget, held)
        } else {
            handle = null
            load()
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

    private fun load() {
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
                }
                is NativeAdResult.Failed -> target?.container?.visibility = View.GONE
            }
        }
    }

    private fun render(target: Target, ad: NativeAdHandle) {
        val result = renderInto(target.container, ad, target.owner, target.layout, target.renderer, destroyWithOwner = false)
        if (result is NativeAdResult.Failed) handle = null
    }

    /** The screen is gone: keep the ad, but drop its views and every reference to them. */
    @OptIn(InternalCompanyAdMobApi::class)
    private fun onOwnerDestroyed() {
        handle?.let(CompanyAdMobInternal::detachRenderedView)
        unbind()
    }

    private fun unbind() {
        val current = target ?: return
        target = null
        current.owner.lifecycle.removeObserver(current.observer)
    }
}
