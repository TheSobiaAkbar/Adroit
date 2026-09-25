package com.company.admob.internal.fullscreen

import com.company.admob.AdFormat

/**
 * What differs between the full-screen formats that share [FullScreenAdEngine]. Deliberately small: TTL and retry
 * defaults are already resolved per placement in `EffectivePlacement`, and global frequency membership lives in
 * `FrequencyGate`. Rewarded formats (Prompt 09) add their reward handling through the engine's `show` hooks and map
 * the `FullScreenResult` to a `RewardResult` in their API layer.
 */
internal interface FullScreenFormatStrategy {
    val format: AdFormat

    object Interstitial : FullScreenFormatStrategy {
        override val format: AdFormat get() = AdFormat.INTERSTITIAL
    }
}
