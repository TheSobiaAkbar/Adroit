package com.company.admob.config

import com.company.admob.AdFormat
import com.company.admob.AppOpenPlacement
import com.company.admob.BannerPlacement
import com.company.admob.BannerSize
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
import com.company.admob.NativeStyle
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement
import com.company.admob.internal.config.AdDefinitionJson

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
     * An interstitial.
     *
     * @property counter 0 = off; N = shows on every Nth `show` call.
     * @property timerSeconds 0 = off; the minimum time between two showings of this ad. Ignored while [counter] is set.
     * @property maxPerSession 0 = no limit; showings per app session.
     * @property splash whether this is the splash ad (not counted in the overall limit, no counter).
     * @property splashTimeoutSeconds how long a splash show waits for the ad.
     */
    public class Interstitial internal constructor(
        override val adUnitIds: List<String>,
        override val enabled: Boolean,
        public val counter: Int,
        public val timerSeconds: Int,
        public val maxPerSession: Int,
        public val splash: Boolean,
        public val splashTimeoutSeconds: Int,
    ) : AdProfile() {
        override val format: AdFormat get() = AdFormat.INTERSTITIAL
    }

    /**
     * A rewarded interstitial.
     *
     * @property counter 0 = off; N = shows on every Nth `show` call.
     * @property timerSeconds 0 = off; the minimum time between two showings of this ad.
     * @property maxPerSession 0 = no limit.
     */
    public class RewardedInterstitial internal constructor(
        override val adUnitIds: List<String>,
        override val enabled: Boolean,
        public val counter: Int,
        public val timerSeconds: Int,
        public val maxPerSession: Int,
    ) : AdProfile() {
        override val format: AdFormat get() = AdFormat.REWARDED_INTERSTITIAL
    }

    /**
     * A rewarded ad.
     *
     * @property maxPerSession 0 = no limit.
     */
    public class Rewarded internal constructor(
        override val adUnitIds: List<String>,
        override val enabled: Boolean,
        public val maxPerSession: Int,
    ) : AdProfile() {
        override val format: AdFormat get() = AdFormat.REWARDED
    }

    /**
     * An app open ad.
     *
     * @property timerSeconds the cooldown between app open ads; null keeps the library default (4 minutes).
     * @property maxPerSession 0 = no limit.
     * @property excludedActivities Activity class names never covered by an app open ad, such as the splash screen.
     */
    public class AppOpen internal constructor(
        override val adUnitIds: List<String>,
        override val enabled: Boolean,
        public val timerSeconds: Int?,
        public val maxPerSession: Int,
        public val excludedActivities: List<String>,
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
    ) : AdProfile() {
        override val format: AdFormat get() = AdFormat.BANNER
    }

    /**
     * A native ad.
     *
     * @property style the library template.
     * @property poolSize ads kept loaded, 1 to 3.
     */
    public class Native internal constructor(
        override val adUnitIds: List<String>,
        override val enabled: Boolean,
        public val style: NativeStyle,
        public val poolSize: Int,
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
        ): Interstitial = Interstitial(ids.toList(), enabled, counter, timerSeconds, maxPerSession, splash, splashTimeoutSeconds)

        /** A rewarded interstitial profile; see [RewardedInterstitial]. */
        @JvmStatic
        @JvmOverloads
        public fun rewardedInterstitial(
            ids: List<String> = emptyList(),
            enabled: Boolean = true,
            counter: Int = 0,
            timerSeconds: Int = 0,
            maxPerSession: Int = 0,
        ): RewardedInterstitial = RewardedInterstitial(ids.toList(), enabled, counter, timerSeconds, maxPerSession)

        /** A rewarded profile; see [Rewarded]. */
        @JvmStatic
        @JvmOverloads
        public fun rewarded(ids: List<String> = emptyList(), enabled: Boolean = true, maxPerSession: Int = 0): Rewarded =
            Rewarded(ids.toList(), enabled, maxPerSession)

        /** An app open profile; see [AppOpen]. */
        @JvmStatic
        @JvmOverloads
        public fun appOpen(
            ids: List<String> = emptyList(),
            enabled: Boolean = true,
            timerSeconds: Int? = null,
            maxPerSession: Int = 0,
            excludedActivities: List<String> = emptyList(),
        ): AppOpen = AppOpen(ids.toList(), enabled, timerSeconds, maxPerSession, excludedActivities.toList())

        /** A banner profile; see [Banner]. */
        @JvmStatic
        @JvmOverloads
        public fun banner(
            ids: List<String> = emptyList(),
            enabled: Boolean = true,
            style: BannerSize = BannerSize.AnchoredAdaptive,
        ): Banner = Banner(ids.toList(), enabled, style)

        /** A native profile; see [Native]. */
        @JvmStatic
        @JvmOverloads
        public fun native(
            ids: List<String> = emptyList(),
            enabled: Boolean = true,
            style: NativeStyle = NativeStyle.MEDIUM,
            poolSize: Int = 1,
        ): Native = Native(ids.toList(), enabled, style, poolSize)
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
 */
public class AdSettings @JvmOverloads constructor(
    public val adsEnabled: Boolean = true,
    public val maxInterstitialsPerSession: Int = 0,
    public val minSecondsBetweenFullScreenAds: Int = 0,
    public val splashSeconds: Int = SplashTimings.DEFAULT_SPLASH_SECONDS,
    public val premiumSplashSeconds: Int = SplashTimings.DEFAULT_SHORT_SPLASH_SECONDS,
    public val offlineSplashSeconds: Int = SplashTimings.DEFAULT_SHORT_SPLASH_SECONDS,
    public val consentTimeoutSeconds: Int = SplashTimings.DEFAULT_CONSENT_TIMEOUT_SECONDS,
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
