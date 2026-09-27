package com.company.admob.config.remote

import com.company.admob.AdFormat
import com.company.admob.BannerSize
import com.company.admob.NativeStyle
import com.company.admob.config.PreloadStrategy
import kotlin.time.Duration

/**
 * One set of remote overrides. Null or empty fields leave the local configuration unchanged. Immutable.
 *
 * @property adsEnabled false turns all ads off; null leaves them as configured.
 * @property disabledFormats formats to turn off.
 * @property placements overrides per placement key.
 * @property minIntervalBetweenFullScreenAds replaces the global full-screen interval.
 * @property maxInterstitialsPerSession replaces `FrequencyOptions.maxInterstitialsPerSession`.
 */
public class AdOverrides(
    public val adsEnabled: Boolean? = null,
    public val disabledFormats: Set<AdFormat> = emptySet(),
    public val placements: Map<String, PlacementOverride> = emptyMap(),
    public val minIntervalBetweenFullScreenAds: Duration? = null,
    public val maxInterstitialsPerSession: Int? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is AdOverrides && other.adsEnabled == adsEnabled && other.disabledFormats == disabledFormats &&
            other.placements == placements &&
            other.minIntervalBetweenFullScreenAds == minIntervalBetweenFullScreenAds &&
            other.maxInterstitialsPerSession == maxInterstitialsPerSession

    override fun hashCode(): Int {
        var result = adsEnabled.hashCode()
        result = 31 * result + disabledFormats.hashCode()
        result = 31 * result + placements.hashCode()
        result = 31 * result + minIntervalBetweenFullScreenAds.hashCode()
        return 31 * result + maxInterstitialsPerSession.hashCode()
    }

    override fun toString(): String =
        "AdOverrides(adsEnabled=$adsEnabled, disabledFormats=$disabledFormats, placements=$placements, " +
            "minIntervalBetweenFullScreenAds=$minIntervalBetweenFullScreenAds, " +
            "maxInterstitialsPerSession=$maxInterstitialsPerSession)"
}

/**
 * Remote overrides for one placement. Null fields leave the local configuration unchanged. Immutable.
 *
 * @property enabled turns the placement on or off.
 * @property minInterval replaces the placement's minimum interval.
 * @property maxPerSession replaces the placement's session cap.
 * @property preload replaces the placement's preload strategy.
 * @property adUnitId replaces the placement's ad units with this one. Validated like the local one; a demo ID is
 *   rejected in PRODUCTION. [adUnitIds] wins when both are set.
 * @property adUnitIds replaces the placement's ad units, in rotation order. Invalid entries are dropped with a
 *   warning; a list left empty is ignored.
 * @property counter replaces the placement's counter (`FrequencyRule.counter`).
 * @property bannerSize replaces a banner placement's size, for banners created afterwards.
 * @property nativeStyle replaces a native placement's style, for native ads rendered afterwards.
 * @property splashTimeout replaces a splash interstitial's `splashTimeout`.
 */
public class PlacementOverride(
    public val enabled: Boolean? = null,
    public val minInterval: Duration? = null,
    public val maxPerSession: Int? = null,
    public val preload: PreloadStrategy? = null,
    public val adUnitId: String? = null,
    public val adUnitIds: List<String>? = null,
    public val counter: Int? = null,
    public val bannerSize: BannerSize? = null,
    public val nativeStyle: NativeStyle? = null,
    public val splashTimeout: Duration? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is PlacementOverride && other.enabled == enabled && other.minInterval == minInterval &&
            other.maxPerSession == maxPerSession && other.preload == preload && other.adUnitId == adUnitId &&
            other.adUnitIds == adUnitIds && other.counter == counter && other.bannerSize == bannerSize &&
            other.nativeStyle == nativeStyle && other.splashTimeout == splashTimeout

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + minInterval.hashCode()
        result = 31 * result + maxPerSession.hashCode()
        result = 31 * result + preload.hashCode()
        result = 31 * result + adUnitId.hashCode()
        result = 31 * result + adUnitIds.hashCode()
        result = 31 * result + counter.hashCode()
        result = 31 * result + bannerSize.hashCode()
        result = 31 * result + nativeStyle.hashCode()
        return 31 * result + splashTimeout.hashCode()
    }

    override fun toString(): String =
        "PlacementOverride(enabled=$enabled, minInterval=$minInterval, maxPerSession=$maxPerSession, " +
            "preload=$preload, adUnitId=$adUnitId, adUnitIds=$adUnitIds, counter=$counter, bannerSize=$bannerSize, " +
            "nativeStyle=$nativeStyle, splashTimeout=$splashTimeout)"

    /** This override with its ad units replaced by [ids] (null removes them), keeping every other field. */
    internal fun withAdUnitIds(ids: List<String>?): PlacementOverride = PlacementOverride(
        enabled, minInterval, maxPerSession, preload, adUnitId = null, adUnitIds = ids, counter, bannerSize,
        nativeStyle, splashTimeout,
    )

    /** The ad units this override sets, in rotation order, or null when it sets none. */
    internal val effectiveAdUnitIds: List<String>? get() = adUnitIds ?: adUnitId?.let(::listOf)
}
