package com.company.admob.compose.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.NativeAdLayout
import com.company.admob.NativeStyle
import com.company.admob.views.AdShimmerView
import com.company.admob.views.NativeTemplates

/** The shimmer of a loading banner [heightPx] tall (PROMPT 29C). */
@OptIn(InternalCompanyAdMobApi::class)
@Composable
internal fun BannerShimmer(heightPx: Int, modifier: Modifier = Modifier) {
    AndroidView(factory = { AdShimmerView(it) }, modifier = modifier, update = { it.showBanner(heightPx) })
}

/** The shimmer of a loading native ad in [style]'s template. */
@OptIn(InternalCompanyAdMobApi::class)
@Composable
internal fun NativeShimmer(style: NativeStyle, modifier: Modifier = Modifier) {
    AndroidView(factory = { AdShimmerView(it) }, modifier = modifier, update = { it.showNative(style) })
}

/** The style [layout] is the template of, or null for an app's own layout. */
internal fun styleOf(layout: NativeAdLayout): NativeStyle? = when (layout) {
    NativeTemplates.Small -> NativeStyle.SMALL
    NativeTemplates.Medium -> NativeStyle.MEDIUM
    NativeTemplates.Large -> NativeStyle.LARGE
    else -> null
}
