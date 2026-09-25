package com.company.admob.config.remote

import com.company.admob.AdFormat
import com.company.admob.config.PreloadStrategy
import kotlin.time.Duration

/**
 * One set of remote overrides. Null or empty fields leave the local configuration unchanged. Immutable.
 *
 * @property adsEnabled false turns all ads off; null leaves them as configured.
 * @property disabledFormats formats to turn off.
 * @property placements overrides per placement key.
 * @property minIntervalBetweenFullScreenAds replaces the global full-screen interval.
 */
public class AdOverrides(
    public val adsEnabled: Boolean? = null,
    public val disabledFormats: Set<AdFormat> = emptySet(),
    public val placements: Map<String, PlacementOverride> = emptyMap(),
    public val minIntervalBetweenFullScreenAds: Duration? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is AdOverrides && other.adsEnabled == adsEnabled && other.disabledFormats == disabledFormats &&
            other.placements == placements && other.minIntervalBetweenFullScreenAds == minIntervalBetweenFullScreenAds

    override fun hashCode(): Int {
        var result = adsEnabled.hashCode()
        result = 31 * result + disabledFormats.hashCode()
        result = 31 * result + placements.hashCode()
        return 31 * result + minIntervalBetweenFullScreenAds.hashCode()
    }

    override fun toString(): String =
        "AdOverrides(adsEnabled=$adsEnabled, disabledFormats=$disabledFormats, placements=$placements, " +
            "minIntervalBetweenFullScreenAds=$minIntervalBetweenFullScreenAds)"
}

/**
 * Remote overrides for one placement. Null fields leave the local configuration unchanged. Immutable.
 *
 * @property enabled turns the placement on or off.
 * @property minInterval replaces the placement's minimum interval.
 * @property maxPerSession replaces the placement's session cap.
 * @property preload replaces the placement's preload strategy.
 * @property adUnitId replaces the placement's ad unit. Validated like the local one; a demo ID is rejected in
 *   PRODUCTION.
 */
public class PlacementOverride(
    public val enabled: Boolean? = null,
    public val minInterval: Duration? = null,
    public val maxPerSession: Int? = null,
    public val preload: PreloadStrategy? = null,
    public val adUnitId: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is PlacementOverride && other.enabled == enabled && other.minInterval == minInterval &&
            other.maxPerSession == maxPerSession && other.preload == preload && other.adUnitId == adUnitId

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + minInterval.hashCode()
        result = 31 * result + maxPerSession.hashCode()
        result = 31 * result + preload.hashCode()
        return 31 * result + adUnitId.hashCode()
    }

    override fun toString(): String =
        "PlacementOverride(enabled=$enabled, minInterval=$minInterval, maxPerSession=$maxPerSession, " +
            "preload=$preload, adUnitId=$adUnitId)"
}
