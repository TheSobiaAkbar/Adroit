package io.github.thesobiaakbar.adroit.compose.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.thesobiaakbar.adroit.AdroitInternal
import io.github.thesobiaakbar.adroit.InternalAdroitApi
import io.github.thesobiaakbar.adroit.NativeAdLayout
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.views.AdShimmerView
import io.github.thesobiaakbar.adroit.views.NativeTemplates

/** The shimmer of a loading banner [heightPx] tall (PROMPT 29C). */
@OptIn(InternalAdroitApi::class)
@Composable
internal fun BannerShimmer(heightPx: Int, modifier: Modifier = Modifier) {
    AndroidView(factory = { AdShimmerView(it) }, modifier = modifier, update = { it.showBanner(heightPx) })
}

/** The shimmer of a loading native ad shown in [layout], built from the layout's own views. */
@OptIn(InternalAdroitApi::class)
@Composable
internal fun NativeShimmer(layout: NativeAdLayout, modifier: Modifier = Modifier) {
    AndroidView(factory = { AdShimmerView(it) }, modifier = modifier, update = { it.showLayout(layout) })
}

/**
 * The layout [placement]'s ads show in now: the app layout registered under the name its ad profile or Firebase
 * gives, or its style's template (as `NativeTemplates.forPlacement`, with this module's client).
 */
@OptIn(InternalAdroitApi::class)
internal fun layoutFor(placement: NativePlacement): NativeAdLayout =
    AdroitInternal.nativeLayoutName(placement.key)?.let(NativeTemplates::registered)
        ?: NativeTemplates.forStyle(ComposeRuntime.client.native.style(placement))
