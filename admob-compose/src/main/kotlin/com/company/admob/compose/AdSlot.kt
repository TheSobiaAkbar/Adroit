package com.company.admob.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.AdFormat
import com.company.admob.AdSlots
import com.company.admob.BannerPlacement
import com.company.admob.NativePlacement
import com.company.admob.config.AdDefinition

/**
 * A banner or a native ad for [key], whichever format it has now (docs/AD_PROFILES.md, "Swapping formats"): a
 * Firebase value can swap it between banner and native, and this slot then shows the other one. Size and template
 * come from the ad's style. Shows nothing while [key] is neither, or isn't configured.
 *
 * @param modifier applied to the banner or native ad.
 * @param placeholder shown while the ad loads.
 */
@Composable
public fun AdSlot(
    key: String,
    modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null,
) {
    val formats = remember(key) { AdSlots.formatChanges(key) }
    val format by formats.collectAsStateWithLifecycle(initialValue = remember(key) { AdSlots.currentFormat(key) })
    when (format) {
        AdFormat.BANNER -> AdBanner(BannerPlacement(key), modifier, placeholder = placeholder)
        AdFormat.NATIVE -> AdNative(NativePlacement(key), modifier, placeholder = placeholder)
        else -> Unit
    }
}

/** [AdSlot] for [definition]'s ad. */
@Composable
public fun AdSlot(
    definition: AdDefinition,
    modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null,
) {
    AdSlot(definition.name, modifier, placeholder)
}
