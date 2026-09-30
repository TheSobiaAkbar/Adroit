package io.github.thesobiaakbar.adroit.config

import io.github.thesobiaakbar.adroit.GoogleRequestCustomizer
import io.github.thesobiaakbar.adroit.GoogleSdkEscapeHatch
import io.github.thesobiaakbar.adroit.internal.config.FormatDefaults
import io.github.thesobiaakbar.adroit.internal.config.toReadOnlySet
import io.github.thesobiaakbar.adroit.logging.AdLogger
import io.github.thesobiaakbar.adroit.logging.LogLevel
import kotlin.time.Duration

/**
 * Test-ad settings. Set them in `adMobConfig { testing { ... } }`.
 *
 * @property testDeviceIds devices that receive test ads for real ad units. Applied in DEVELOPMENT and STAGING;
 *   ignored (with a warning) in PRODUCTION. Emulators are always test devices.
 * @property useDemoAdUnits whether STAGING uses Google's demo ad units. Null means the environment default:
 *   true in STAGING. DEVELOPMENT always uses demo ad units; PRODUCTION never does, and rejects `true`.
 */
public class TestingOptions internal constructor(
    public val testDeviceIds: Set<String>,
    public val useDemoAdUnits: Boolean?,
) {
    override fun equals(other: Any?): Boolean =
        other is TestingOptions && other.testDeviceIds == testDeviceIds && other.useDemoAdUnits == useDemoAdUnits

    override fun hashCode(): Int = 31 * testDeviceIds.hashCode() + useDemoAdUnits.hashCode()

    override fun toString(): String = "TestingOptions(testDeviceIds=$testDeviceIds, useDemoAdUnits=$useDemoAdUnits)"

    /** Mutable builder for [TestingOptions]. */
    @AdMobConfigDsl
    public class Builder internal constructor() {
        /** See [TestingOptions.testDeviceIds]. */
        public val testDeviceIds: MutableSet<String> = linkedSetOf()

        /** See [TestingOptions.useDemoAdUnits]. */
        public var useDemoAdUnits: Boolean? = null

        internal fun build(): TestingOptions = TestingOptions(testDeviceIds.toReadOnlySet(), useDemoAdUnits)
    }
}

/**
 * Privacy settings. Set them in `adMobConfig { privacy { ... } }`. Choosing the right values for your app and
 * audience is your responsibility.
 *
 * @property ageTreatment the audience's age treatment.
 * @property maxAdContentRating the maximum ad content rating, or null to leave it unset.
 * @property restrictedDataProcessing enables restricted data processing (`gad_rdp`) for US state privacy laws.
 * @property debugGeography forces UMP's geography on test devices. Must be DISABLED in PRODUCTION.
 * @property debugBuildGeography the geography UMP assumes in a debuggable build when [debugGeography] is DISABLED.
 *   Default EEA: every debug build asks for consent on the splash, as it does for a user in Europe, on an emulator
 *   and on a phone alike (the phone is made a UMP test device automatically). DISABLED uses the device's real
 *   location. Never applied in PRODUCTION or in a non-debuggable build.
 * @property consentSyncId an ID that syncs the user's consent choices across your apps (UMP's consent sync ID), or
 *   null. Use the same value for the same user in every app that shares consent. A blank value is ignored.
 */
public class PrivacyOptions internal constructor(
    public val ageTreatment: AgeTreatment,
    public val maxAdContentRating: MaxAdContentRating?,
    public val restrictedDataProcessing: Boolean,
    public val debugGeography: DebugGeography,
    public val consentSyncId: String?,
    public val debugBuildGeography: DebugGeography,
) {
    override fun equals(other: Any?): Boolean =
        other is PrivacyOptions && other.ageTreatment == ageTreatment &&
            other.maxAdContentRating == maxAdContentRating &&
            other.restrictedDataProcessing == restrictedDataProcessing && other.debugGeography == debugGeography &&
            other.consentSyncId == consentSyncId && other.debugBuildGeography == debugBuildGeography

    override fun hashCode(): Int {
        var result = ageTreatment.hashCode()
        result = 31 * result + maxAdContentRating.hashCode()
        result = 31 * result + restrictedDataProcessing.hashCode()
        result = 31 * result + debugGeography.hashCode()
        result = 31 * result + consentSyncId.hashCode()
        return 31 * result + debugBuildGeography.hashCode()
    }

    // The sync ID identifies a user, so it's only reported as set or unset.
    override fun toString(): String =
        "PrivacyOptions(ageTreatment=$ageTreatment, maxAdContentRating=$maxAdContentRating, " +
            "restrictedDataProcessing=$restrictedDataProcessing, debugGeography=$debugGeography, " +
            "consentSyncId=${if (consentSyncId == null) "null" else "<set>"}, " +
            "debugBuildGeography=$debugBuildGeography)"

    /** Mutable builder for [PrivacyOptions]. */
    @AdMobConfigDsl
    public class Builder internal constructor() {
        /** See [PrivacyOptions.ageTreatment]. */
        public var ageTreatment: AgeTreatment = AgeTreatment.UNSPECIFIED

        /** See [PrivacyOptions.maxAdContentRating]. */
        public var maxAdContentRating: MaxAdContentRating? = null

        /** See [PrivacyOptions.restrictedDataProcessing]. */
        public var restrictedDataProcessing: Boolean = false

        /** See [PrivacyOptions.debugGeography]. */
        public var debugGeography: DebugGeography = DebugGeography.DISABLED

        /** See [PrivacyOptions.consentSyncId]. */
        public var consentSyncId: String? = null

        /** See [PrivacyOptions.debugBuildGeography]. */
        public var debugBuildGeography: DebugGeography = DebugGeography.EEA

        internal fun build(): PrivacyOptions = PrivacyOptions(
            ageTreatment,
            maxAdContentRating,
            restrictedDataProcessing,
            debugGeography,
            consentSyncId?.takeIf { it.isNotBlank() },
            debugBuildGeography,
        )
    }
}

/**
 * App-wide frequency settings. Set them in `adMobConfig { frequency { ... } }`. Like [FrequencyRule], these are a
 * user-experience safeguard and don't replace AdMob's server-side frequency capping.
 *
 * @property minIntervalBetweenFullScreenAds minimum time between any two full-screen ads (interstitial, rewarded
 *   interstitial, app open). Default 30 seconds.
 * @property includeRewardedInGlobalInterval whether rewarded ads also respect that interval. Default false,
 *   because the user asks for rewarded ads.
 * @property maxInterstitialsPerSession maximum interstitials shown per process lifetime, across every interstitial
 *   placement except splash ones. It applies on top of each placement's own [FrequencyRule.maxPerSession], so the
 *   lower limit wins. Default [Int.MAX_VALUE] (no limit); must not be negative.
 */
public class FrequencyOptions internal constructor(
    public val minIntervalBetweenFullScreenAds: Duration,
    public val includeRewardedInGlobalInterval: Boolean,
    public val maxInterstitialsPerSession: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is FrequencyOptions && other.minIntervalBetweenFullScreenAds == minIntervalBetweenFullScreenAds &&
            other.includeRewardedInGlobalInterval == includeRewardedInGlobalInterval &&
            other.maxInterstitialsPerSession == maxInterstitialsPerSession

    override fun hashCode(): Int {
        var result = minIntervalBetweenFullScreenAds.hashCode()
        result = 31 * result + includeRewardedInGlobalInterval.hashCode()
        return 31 * result + maxInterstitialsPerSession
    }

    override fun toString(): String =
        "FrequencyOptions(minIntervalBetweenFullScreenAds=$minIntervalBetweenFullScreenAds, " +
            "includeRewardedInGlobalInterval=$includeRewardedInGlobalInterval, " +
            "maxInterstitialsPerSession=$maxInterstitialsPerSession)"

    /** Mutable builder for [FrequencyOptions]. */
    @AdMobConfigDsl
    public class Builder internal constructor() {
        /** See [FrequencyOptions.minIntervalBetweenFullScreenAds]. */
        public var minIntervalBetweenFullScreenAds: Duration = FormatDefaults.MIN_INTERVAL_BETWEEN_FULL_SCREEN_ADS

        /** See [FrequencyOptions.includeRewardedInGlobalInterval]. */
        public var includeRewardedInGlobalInterval: Boolean = false

        /** See [FrequencyOptions.maxInterstitialsPerSession]. */
        public var maxInterstitialsPerSession: Int = Int.MAX_VALUE

        internal fun build(): FrequencyOptions =
            FrequencyOptions(minIntervalBetweenFullScreenAds, includeRewardedInGlobalInterval, maxInterstitialsPerSession)
    }
}

/**
 * Logging settings. Set them in `adMobConfig { logging { ... } }`.
 *
 * @property level the minimum level logged, or null for the environment default: DEBUG in DEVELOPMENT and
 *   STAGING, WARN in PRODUCTION.
 * @property logger where log messages go. Defaults to logcat ([io.github.thesobiaakbar.adroit.logging.AndroidAdLogger]).
 */
public class LoggingOptions internal constructor(
    public val level: LogLevel?,
    public val logger: AdLogger,
) {
    override fun equals(other: Any?): Boolean =
        other is LoggingOptions && other.level == level && other.logger == logger

    override fun hashCode(): Int = 31 * level.hashCode() + logger.hashCode()

    override fun toString(): String = "LoggingOptions(level=$level, logger=${logger::class.java.name})"

    /** Mutable builder for [LoggingOptions]. */
    @AdMobConfigDsl
    public class Builder internal constructor() {
        /** See [LoggingOptions.level]. */
        public var level: LogLevel? = null

        /** See [LoggingOptions.logger]. */
        public var logger: AdLogger = FormatDefaults.LOGGER

        internal fun build(): LoggingOptions = LoggingOptions(level, logger)
    }
}

/**
 * Rarely needed settings. Set them in `adMobConfig { advanced { ... } }`.
 *
 * @property showTimeout how long a full-screen show may wait for the SDK to report that the ad appeared or
 *   failed, before the library gives up with a timeout. Default 5 seconds; must be positive.
 * @property failFast whether an invalid configuration throws from `initialize`. Null means the environment
 *   default: true in DEVELOPMENT and STAGING, false in PRODUCTION (ads turn off and the app keeps running).
 * @property disableSdkCrashReporting asks the Google Mobile Ads SDK not to report its own crashes.
 */
// Lint flags the escape-hatch accessors even though they propagate the marker; the opt-in is scoped to this class.
@OptIn(GoogleSdkEscapeHatch::class)
public class AdvancedOptions internal constructor(
    googleRequestCustomizer: Any?,
    public val showTimeout: Duration,
    public val failFast: Boolean?,
    public val disableSdkCrashReporting: Boolean,
) {
    // Stored untyped so this class's non-escape-hatch members don't need the opt-in.
    private val customizer: Any? = googleRequestCustomizer

    /** The request customizer, if one was set. See [GoogleRequestCustomizer]. */
    @GoogleSdkEscapeHatch
    public val googleRequestCustomizer: GoogleRequestCustomizer?
        get() = customizer as GoogleRequestCustomizer?

    override fun equals(other: Any?): Boolean =
        other is AdvancedOptions && other.customizer == customizer && other.showTimeout == showTimeout &&
            other.failFast == failFast && other.disableSdkCrashReporting == disableSdkCrashReporting

    override fun hashCode(): Int {
        var result = customizer.hashCode()
        result = 31 * result + showTimeout.hashCode()
        result = 31 * result + failFast.hashCode()
        return 31 * result + disableSdkCrashReporting.hashCode()
    }

    override fun toString(): String =
        "AdvancedOptions(googleRequestCustomizer=${if (customizer == null) "none" else "set"}, " +
            "showTimeout=$showTimeout, failFast=$failFast, disableSdkCrashReporting=$disableSdkCrashReporting)"

    /** Mutable builder for [AdvancedOptions]. */
    @OptIn(GoogleSdkEscapeHatch::class)
    @AdMobConfigDsl
    public class Builder internal constructor() {
        /** See [GoogleRequestCustomizer]. */
        @GoogleSdkEscapeHatch
        public var googleRequestCustomizer: GoogleRequestCustomizer? = null

        /** See [AdvancedOptions.showTimeout]. */
        public var showTimeout: Duration = FormatDefaults.SHOW_TIMEOUT

        /** See [AdvancedOptions.failFast]. */
        public var failFast: Boolean? = null

        /** See [AdvancedOptions.disableSdkCrashReporting]. */
        public var disableSdkCrashReporting: Boolean = false

        internal fun build(): AdvancedOptions =
            AdvancedOptions(googleRequestCustomizer, showTimeout, failFast, disableSdkCrashReporting)
    }
}
