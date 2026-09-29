package com.company.admob.compose

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isNotEmpty
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.NativeAdHandle
import com.company.admob.NativeAdHandleState
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdRenderer
import com.company.admob.NativeAdResult
import com.company.admob.NativePlacement
import com.company.admob.compose.internal.ComposeRuntime
import com.company.admob.compose.internal.NativeShimmer
import com.company.admob.compose.internal.layoutFor
import com.company.admob.views.NativeTemplates

/**
 * A native ad for [placement], rendered with [layout] (one of [NativeTemplates], or your own `NativeAdLayout`). With no
 * [layout], the placement's configured layout is used ([NativeTemplates.forPlacement]: its style's template, or a registered app layout), so the
 * configuration (or Firebase) can change it.
 *
 * The ad comes from [rememberNativeAd]: it's loaded once per ([placement], [slotKey]) and kept by the screen's
 * ViewModel, so recomposition, rotation and scrolling out of a lazy list don't reload it; after a configuration
 * change the same ad is rendered into the new view. In a `LazyColumn`, give each ad item a distinct, stable
 * [slotKey] (for example the item key).
 *
 * @param modifier applied to the ad's slot.
 * @param renderer styles the bound views after the standard binding; used when the ad is rendered.
 * @param placeholder shown while the ad loads. Without one, an ad profile with `showLoading` (the default) shows a
 *   shimmer shaped like its template.
 *
 * Shows nothing if the load fails, or once ads are blocked (ad-free, consent withdrawn). A layout without a headline
 * view is logged as an error and shows nothing.
 */
@Composable
public fun AdNative(
    placement: NativePlacement,
    modifier: Modifier = Modifier,
    layout: NativeAdLayout? = null,
    slotKey: Any = Unit,
    renderer: NativeAdRenderer? = null,
    placeholder: (@Composable () -> Unit)? = null,
) {
    AdNative(placement, modifier, layout, slotKey, renderer, placeholder, refreshKey = null)
}

/**
 * [AdNative], with [refreshKey]: each time it changes after the first composition, the slot asks for a new ad. A
 * shown ad stays on screen (no shimmer) until the new one arrives and replaces it; if the new one fails, the shown
 * one stays. A slot without an ad (its load failed) loads again, and one still loading is left alone. Without a
 * change of [refreshKey], the slot never requests a second ad: recomposition, rotation and scrolling reuse its ad.
 */
@Composable
public fun AdNative(
    placement: NativePlacement,
    modifier: Modifier = Modifier,
    layout: NativeAdLayout? = null,
    slotKey: Any = Unit,
    renderer: NativeAdRenderer? = null,
    placeholder: (@Composable () -> Unit)? = null,
    refreshKey: Any?,
) {
    val retainer = rememberNativeAdRetainer()
    var attempt by remember(placement, slotKey) { mutableIntStateOf(0) }
    val lastRefreshKey = remember(placement, slotKey) { arrayOf(refreshKey) }
    LaunchedEffect(placement, slotKey, refreshKey) {
        if (lastRefreshKey[0] == refreshKey) return@LaunchedEffect
        lastRefreshKey[0] = refreshKey
        if (!retainer.refresh(placement, slotKey)) attempt++
    }
    val result by rememberNativeAd(placement, slotKey, retainer, attempt)
    when (val current = result) {
        null -> when {
            placeholder != null -> Box(modifier) { placeholder() }
            ComposeRuntime.showsLoading(placement.key) -> NativeShimmer(layout ?: layoutFor(placement), modifier)
        }
        is NativeAdResult.Loaded -> RenderedNativeAd(
            current.handle,
            modifier,
            layout ?: layoutFor(placement),
            renderer,
            onReplaced = retainer::onReplaced,
        )
        is NativeAdResult.Failed -> Unit
    }
}

/** What a container shows; it re-renders only when this changes (a new handle or layout), never on recomposition. */
private data class Rendered(val handle: NativeAdHandle, val layout: NativeAdLayout)

@Composable
private fun RenderedNativeAd(
    handle: NativeAdHandle,
    modifier: Modifier,
    layout: NativeAdLayout,
    renderer: NativeAdRenderer?,
    onReplaced: (NativeAdHandle) -> Unit,
) {
    val handleState by handle.state.collectAsStateWithLifecycle()
    if (handleState != NativeAdHandleState.Active) return
    val currentRenderer by rememberUpdatedState(renderer)
    AndroidView(
        factory = { context -> FrameLayout(context) },
        modifier = modifier,
        update = { container ->
            val rendered = Rendered(handle, layout)
            val previous = container.tag as? Rendered
            if (previous != rendered) {
                container.tag = rendered
                render(container, rendered, currentRenderer)
                // A refreshed ad is drawn: the one it replaced can go.
                if (previous != null && previous.handle !== handle) onReplaced(previous.handle)
            }
        },
        onRelease = { container ->
            // The retained handle must not keep this screen's views: drop them, keeping the ad. A container that
            // was emptied already (the ad was rendered elsewhere since) is left alone.
            val rendered = container.tag as? Rendered
            container.tag = null
            if (rendered != null && container.isNotEmpty()) ComposeRuntime.detachRenderedView(rendered.handle)
        },
    )
}

private fun render(container: FrameLayout, rendered: Rendered, renderer: NativeAdRenderer?) {
    try {
        ComposeRuntime.client.native.render(rendered.handle, container, rendered.layout, renderer)
    } catch (e: IllegalArgumentException) {
        ComposeRuntime.error("AdNative(${rendered.handle.placement.key}): ${e.message}")
        container.removeAllViews()
    } catch (e: IllegalStateException) {
        // Destroyed or blocked between the state check and the render; the next state emission removes the slot.
        ComposeRuntime.debug("AdNative(${rendered.handle.placement.key}): not rendered: ${e.message}")
        container.removeAllViews()
    }
}
