package com.company.admob.internal.fullscreen

import com.company.admob.AdFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * What differs between the full-screen formats that share [FullScreenAdEngine]. Deliberately small: TTL and retry
 * defaults are already resolved per placement in `EffectivePlacement`, and global frequency membership (rewarded is
 * exempt by default, rewarded interstitial isn't) lives in `FrequencyGate`. Rewards are handled by [RewardSession].
 */
internal sealed interface FullScreenFormatStrategy {
    val format: AdFormat

    /**
     * How long a dismissed show stays open for a late reward callback before it resolves (architecture §14). Some
     * mediation adapters report the reward just after dismissal, and the ad's listener is dropped once the show
     * ends, so the engine waits this long before ending it. Zero for formats without a reward.
     */
    val dismissGrace: Duration

    data object Interstitial : FullScreenFormatStrategy {
        override val format: AdFormat get() = AdFormat.INTERSTITIAL
        override val dismissGrace: Duration get() = Duration.ZERO
    }

    data class Rewarded(override val dismissGrace: Duration = DEFAULT_REWARD_GRACE) : FullScreenFormatStrategy {
        override val format: AdFormat get() = AdFormat.REWARDED
    }

    data class RewardedInterstitial(override val dismissGrace: Duration = DEFAULT_REWARD_GRACE) :
        FullScreenFormatStrategy {
        override val format: AdFormat get() = AdFormat.REWARDED_INTERSTITIAL
    }

    /** App open ads, driven by `AppOpenController` rather than a host `show` call (architecture §15). */
    data object AppOpen : FullScreenFormatStrategy {
        override val format: AdFormat get() = AdFormat.APP_OPEN
        override val dismissGrace: Duration get() = Duration.ZERO
    }

    companion object {
        /** Long enough for adapters that report the reward right after dismissal; short enough to go unnoticed. */
        val DEFAULT_REWARD_GRACE: Duration = 300.milliseconds
    }
}
