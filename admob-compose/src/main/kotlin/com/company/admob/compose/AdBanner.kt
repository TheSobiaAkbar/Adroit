package com.company.admob.compose

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.BannerAdHandle
import com.company.admob.BannerOptions
import com.company.admob.BannerPlacement
import com.company.admob.BannerState
import com.company.admob.compose.internal.ComposeRuntime
import com.company.admob.compose.internal.findActivity

/**
 * A banner ad for [placement].
 *
 * One banner is created per ([placement], [options], Activity) and reused across recompositions; it's destroyed when
 * this leaves the composition or one of those changes. Recomposition never reloads it. Banners belong in fixed
 * slots: in a lazy list, every item scrolled back in creates a new banner, so prefer [AdNative] there.
 *
 * @param modifier applied to the banner's slot, which fills the available width.
 * @param options the banner size, and whether to reserve its expected height while it loads.
 * @param placeholder shown over the reserved space while the banner is loading.
 * @param onStateChange called on the main thread with each new [BannerState].
 *
 * Without an Activity in `LocalContext`, logs an error and shows nothing.
 */
@Composable
public fun AdBanner(
    placement: BannerPlacement,
    modifier: Modifier = Modifier,
    options: BannerOptions = BannerOptions(),
    placeholder: (@Composable () -> Unit)? = null,
    onStateChange: (BannerState) -> Unit = {},
) {
    val activity = LocalContext.current.findActivity()
    if (activity == null) {
        LaunchedEffect(placement) {
            ComposeRuntime.error("AdBanner(${placement.key}): no Activity in LocalContext; not showing a banner")
        }
        return
    }
    val handle = remember(placement, activity, options) { RememberedBanner(activity, placement, options) }.handle
    val state by handle.state.collectAsStateWithLifecycle()
    val currentOnStateChange by rememberUpdatedState(onStateChange)
    LaunchedEffect(handle, state) { currentOnStateChange(state) }

    val loading = state == BannerState.Loading
    val showing = loading || state == BannerState.Loaded
    val reserved = if (options.reserveSpace && showing) with(LocalDensity.current) { handle.expectedHeightPx.toDp() } else 0.dp
    val collapsed = !options.reserveSpace && !showing
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = reserved)
            .then(if (collapsed) Modifier.height(0.dp) else Modifier),
    ) {
        AndroidView(
            // The handle's own view, never a new AdView: recomposition and re-attachment reuse the same banner.
            factory = { handle.view.also { (it.parent as? ViewGroup)?.removeView(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (loading && placeholder != null) {
            Box(Modifier.matchParentSize()) { placeholder() }
        }
    }
}

/**
 * Creates the banner once it's remembered and destroys it when forgotten, or if the composition that created it is
 * abandoned (which a `DisposableEffect` would miss).
 */
private class RememberedBanner(
    activity: Activity,
    placement: BannerPlacement,
    options: BannerOptions,
) : RememberObserver {
    val handle: BannerAdHandle = ComposeRuntime.client.banner.create(activity, placement, options)

    override fun onRemembered() = Unit

    override fun onForgotten() {
        handle.destroy()
    }

    override fun onAbandoned() {
        handle.destroy()
    }
}
