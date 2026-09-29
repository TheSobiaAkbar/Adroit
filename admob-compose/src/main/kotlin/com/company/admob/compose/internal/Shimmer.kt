package com.company.admob.compose.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.company.admob.CompanyAdMobInternal
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.NativeAdLayout
import com.company.admob.NativePlacement
import com.company.admob.views.AdShimmerView
import com.company.admob.views.NativeTemplates

/** The shimmer of a loading banner [heightPx] tall (PROMPT 29C). */
@OptIn(InternalCompanyAdMobApi::class)
@Composable
internal fun BannerShimmer(heightPx: Int, modifier: Modifier = Modifier) {
    AndroidView(factory = { AdShimmerView(it) }, modifier = modifier, update = { it.showBanner(heightPx) })
}

/** The shimmer of a loading native ad shown in [layout], built from the layout's own views. */
@OptIn(InternalCompanyAdMobApi::class)
@Composable
internal fun NativeShimmer(layout: NativeAdLayout, modifier: Modifier = Modifier) {
    AndroidView(factory = { AdShimmerView(it) }, modifier = modifier, update = { it.showLayout(layout) })
}

/**
 * The layout [placement]'s ads show in now: the app layout registered under the name its ad profile or Firebase
 * gives, or its style's template (as `NativeTemplates.forPlacement`, with this module's client).
 */
@OptIn(InternalCompanyAdMobApi::class)
internal fun layoutFor(placement: NativePlacement): NativeAdLayout =
    CompanyAdMobInternal.nativeLayoutName(placement.key)?.let(NativeTemplates::registered)
        ?: NativeTemplates.forStyle(ComposeRuntime.client.native.style(placement))
