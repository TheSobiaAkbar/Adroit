package io.github.thesobiaakbar.adroit.config.remote

import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.config.PreloadStrategy
import io.github.thesobiaakbar.adroit.config.SplashTimings
import io.github.thesobiaakbar.adroit.internal.config.AdBehavior
import io.github.thesobiaakbar.adroit.internal.config.CounterPlan
import kotlin.time.Duration

/**
 * One set of remote overrides. Null or empty fields leave the local configuration unchanged. Immutable.
 *
 * @property adsEnabled false turns all ads off; null leaves them as configured.
 * @property disabledFormats formats to turn off.
 * @property placements overrides per placement key.
 * @property minIntervalBetweenFullScreenAds replaces the global full-screen interval.
 * @property maxInterstitialsPerSession replaces `FrequencyOptions.maxInterstitialsPerSession`.
 * @property splash replaces the splash timings it sets (docs/SPLASH.md); null leaves them all.
 */
public class AdOverrides(
    public val adsEnabled: Boolean? = null,
    public val disabledFormats: Set<AdFormat> = emptySet(),
    public val placements: Map<String, PlacementOverride> = emptyMap(),
    public val minIntervalBetweenFullScreenAds: Duration? = null,
    public val maxInterstitialsPerSession: Int? = null,
) {
    /** See the class description. Set only by the constructor that takes it; immutable afterwards. */
    public var splash: SplashTimings? = null
        private set

    /**
     * Overrides that also set [splash]. The other parameters are those of the primary constructor, which is kept
     * without [splash] so code compiled against 1.0.0-rc1 keeps working.
     */
    public constructor(
        splash: SplashTimings?,
        adsEnabled: Boolean? = null,
        disabledFormats: Set<AdFormat> = emptySet(),
        placements: Map<String, PlacementOverride> = emptyMap(),
        minIntervalBetweenFullScreenAds: Duration? = null,
        maxInterstitialsPerSession: Int? = null,
    ) : this(adsEnabled, disabledFormats, placements, minIntervalBetweenFullScreenAds, maxInterstitialsPerSession) {
        this.splash = splash
    }

    /** The `AdSettings` loading fields from Firebase (docs/AD_PROFILES.md). Set only by the library's parser. */
    internal var behavior: AdBehavior? = null

    override fun equals(other: Any?): Boolean =
        other is AdOverrides && other.adsEnabled == adsEnabled && other.disabledFormats == disabledFormats &&
            other.placements == placements &&
            other.minIntervalBetweenFullScreenAds == minIntervalBetweenFullScreenAds &&
            other.maxInterstitialsPerSession == maxInterstitialsPerSession && other.splash == splash &&
            other.behavior == behavior

    override fun hashCode(): Int {
        var result = adsEnabled.hashCode()
        result = 31 * result + disabledFormats.hashCode()
        result = 31 * result + placements.hashCode()
        result = 31 * result + minIntervalBetweenFullScreenAds.hashCode()
        result = 31 * result + maxInterstitialsPerSession.hashCode()
        result = 31 * result + splash.hashCode()
        return 31 * result + behavior.hashCode()
    }

    override fun toString(): String =
        "AdOverrides(adsEnabled=$adsEnabled, disabledFormats=$disabledFormats, placements=$placements, " +
            "minIntervalBetweenFullScreenAds=$minIntervalBetweenFullScreenAds, " +
            "maxInterstitialsPerSession=$maxInterstitialsPerSession, splash=$splash, behavior=$behavior)"
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
 * @property format swaps the placement to its partner format: `INTERSTITIAL` ↔ `APP_OPEN`, `BANNER` ↔ `NATIVE`
 *   (docs/AD_PROFILES.md, "Swapping formats"). The same override should carry [adUnitIds] of the new format; without
 *   them, PRODUCTION ignores the swap and other environments use Google's test ad unit. Its own format, or null,
 *   changes nothing; any other format is rejected.
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
    /** See the class description. Set only by the constructor that takes it; immutable afterwards. */
    public var format: AdFormat? = null
        private set

    /**
     * An ad profile's `"loadAgain"` from its Firebase value (docs/AD_PROFILES.md): replaces the ad's own. Set only by
     * the library's parser, right after construction.
     */
    internal var loadAgain: Boolean? = null

    /** An ad profile's loading fields from its Firebase value. Set only by the library's parser. */
    internal var behavior: AdBehavior? = null

    /**
     * An ad profile's counter fields from its Firebase value (`counter` as a list, `counterStart`, `counterStep`,
     * `counterResetOnFail`, `loadAhead`); each replaces the ad's own. Set only by the library's parser.
     */
    internal var counterPlan: CounterPlan? = null

    /**
     * An ad profile's `"style"` naming a registered app layout instead of a [NativeStyle]; it replaces the ad's own
     * style or layout. Set only by the library's parser.
     */
    internal var nativeLayoutName: String? = null

    /**
     * An override that may swap the placement's [format]. The other parameters are those of the primary constructor,
     * which is kept without [format] so code compiled against 1.0.0-rc1 keeps working.
     */
    public constructor(
        format: AdFormat?,
        enabled: Boolean? = null,
        minInterval: Duration? = null,
        maxPerSession: Int? = null,
        preload: PreloadStrategy? = null,
        adUnitId: String? = null,
        adUnitIds: List<String>? = null,
        counter: Int? = null,
        bannerSize: BannerSize? = null,
        nativeStyle: NativeStyle? = null,
        splashTimeout: Duration? = null,
    ) : this(enabled, minInterval, maxPerSession, preload, adUnitId, adUnitIds, counter, bannerSize, nativeStyle, splashTimeout) {
        this.format = format
    }

    override fun equals(other: Any?): Boolean =
        other is PlacementOverride && other.enabled == enabled && other.minInterval == minInterval &&
            other.maxPerSession == maxPerSession && other.preload == preload && other.adUnitId == adUnitId &&
            other.adUnitIds == adUnitIds && other.counter == counter && other.bannerSize == bannerSize &&
            other.nativeStyle == nativeStyle && other.splashTimeout == splashTimeout && other.format == format &&
            other.loadAgain == loadAgain && other.behavior == behavior && other.nativeLayoutName == nativeLayoutName &&
            other.counterPlan == counterPlan

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
        result = 31 * result + splashTimeout.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + loadAgain.hashCode()
        result = 31 * result + nativeLayoutName.hashCode()
        result = 31 * result + counterPlan.hashCode()
        return 31 * result + behavior.hashCode()
    }

    override fun toString(): String =
        "PlacementOverride(enabled=$enabled, minInterval=$minInterval, maxPerSession=$maxPerSession, " +
            "preload=$preload, adUnitId=$adUnitId, adUnitIds=$adUnitIds, counter=$counter, bannerSize=$bannerSize, " +
            "nativeStyle=$nativeStyle, splashTimeout=$splashTimeout, format=$format, loadAgain=$loadAgain, behavior=$behavior" +
            (nativeLayoutName?.let { ", nativeLayoutName=$it" } ?: "") + (counterPlan?.let { ", counterPlan=$it" } ?: "") + ")"

    /** This override with its ad units replaced by [ids] (null removes them), keeping every other field. */
    internal fun withAdUnitIds(ids: List<String>?): PlacementOverride = PlacementOverride(
        format, enabled, minInterval, maxPerSession, preload, adUnitId = null, adUnitIds = ids, counter, bannerSize,
        nativeStyle, splashTimeout,
    ).also {
        it.loadAgain = loadAgain
        it.behavior = behavior
        it.nativeLayoutName = nativeLayoutName
        it.counterPlan = counterPlan
    }

    /** This override without its format swap, keeping every other field. */
    internal fun withoutSwap(): PlacementOverride = PlacementOverride(
        format = null, enabled, minInterval, maxPerSession, preload, adUnitId, adUnitIds, counter, bannerSize,
        nativeStyle, splashTimeout,
    ).also {
        it.loadAgain = loadAgain
        it.behavior = behavior
        it.nativeLayoutName = nativeLayoutName
        it.counterPlan = counterPlan
    }

    /** Whether it sets a native look: a style, or a registered app layout. */
    internal val setsNativeLook: Boolean get() = nativeStyle != null || nativeLayoutName != null

    /** The ad units this override sets, in rotation order, or null when it sets none. */
    internal val effectiveAdUnitIds: List<String>? get() = adUnitIds ?: adUnitId?.let(::listOf)
}
