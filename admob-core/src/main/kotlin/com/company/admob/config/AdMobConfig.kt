package com.company.admob.config

import android.content.Context
import androidx.annotation.RawRes
import com.company.admob.AppOpenPlacement
import com.company.admob.BannerPlacement
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement
import com.company.admob.config.remote.AdConfigProvider
import com.company.admob.config.remote.StaticConfigProvider
import com.company.admob.events.AdEventListener
import com.company.admob.internal.config.AdDefinitionJson
import com.company.admob.internal.config.AdBehavior
import com.company.admob.internal.config.AdProfiles
import com.company.admob.internal.config.toReadOnlyList

/**
 * Builds an [AdMobConfig]:
 *
 * ```
 * adMobConfig {
 *   environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
 *   privacy { maxAdContentRating = MaxAdContentRating.T }
 *   placements {
 *     interstitial(Placements.LevelComplete, adUnitId = "ca-app-pub-...") { frequency { minInterval = 60.seconds } }
 *   }
 * }
 * ```
 *
 * Building never throws. The configuration is validated when it's passed to `CompanyAdMob.initialize`.
 */
public fun adMobConfig(block: AdMobConfig.Builder.() -> Unit): AdMobConfig = AdMobConfig.Builder().apply(block).build()

/**
 * The complete, immutable CompanyAdMob configuration. Create it with [adMobConfig] (Kotlin) or [Builder] (Java).
 * Two configurations are equal when all their settings are equal; listeners, loggers, providers and customizers
 * are compared by identity.
 *
 * @property environment decides which ad units are used and which mistakes are fatal. See [AdEnvironment].
 * @property appId the AdMob app ID, or null to use the one in the manifest. If set, it must match the manifest.
 * @property testing test-ad settings.
 * @property privacy privacy settings.
 * @property frequency app-wide frequency settings.
 * @property logging logging settings.
 * @property advanced rarely needed settings.
 * @property placements the configured placements, in declaration order.
 * @property eventListeners listeners that receive every ad event, in registration order.
 * @property remoteConfig the source of remote overrides. [StaticConfigProvider] (no overrides) by default.
 */
public class AdMobConfig internal constructor(builder: Builder) {
    // The ads file, if any: its placements follow the DSL's, and its settings replace the frequency options it sets.
    private val profile: AdProfiles.ParsedFile? = builder.profileText?.let(AdProfiles::parseFile)

    public val environment: AdEnvironment = builder.environment
    public val appId: AdMobAppId? = builder.appId?.let(::AdMobAppId)
    public val testing: TestingOptions = builder.testing.build()
    public val privacy: PrivacyOptions = builder.privacy.build()
    public val frequency: FrequencyOptions = profile?.let { file ->
        FrequencyOptions(
            minIntervalBetweenFullScreenAds = file.settings.minIntervalBetweenFullScreenAds,
            includeRewardedInGlobalInterval = builder.frequency.includeRewardedInGlobalInterval,
            maxInterstitialsPerSession =
                file.settings.maxInterstitialsPerSession ?: builder.frequency.maxInterstitialsPerSession,
        )
    } ?: builder.frequency.build()
    public val logging: LoggingOptions = builder.logging.build()
    public val advanced: AdvancedOptions = builder.advanced.build()
    public val placements: List<PlacementConfig> =
        (builder.placements.configs + profile?.placements.orEmpty()).toReadOnlyList()
    public val eventListeners: List<AdEventListener> = builder.eventListeners.toReadOnlyList()
    public val remoteConfig: AdConfigProvider = builder.remoteConfig

    /** The splash timings from the ads file or definitions' settings; defaults without them. */
    internal val splashTimings: SplashTimings = profile?.settings?.splash ?: SplashTimings()

    /** Whether ads are on before any remote override: the ads file's `settings.adsEnabled`, true without one. */
    internal val adsEnabledByDefault: Boolean = profile?.settings?.adsEnabled ?: true

    /** Each ad definition's JSON (and the settings'), by Firebase parameter name, when built from definitions. */
    internal val definitionJson: Map<String, String> = builder.definitionJson.orEmpty()

    /** Placements with no ad unit IDs in their profile, which use Google's test ad unit. */
    internal val testAdUnitPlacementKeys: Set<String> = profile?.testIdKeys.orEmpty()

    /** Ad profiles with `"loadAgain": true`: loaded again right after each show. */
    internal val loadAgainPlacementKeys: Set<String> = profile?.loadAgainKeys.orEmpty()

    /** Every ad defined as a profile (enum or ads file): the ones with loading behaviour (PROMPT 29). */
    internal val profilePlacementKeys: Set<String> = profile?.placements?.map { it.placement.key }.orEmpty().toSet()

    /** Each profile's own loading behaviour, and the settings' default for all of them. */
    internal val profileBehaviors: Map<String, AdBehavior> = profile?.behaviors.orEmpty()
    internal val settingsBehavior: AdBehavior? = profile?.settings?.behavior

    /** Problems in the ads file (reading or parsing). Validation reports them as errors. */
    internal val profileIssues: List<String> = listOfNotNull(builder.profileReadError) + profile?.issues.orEmpty()

    override fun equals(other: Any?): Boolean =
        other is AdMobConfig && other.environment == environment && other.appId == appId &&
            other.testing == testing && other.privacy == privacy && other.frequency == frequency &&
            other.logging == logging && other.advanced == advanced && other.placements == placements &&
            other.eventListeners == eventListeners && other.remoteConfig == remoteConfig &&
            other.adsEnabledByDefault == adsEnabledByDefault && other.profileIssues == profileIssues

    override fun hashCode(): Int {
        var result = environment.hashCode()
        result = 31 * result + appId.hashCode()
        result = 31 * result + testing.hashCode()
        result = 31 * result + privacy.hashCode()
        result = 31 * result + frequency.hashCode()
        result = 31 * result + logging.hashCode()
        result = 31 * result + advanced.hashCode()
        result = 31 * result + placements.hashCode()
        result = 31 * result + eventListeners.hashCode()
        result = 31 * result + remoteConfig.hashCode()
        result = 31 * result + adsEnabledByDefault.hashCode()
        return 31 * result + profileIssues.hashCode()
    }

    override fun toString(): String =
        "AdMobConfig(environment=$environment, appId=$appId, testing=$testing, privacy=$privacy, " +
            "frequency=$frequency, logging=$logging, advanced=$advanced, placements=$placements, " +
            "eventListeners=${eventListeners.size}, remoteConfig=${remoteConfig::class.java.name})"

    /**
     * Mutable builder for [AdMobConfig]. Kotlin code uses it through [adMobConfig]. Java code can chain the
     * setters without lambdas:
     *
     * ```
     * AdMobConfig config = new AdMobConfig.Builder()
     *     .environment(AdEnvironment.PRODUCTION)
     *     .addInterstitial(Placements.LEVEL_COMPLETE, "ca-app-pub-...")
     *     .build();
     * ```
     *
     * Option groups are also reachable as properties, for example `getPrivacy().setMaxAdContentRating(...)`.
     * Not thread-safe; [build] copies everything, so later changes to the builder don't affect built configs.
     */
    @AdMobConfigDsl
    public class Builder {
        /**
         * The environment. Defaults to [AdEnvironment.DEVELOPMENT], the fail-safe choice: a forgotten setting
         * shows test ads (and logs an error in non-debuggable builds) instead of requesting live ads in development.
         */
        public var environment: AdEnvironment = AdEnvironment.DEVELOPMENT

        /** The AdMob app ID, or null (the default) to use the manifest's `com.google.android.gms.ads.APPLICATION_ID`. */
        public var appId: String? = null

        /** Test-ad settings. */
        public val testing: TestingOptions.Builder = TestingOptions.Builder()

        /** Privacy settings. */
        public val privacy: PrivacyOptions.Builder = PrivacyOptions.Builder()

        /** App-wide frequency settings. */
        public val frequency: FrequencyOptions.Builder = FrequencyOptions.Builder()

        /** Logging settings. */
        public val logging: LoggingOptions.Builder = LoggingOptions.Builder()

        /** Rarely needed settings. */
        public val advanced: AdvancedOptions.Builder = AdvancedOptions.Builder()

        /** Placement declarations. */
        public val placements: PlacementsBuilder = PlacementsBuilder()

        /** Listeners that receive every ad event. */
        public val eventListeners: MutableList<AdEventListener> = mutableListOf()

        /** The source of remote overrides. */
        public var remoteConfig: AdConfigProvider = StaticConfigProvider

        internal var profileText: String? = null
        internal var profileReadError: String? = null
        internal var definitionJson: Map<String, String>? = null

        /**
         * Adds every ad in an ads file (`res/raw/ads_config.json`, see docs/AD_PROFILES.md): each entry becomes a
         * placement of its format, keyed by its name, and the file's `settings` replace the frequency options they
         * set. Each ad can then be changed from a Firebase parameter of the same name. A missing or invalid file is
         * reported when the configuration is validated, never here.
         */
        public fun profiles(context: Context, @RawRes resId: Int) {
            try {
                profiles(context.resources.openRawResource(resId).bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                profileReadError = "Ads file: resource $resId can't be read (${e.javaClass.simpleName}: ${e.message})"
            }
        }

        /** Adds every ad in an ads file given as JSON text. See the other overload. */
        public fun profiles(json: String) {
            profileText = json
            profileReadError = null
            definitionJson = null
        }

        /**
         * Adds every ad in [definitions], usually an enum's `entries` (docs/AD_PROFILES.md): each becomes a placement
         * of its default format, keyed by its `name`, with [settings] as the frequency settings. Each ad can then be
         * changed from the Firebase parameter of the same name, exactly as with an ads file, and the same validation
         * applies. Replaces an earlier `profiles` call. Outside PRODUCTION, `initialize` logs every ad's JSON (debug
         * level), ready to paste into Firebase.
         */
        @JvmOverloads
        public fun profiles(definitions: Iterable<AdDefinition>, settings: AdSettings = AdSettings()) {
            val list = definitions.toList()
            profiles(AdDefinitionJson.file(list, settings).toString())
            definitionJson = linkedMapOf<String, String>().apply {
                put(AdProfiles.DEFAULT_SETTINGS_KEY, settings.toJson())
                list.forEach { put(it.name, it.defaults.toJson()) }
            }
        }

        /** Configures [testing]. */
        public fun testing(block: TestingOptions.Builder.() -> Unit) {
            testing.apply(block)
        }

        /** Configures [privacy]. */
        public fun privacy(block: PrivacyOptions.Builder.() -> Unit) {
            privacy.apply(block)
        }

        /** Configures [frequency]. */
        public fun frequency(block: FrequencyOptions.Builder.() -> Unit) {
            frequency.apply(block)
        }

        /** Configures [logging]. */
        public fun logging(block: LoggingOptions.Builder.() -> Unit) {
            logging.apply(block)
        }

        /** Configures [advanced]. */
        public fun advanced(block: AdvancedOptions.Builder.() -> Unit) {
            advanced.apply(block)
        }

        /** Declares placements. */
        public fun placements(block: PlacementsBuilder.() -> Unit) {
            placements.apply(block)
        }

        /** Sets [environment]. For Java chaining. */
        public fun environment(environment: AdEnvironment): Builder = apply { this.environment = environment }

        /** Sets [appId]. For Java chaining. */
        public fun appId(appId: String?): Builder = apply { this.appId = appId }

        /** Adds a test device ID. For Java chaining. */
        public fun addTestDeviceId(deviceId: String): Builder = apply { testing.testDeviceIds += deviceId }

        /** Adds an event listener. For Java chaining. */
        public fun addEventListener(listener: AdEventListener): Builder = apply { eventListeners += listener }

        /** Sets [remoteConfig]. For Java chaining. */
        public fun remoteConfig(provider: AdConfigProvider): Builder = apply { remoteConfig = provider }

        /** Adds a banner placement with default settings. For Java chaining. */
        public fun addBanner(placement: BannerPlacement, adUnitId: String): Builder =
            apply { placements.banner(placement, adUnitId) }

        /** Adds an interstitial placement with default settings. For Java chaining. */
        public fun addInterstitial(placement: InterstitialPlacement, adUnitId: String): Builder =
            apply { placements.interstitial(placement, adUnitId) }

        /** Adds a rewarded placement with default settings. For Java chaining. */
        public fun addRewarded(placement: RewardedPlacement, adUnitId: String): Builder =
            apply { placements.rewarded(placement, adUnitId) }

        /** Adds a rewarded interstitial placement with default settings. For Java chaining. */
        public fun addRewardedInterstitial(placement: RewardedInterstitialPlacement, adUnitId: String): Builder =
            apply { placements.rewardedInterstitial(placement, adUnitId) }

        /** Adds an app open placement with default settings. For Java chaining. */
        public fun addAppOpen(placement: AppOpenPlacement, adUnitId: String): Builder =
            apply { placements.appOpen(placement, adUnitId) }

        /** Adds a native ad placement with default settings. For Java chaining. */
        public fun addNative(placement: NativePlacement, adUnitId: String): Builder =
            apply { placements.native(placement, adUnitId) }

        /** Creates the immutable configuration. Never throws; validation happens at initialization. */
        public fun build(): AdMobConfig = AdMobConfig(this)
    }
}
