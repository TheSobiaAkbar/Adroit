package io.github.thesobiaakbar.adroit.internal.config

import io.github.thesobiaakbar.adroit.AdFormat

/**
 * The formats an ad may swap to from a remote value (docs/AD_PROFILES.md, "Swapping formats"): interstitial and app
 * open, banner and native. Rewarded formats never swap: they carry a reward the user chose to watch for.
 */
internal object FormatSwap {
    fun partnerOf(format: AdFormat): AdFormat? = when (format) {
        AdFormat.INTERSTITIAL -> AdFormat.APP_OPEN
        AdFormat.APP_OPEN -> AdFormat.INTERSTITIAL
        AdFormat.BANNER -> AdFormat.NATIVE
        AdFormat.NATIVE -> AdFormat.BANNER
        AdFormat.REWARDED, AdFormat.REWARDED_INTERSTITIAL -> null
    }
}
