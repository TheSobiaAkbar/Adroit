package io.github.thesobiaakbar.adroit.config

import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AppOpenPlacement
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.RewardedInterstitialPlacement
import io.github.thesobiaakbar.adroit.RewardedPlacement
import io.github.thesobiaakbar.adroit.internal.config.AdBehavior
import io.github.thesobiaakbar.adroit.internal.config.AdDefinitionJson

/**
 * One ad of the app, with its default settings: usually an enum entry (docs/AD_PROFILES.md).
 *
 * ```
 * enum class AppAds(override val defaults: AdProfile) : AdDefinition {
 *     SplashAd(AdProfile.interstitial(splash = true)),
 *     HistoryAd(AdProfile.interstitial(ids = listOf("ca-app-pub-…/…"), counter = 3)),
 * }
 * ```
 *
 * [name] is the ad's key: the Firebase Remote Config parameter whose JSON value overrides [defaults], field by field.
 * An enum supplies it: the entry's name. Register every entry with `adMobConfig { profiles(AppAds.entries) }`.
 */
public interface AdDefinition {
    /** The ad's key and Firebase parameter name: letters, digits and underscores, up to 40. */
    public val name: String

    /** The values used until, and wherever, Firebase doesn't set one. */
    public val defaults: AdProfile
}

/**
 * The default settings of one ad, as written in its [AdDefinition]. Each field matches the field of the same name in
 * the ad's Firebase JSON (docs/AD_PROFILES.md). Create one with the factory for its format, for example
 * [AdProfile.interstitial]. Immutable.
 *
 * An empty [adUnitIds] list means Google's test ad unit for the format, in every environment. PRODUCTION logs a
 * warning for it, since test ads earn nothing: set the real IDs here or in Firebase before release.
 */
public sealed class AdProfile {
    /** The ad's format. */
    public abstract val format: AdFormat

    /** The ad unit IDs, used in rotation. Empty means Google's test ad unit. */
    public abstract val adUnitIds: List<String>

    /** Whether the ad is on. */
    public abstract val enabled: Boolean

    /**
     * Whether the ad loads again by itself once it has been shown: the next ad unit of [adUnitIds] is requested right
     * after each show. False (the default): after a show, the ad loads again only when the app calls
     * `AdSlots.load`. Either way, the first load is the app's `AdSlots.load` call (a native ad's view also asks for
     * one), and a failed load isn't retried. Always false for a banner, which loads with its view.
     */
    public open val loadAgain: Boolean get() = false

    /**
     * How long a show waits for this full-screen ad when it isn't loaded yet (for example on a slow network), in
     * seconds; the show then gives up (`NotShown(Timeout)`) and the app moves on. An ad that arrives later is kept
     * for the next show. 0 doesn't wait. Null uses [AdSettings.loadTimeoutSeconds]; always null for a banner or a
     * native ad.
     */
    public open val loadTimeoutSeconds: Int? get() = null

    /**
     * Whether a loading screen appears before this full-screen ad, or a shimmer while this banner or native ad loads.
     * Null uses [AdSettings.showLoading].
     */
    public open val showLoading: Boolean? get() = null

    /**
     * How long the loading screen stays up at least before this full-screen ad, in seconds, when the ad is already
     * loaded. Null uses [AdSettings.loadingSeconds]; always null for a banner or a native ad.
     */
    public open val loadingSeconds: Int? get() = null

    /**
     * An interstitial.
     *
     * @property counter 0 = off; N = shows on every Nth `show` call.
     * @property counters several max counters, used one after the other: with `[3, 2]`, the ad shows when the counter
     *   reaches 3, then when it reaches 2, then 3 again. Written as a list in `"counter"`. Empty uses [counter].
     * @property counterStart the counter's value when the session starts.
     * @property counterStep what each `show` call adds to the counter.
     * @property counterResetOnFail what happens when the ad is due and can't be shown: false tries again on the very
     *   next call, true starts counting again from 0.
     * @property loadAhead how many calls before it's due the ad is loaded: 0 = on the call it's due, 1 = one call
     *   earlier, and so on.
     * @property timerSeconds 0 = off; the minimum time between two showings of this ad. After a show, the next ad is
     *   requested when this time is over, then kept until it's shown. Ignored while a counter is set.
     * @property maxPerSession 0 = no limit; showings per app session.
     * @property splash whether this is the splash ad (not counted in the overall limit, no counter).
     * @property splashTimeoutSeconds how long a splash show waits for the ad.
     * @property loadAgain see [AdProfile.loadAgain].
     */
    public class Interstitial internal constructor(
        override val adUnitIds: List<String>,
        override val enabled: Boolean,
        public val counter: Int,
        public val timerSeconds: Int,
        public val maxPerSession: Int,
        public val splash: Boolean,
        public val splashTimeoutSeconds: Int,
        override val loadAgain: Boolean,
        override val loadTimeoutSeconds: Int?,
        override val showLoading: Boolean?,
        override val loadingSeconds: Int?,
        public val counters: List<Double> = emptyList(),
        public val counterStart: Double = 0.0,
        public val counterStep: Double = 1.0,
        public val counterResetOnFail: Boolean = false,
        public val loadAhead: Int = 1,
    ) : AdProfile() {
        override val format: AdFormat get() = AdFormat.INTERSTITIAL
    }

    /**
     * A rewarded interstitial.
     *
     * @property counter 0 = off; N = shows on every Nth `show` call.
     * @property counters several max counters, used one after the other: with `[3, 2]`, the ad shows when the counter
     *   reaches 3, then when it reaches 2, then 3 again. Written as a list in `"counter"`. Empty uses [counter].
     * @property counterStart the counter's value when the session starts.
     * @property counterStep what each `show` call adds to the counter.
     * @property counterResetOnFail what happens when the ad is due and can't be shown: false tries again on the very
     *   next call, true starts counting again from 0.
     * @property loadAhead how many calls before it's due the ad is loaded: 0 = on the call it's due, 1 = one call
     *   earlier, and so on.
     * @property timerSeconds 0 = off; the minimum time between two showings of this ad. After a show, the next ad is
     *   requested when this time is over, then kept until it's shown. Ignored while a counter is set.
     * @property maxPerSession 0 = no limit.
     * @property loadAgain see [AdProfile.loadAgain].
     */
    public class RewardedInterstitial internal constructor(
        override val adUnitIds: List<String>,
        override val enabled: Boolean,
        public val counter: Int,
        public val timerSeconds: Int,
        public val maxPerSession: Int,
        override val loadAgain: Boolean,
        override val loadTimeoutSeconds: Int?,
        override val showLoading: Boolean?,
        override val loadingSeconds: Int?,
        public val counters: List<Double> = emptyList(),
        public val counterStart: Double = 0.0,
        public val counterStep: Double = 1.0,
        public val counterResetOnFail: Boolean = false,
        public val loadAhead: Int = 1,
    ) : AdProfile() {
        override val format: AdFormat get() = AdFormat.REWARDED_INTERSTITIAL
    }

    /**
     * A rewarded ad.
     *
     * @property maxPerSession 0 = no limit.
     * @property loadAgain see [AdProfile.loadAgain].
     */
    public class Rewarded internal constructor(
        override val adUnitIds: List<String>,
        override val enabled: Boolean,
        public val maxPerSession: Int,
        override val loadAgain: Boolean,
        override val loadTimeoutSeconds: Int?,
        override val showLoading: Boolean?,
        override val loadingSeconds: Int?,
    ) : AdProfile() {
        override val format: AdFormat get() = AdFormat.REWARDED
    }

    /**
     * An app open ad.
     *
     * @property timerSeconds the cooldown between app open ads; null means none (0).
     * @property maxPerSession 0 = no limit.
     * @property excludedActivities Activity class names never covered by an app open ad, such as the splash screen.
     * @property loadAgain see [AdProfile.loadAgain]; set it to keep an app open ad for every return to the app.
     */
    public class AppOpen internal constructor(
        override val adUnitIds: List<String>,
        override val enabled: Boolean,
        public val timerSeconds: Int?,
        public val maxPerSession: Int,
        public val excludedActivities: List<String>,
        override val loadAgain: Boolean,
        override val loadTimeoutSeconds: Int?,
        override val showLoading: Boolean?,
        override val loadingSeconds: Int?,
    ) : AdProfile() {
        override val format: AdFormat get() = AdFormat.APP_OPEN
    }

    /**
     * A banner.
     *
     * @property style the banner size. An [BannerSize.InlineAdaptive] is written as `inline_adaptive`, whose maximum
     *   height is always 250 dp.
     */
    public class Banner internal constructor(
        override val adUnitIds: List<String>,
        override val enabled: Boolean,
        public val style: BannerSize,
        override val showLoading: Boolean?,
    ) : AdProfile() {
        override val format: AdFormat get() = AdFormat.BANNER
    }

    /**
     * A native ad.
     *
     * @property style the library template.
     * @property layoutName the name of an app layout registered with `NativeTemplates.register` (adroit-views), used
     *   instead of [style]; null for a library template. An unregistered name shows the [style] template.
     * @property poolSize ads kept loaded, 1 to 3.
     * @property loadAgain see [AdProfile.loadAgain]; for a native ad, "shown" means handed to a view.
     */
    public class Native internal constructor(
        override val adUnitIds: List<String>,
        override val enabled: Boolean,
        public val style: NativeStyle,
        public val poolSize: Int,
        override val loadAgain: Boolean,
        override val showLoading: Boolean?,
        public val layoutName: String? = null,
    ) : AdProfile() {
        override val format: AdFormat get() = AdFormat.NATIVE
    }

    override fun equals(other: Any?): Boolean =
        other is AdProfile && other.javaClass == javaClass && other.toJson() == toJson()

    override fun hashCode(): Int = toJson().hashCode()

    /** The profile as its Firebase JSON value, for example `{"format":"interstitial","enabled":true,…}`. */
    override fun toString(): String = toJson()

    /**
     * The profile as its Firebase JSON value: paste it as the value of the ad's Remote Config parameter. The fields
     * Firebase can't change (`splash`, `excludedActivities`, `poolSize`) are left out.
     */
    public fun toJson(): String = AdDefinitionJson.profile(this).toString()

    /** Factories, one per format. Every argument has the JSON field's default. */
    public companion object {
        /** An interstitial profile; see [Interstitial]. */
        @JvmStatic
        @JvmOverloads
        public fun interstitial(
            ids: List<String> = emptyList(),
            enabled: Boolean = true,
            counter: Int = 0,
            timerSeconds: Int = 0,
            maxPerSession: Int = 0,
            splash: Boolean = false,
            splashTimeoutSeconds: Int = 8,
            loadAgain: Boolean = false,
            loadTimeoutSeconds: Int? = null,
            showLoading: Boolean? = null,
            loadingSeconds: Int? = null,
            counters: List<Number> = emptyList(),
            counterStart: Number = 0,
            counterStep: Number = 1,
            counterResetOnFail: Boolean = false,
            loadAhead: Int = 1,
        ): Interstitial = Interstitial(
            ids.toList(), enabled, counter, timerSeconds, maxPerSession, splash, splashTimeoutSeconds, loadAgain,
            loadTimeoutSeconds, showLoading, loadingSeconds,
            counters.map { it.toDouble() }, counterStart.toDouble(), counterStep.toDouble(), counterResetOnFail, loadAhead,
        )

        /** A rewarded interstitial profile; see [RewardedInterstitial]. */
        @JvmStatic
        @JvmOverloads
        public fun rewardedInterstitial(
            ids: List<String> = emptyList(),
            enabled: Boolean = true,
            counter: Int = 0,
            timerSeconds: Int = 0,
            maxPerSession: Int = 0,
            loadAgain: Boolean = false,
            loadTimeoutSeconds: Int? = null,
            showLoading: Boolean? = null,
            loadingSeconds: Int? = null,
            counters: List<Number> = emptyList(),
            counterStart: Number = 0,
            counterStep: Number = 1,
            counterResetOnFail: Boolean = false,
            loadAhead: Int = 1,
        ): RewardedInterstitial = RewardedInterstitial(
            ids.toList(), enabled, counter, timerSeconds, maxPerSession, loadAgain, loadTimeoutSeconds, showLoading,
            loadingSeconds,
            counters.map { it.toDouble() }, counterStart.toDouble(), counterStep.toDouble(), counterResetOnFail, loadAhead,
        )

        /** A rewarded profile; see [Rewarded]. */
        @JvmStatic
        @JvmOverloads
        public fun rewarded(
            ids: List<String> = emptyList(),
            enabled: Boolean = true,
            maxPerSession: Int = 0,
            loadAgain: Boolean = false,
            loadTimeoutSeconds: Int? = null,
            showLoading: Boolean? = null,
            loadingSeconds: Int? = null,
        ): Rewarded =
            Rewarded(ids.toList(), enabled, maxPerSession, loadAgain, loadTimeoutSeconds, showLoading, loadingSeconds)

        /** An app open profile; see [AppOpen]. */
        @JvmStatic
        @JvmOverloads
        public fun appOpen(
            ids: List<String> = emptyList(),
            enabled: Boolean = true,
            timerSeconds: Int? = null,
            maxPerSession: Int = 0,
            excludedActivities: List<String> = emptyList(),
            loadAgain: Boolean = false,
            loadTimeoutSeconds: Int? = null,
            showLoading: Boolean? = null,
            loadingSeconds: Int? = null,
        ): AppOpen = AppOpen(
            ids.toList(), enabled, timerSeconds, maxPerSession, excludedActivities.toList(), loadAgain, loadTimeoutSeconds,
            showLoading, loadingSeconds,
        )

        /** A banner profile; see [Banner]. */
        @JvmStatic
        @JvmOverloads
        public fun banner(
            ids: List<String> = emptyList(),
            enabled: Boolean = true,
            style: BannerSize = BannerSize.AnchoredAdaptive,
            showLoading: Boolean? = null,
        ): Banner = Banner(ids.toList(), enabled, style, showLoading)

        /** A native profile; see [Native]. */
        @JvmStatic
        @JvmOverloads
        public fun native(
            ids: List<String> = emptyList(),
            enabled: Boolean = true,
            style: NativeStyle = NativeStyle.DEFAULT,
            poolSize: Int = 1,
            loadAgain: Boolean = false,
            showLoading: Boolean? = null,
        ): Native = Native(ids.toList(), enabled, style, poolSize, loadAgain, showLoading)

        /**
         * A native profile shown in the app's own layout, registered under [layout] with `NativeTemplates.register`
         * (adroit-views), for example `AdProfile.native(layout = "promo")`. Firebase can switch it to a library style,
         * or to another registered layout, with `"style"`. Until the name is registered, the Medium template shows.
         */
        @JvmStatic
        @JvmOverloads
        public fun native(
            layout: String,
            ids: List<String> = emptyList(),
            enabled: Boolean = true,
            poolSize: Int = 1,
            loadAgain: Boolean = false,
            showLoading: Boolean? = null,
        ): Native = Native(
            ids.toList(), enabled, NativeStyle.DEFAULT, poolSize, loadAgain, showLoading,
            layoutName = NativeLayoutNames.normalize(layout),
        )
    }
}

/**
 * The settings of every ad: the defaults of the Firebase `AdSettings` parameter (docs/AD_PROFILES.md). Immutable.
 *
 * @property adsEnabled false turns every ad off.
 * @property maxInterstitialsPerSession 0 = no limit; interstitials per session, splash ones not counted.
 * @property minSecondsBetweenFullScreenAds the minimum time between any two full-screen ads.
 * @property splashSeconds the splash timer after consent (docs/SPLASH.md).
 * @property premiumSplashSeconds the splash timer for a premium user.
 * @property offlineSplashSeconds the splash timer without internet.
 * @property consentTimeoutSeconds how long the consent form may take to appear on the splash screen.
 * @property loadTimeoutSeconds how long a show waits for a full-screen ad that isn't loaded yet; each ad can set its own.
 * @property showLoading a loading screen before full-screen ads and a shimmer while banners and native ads load; each
 *   ad can set its own.
 * @property loadingSeconds how long the loading screen stays up at least; each ad can set its own.
 */
public class AdSettings @JvmOverloads constructor(
    public val adsEnabled: Boolean = true,
    public val maxInterstitialsPerSession: Int = 0,
    public val minSecondsBetweenFullScreenAds: Int = 0,
    public val splashSeconds: Int = SplashTimings.DEFAULT_SPLASH_SECONDS,
    public val premiumSplashSeconds: Int = SplashTimings.DEFAULT_SHORT_SPLASH_SECONDS,
    public val offlineSplashSeconds: Int = SplashTimings.DEFAULT_SHORT_SPLASH_SECONDS,
    public val consentTimeoutSeconds: Int = SplashTimings.DEFAULT_CONSENT_TIMEOUT_SECONDS,
    public val loadTimeoutSeconds: Int = AdBehavior.DEFAULT_LOAD_TIMEOUT_SECONDS,
    public val showLoading: Boolean = true,
    public val loadingSeconds: Int = AdBehavior.DEFAULT_LOADING_SECONDS,
) {
    override fun equals(other: Any?): Boolean = other is AdSettings && other.toJson() == toJson()

    override fun hashCode(): Int = toJson().hashCode()

    /** The settings as their Firebase JSON value. */
    override fun toString(): String = toJson()

    /** The settings as the Firebase `AdSettings` JSON value. */
    public fun toJson(): String = AdDefinitionJson.settings(this).toString()
}

/** This ad as an interstitial placement. Throws [IllegalArgumentException] if its default format is another one. */
public fun AdDefinition.interstitialPlacement(): InterstitialPlacement = InterstitialPlacement(checkedKey(AdFormat.INTERSTITIAL))

/** This ad as a rewarded placement. Throws [IllegalArgumentException] if its default format is another one. */
public fun AdDefinition.rewardedPlacement(): RewardedPlacement = RewardedPlacement(checkedKey(AdFormat.REWARDED))

/** This ad as a rewarded interstitial placement. Throws [IllegalArgumentException] if its default format is another one. */
public fun AdDefinition.rewardedInterstitialPlacement(): RewardedInterstitialPlacement =
    RewardedInterstitialPlacement(checkedKey(AdFormat.REWARDED_INTERSTITIAL))

/** This ad as an app open placement. Throws [IllegalArgumentException] if its default format is another one. */
public fun AdDefinition.appOpenPlacement(): AppOpenPlacement = AppOpenPlacement(checkedKey(AdFormat.APP_OPEN))

/** This ad as a banner placement. Throws [IllegalArgumentException] if its default format is another one. */
public fun AdDefinition.bannerPlacement(): BannerPlacement = BannerPlacement(checkedKey(AdFormat.BANNER))

/** This ad as a native placement. Throws [IllegalArgumentException] if its default format is another one. */
public fun AdDefinition.nativePlacement(): NativePlacement = NativePlacement(checkedKey(AdFormat.NATIVE))

private fun AdDefinition.checkedKey(format: AdFormat): String {
    require(defaults.format == format) { "Ad $name is ${defaults.format}, not $format" }
    return name
}

/** Tools for [AdDefinition]s. */
public object AdDefinitions {
    /**
     * A Firebase Remote Config template holding every definition's defaults, one JSON parameter per ad named after
     * it, plus the [settingsKey] parameter with [settings]. Import it in the Firebase console (Remote Config, ⋮,
     * "Publish from a file"), or save it for the Firebase CLI. Any thread.
     */
    @JvmStatic
    @JvmOverloads
    public fun firebaseTemplate(
        definitions: Iterable<AdDefinition>,
        settings: AdSettings = AdSettings(),
        settingsKey: String = "AdSettings",
    ): String = AdDefinitionJson.firebaseTemplate(definitions, settings, settingsKey).toString(2)
}
