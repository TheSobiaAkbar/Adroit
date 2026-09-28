package com.company.admob.internal.config

import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobAppId
import com.company.admob.config.AdMobConfig
import com.company.admob.config.AdUnitId
import com.company.admob.config.AppOpenPlacementConfig
import com.company.admob.AdFormat
import com.company.admob.config.DebugGeography
import com.company.admob.config.InterstitialPlacementConfig
import com.company.admob.config.NativePlacementConfig
import com.company.admob.config.RetryPolicy
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import kotlin.time.Duration

/** Identifies a configuration problem, so callers and tests don't depend on message wording. */
internal enum class ConfigIssueCode {
    // Errors in every environment.
    APP_ID_MISSING,
    APP_ID_MALFORMED,
    APP_ID_MISMATCH,
    AD_UNIT_ID_MALFORMED,
    DUPLICATE_PLACEMENT_KEY,
    FORMAT_MISMATCH,
    POOL_SIZE_OUT_OF_RANGE,
    INVALID_DURATION,
    INVALID_COUNT,
    INVALID_RETRY_POLICY,
    UNKNOWN_PLACEMENT_OVERRIDE,
    SWAP_WITHOUT_AD_UNITS,
    INVALID_AD_PROFILE,

    // Errors in PRODUCTION.
    DEMO_ID_IN_PRODUCTION,
    DEMO_AD_UNITS_IN_PRODUCTION,
    DEBUG_GEOGRAPHY_IN_PRODUCTION,

    // Errors in STAGING.
    STAGING_REAL_IDS_WITHOUT_TEST_DEVICES,

    // Warnings.
    TEST_DEVICES_IN_PRODUCTION,
    NON_PRODUCTION_IN_RELEASE_BUILD,
    PRODUCTION_IN_DEBUGGABLE_BUILD,
    NO_PLACEMENTS,
    TEST_AD_UNITS_BY_DEFAULT,
}

/** One configuration problem. [placementKey] is set when the problem belongs to a placement. */
internal data class ConfigIssue(val code: ConfigIssueCode, val message: String, val placementKey: String? = null)

/** The outcome of [ConfigValidator.validate]. The configuration is usable only when [errors] is empty. */
internal class ValidationResult(val errors: List<ConfigIssue>, val warnings: List<ConfigIssue>) {
    val isValid: Boolean get() = errors.isEmpty()

    override fun toString(): String = "ValidationResult(errors=$errors, warnings=$warnings)"
}

/** The outcome of [ConfigValidator.validateOverrides]: what may be applied, and what was dropped and why. */
internal class OverridesValidation(val accepted: AdOverrides, val issues: List<ConfigIssue>)

/**
 * Checks an [AdMobConfig] against the rules in docs/architecture/02_ARCHITECTURE.md §20.3. Never throws: the
 * caller decides what to do with errors, based on `failFast`. Pure, so it runs on any thread.
 */
internal object ConfigValidator {
    /**
     * @param manifestAppId the manifest's `com.google.android.gms.ads.APPLICATION_ID`, or null if absent.
     * @param isDebuggable whether the host app is debuggable (`ApplicationInfo.FLAG_DEBUGGABLE`).
     */
    fun validate(config: AdMobConfig, manifestAppId: String?, isDebuggable: Boolean): ValidationResult {
        val errors = mutableListOf<ConfigIssue>()
        val warnings = mutableListOf<ConfigIssue>()
        val production = config.environment == AdEnvironment.PRODUCTION

        checkAppId(config, manifestAppId, production, errors)
        config.profileIssues.forEach { errors += ConfigIssue(ConfigIssueCode.INVALID_AD_PROFILE, it) }
        checkPlacements(config, production, errors)
        if (production) {
            config.testAdUnitPlacementKeys.forEach { key ->
                warnings += ConfigIssue(
                    ConfigIssueCode.TEST_AD_UNITS_BY_DEFAULT,
                    "Placement '$key' has no ad unit IDs, so it shows Google's test ads, which earn nothing. Set its " +
                        "adUnitIds in its definition or in Firebase.",
                    key,
                )
            }
        }
        checkGlobalOptions(config, errors)

        if (production) {
            if (config.testing.useDemoAdUnits == true) {
                errors += ConfigIssue(
                    ConfigIssueCode.DEMO_AD_UNITS_IN_PRODUCTION,
                    "testing.useDemoAdUnits = true is not allowed in PRODUCTION.",
                )
            }
            if (config.privacy.debugGeography != DebugGeography.DISABLED) {
                errors += ConfigIssue(
                    ConfigIssueCode.DEBUG_GEOGRAPHY_IN_PRODUCTION,
                    "privacy.debugGeography must be DISABLED in PRODUCTION.",
                )
            }
            if (config.testing.testDeviceIds.isNotEmpty()) {
                warnings += ConfigIssue(
                    ConfigIssueCode.TEST_DEVICES_IN_PRODUCTION,
                    "testing.testDeviceIds is ignored in PRODUCTION. Google advises removing test device IDs " +
                        "before release.",
                )
            }
            if (isDebuggable) {
                warnings += ConfigIssue(
                    ConfigIssueCode.PRODUCTION_IN_DEBUGGABLE_BUILD,
                    "PRODUCTION environment in a debuggable build: live ads will be requested. Don't click them.",
                )
            }
        } else if (!isDebuggable) {
            warnings += ConfigIssue(
                ConfigIssueCode.NON_PRODUCTION_IN_RELEASE_BUILD,
                "${config.environment} environment in a non-debuggable build: only test ads will be served, " +
                    "so ads earn nothing.",
            )
        }

        if (config.environment == AdEnvironment.STAGING && config.testing.useDemoAdUnits == false &&
            config.testing.testDeviceIds.isEmpty()
        ) {
            errors += ConfigIssue(
                ConfigIssueCode.STAGING_REAL_IDS_WITHOUT_TEST_DEVICES,
                "STAGING with real ad units requires testing.testDeviceIds, so testers don't receive live ads.",
            )
        }

        if (config.placements.isEmpty()) {
            warnings += ConfigIssue(ConfigIssueCode.NO_PLACEMENTS, "No placements are configured.")
        }
        return ValidationResult(errors.toList(), warnings.toList())
    }

    private fun checkAppId(
        config: AdMobConfig,
        manifestAppId: String?,
        production: Boolean,
        errors: MutableList<ConfigIssue>,
    ) {
        val configAppId = config.appId
        if (manifestAppId == null) {
            errors += ConfigIssue(
                ConfigIssueCode.APP_ID_MISSING,
                "The manifest has no <meta-data android:name=\"${ManifestAppIdReader.META_DATA_KEY}\">. " +
                    "The Google Mobile Ads and UMP SDKs require it.",
            )
        } else if (!AdMobAppId(manifestAppId).isWellFormed) {
            errors += ConfigIssue(
                ConfigIssueCode.APP_ID_MALFORMED,
                "The manifest app ID '$manifestAppId' is not of the form ca-app-pub-<16 digits>~<10 digits>.",
            )
        }
        if (configAppId != null) {
            if (!configAppId.isWellFormed) {
                errors += ConfigIssue(
                    ConfigIssueCode.APP_ID_MALFORMED,
                    "appId '${configAppId.value}' is not of the form ca-app-pub-<16 digits>~<10 digits>.",
                )
            } else if (manifestAppId != null && manifestAppId != configAppId.value) {
                errors += ConfigIssue(
                    ConfigIssueCode.APP_ID_MISMATCH,
                    "appId '${configAppId.value}' differs from the manifest app ID '$manifestAppId'.",
                )
            }
        }
        if (production) {
            listOfNotNull(manifestAppId, configAppId?.value).distinct().filter(GoogleDemoAdUnits::isDemo).forEach {
                errors += ConfigIssue(
                    ConfigIssueCode.DEMO_ID_IN_PRODUCTION,
                    "The app ID '$it' is Google's sample app ID, which is not allowed in PRODUCTION.",
                )
            }
        }
    }

    private fun checkPlacements(config: AdMobConfig, production: Boolean, errors: MutableList<ConfigIssue>) {
        val seenKeys = mutableSetOf<String>()
        for (placementConfig in config.placements) {
            val key = placementConfig.placement.key
            fun error(code: ConfigIssueCode, message: String) {
                errors += ConfigIssue(code, "Placement '$key': $message", key)
            }

            if (!seenKeys.add(key)) {
                error(ConfigIssueCode.DUPLICATE_PLACEMENT_KEY, "the key is configured more than once.")
            }
            if (placementConfig.placement.format != placementConfig.format) {
                error(
                    ConfigIssueCode.FORMAT_MISMATCH,
                    "a ${placementConfig.placement.format} placement is configured as ${placementConfig.format}.",
                )
            }
            // An ad without IDs was given Google's test ad unit on purpose: only warned about in PRODUCTION (above).
            val testIdsByDefault = key in config.testAdUnitPlacementKeys
            for (adUnitId in placementConfig.adUnitIds) {
                adUnitIdProblem(adUnitId.value, production && !testIdsByDefault)?.let { (code, message) -> error(code, message) }
            }

            checkNotNegative(placementConfig.frequency.minInterval, "frequency.minInterval", ::error)
            if (placementConfig.frequency.maxPerSession < 0) {
                error(ConfigIssueCode.INVALID_COUNT, "frequency.maxPerSession must not be negative.")
            }
            if (placementConfig.frequency.counter < 0) {
                error(ConfigIssueCode.INVALID_COUNT, "frequency.counter must not be negative.")
            }
            placementConfig.expiry?.let { checkNotNegative(it, "expiry", ::error) }
            placementConfig.retry?.let { checkRetryPolicy(it, ::error) }

            when (placementConfig) {
                is NativePlacementConfig -> if (placementConfig.poolSize !in FormatDefaults.NATIVE_POOL_SIZE_RANGE) {
                    error(
                        ConfigIssueCode.POOL_SIZE_OUT_OF_RANGE,
                        "poolSize ${placementConfig.poolSize} is outside ${FormatDefaults.NATIVE_POOL_SIZE_RANGE}.",
                    )
                }
                is InterstitialPlacementConfig -> if (!placementConfig.splashTimeout.isPositive()) {
                    error(ConfigIssueCode.INVALID_DURATION, "splashTimeout must be positive.")
                }
                is AppOpenPlacementConfig -> {
                    checkNotNegative(placementConfig.cooldown, "cooldown", ::error)
                    checkNotNegative(placementConfig.suppressAfterFullScreenAd, "suppressAfterFullScreenAd", ::error)
                    if (placementConfig.minLaunchesBeforeFirstShow < 0) {
                        error(ConfigIssueCode.INVALID_COUNT, "minLaunchesBeforeFirstShow must not be negative.")
                    }
                }
                else -> Unit
            }
        }
    }

    /** Why [id] can't be used as an ad unit, or null when it can. */
    private fun adUnitIdProblem(id: String, production: Boolean): Pair<ConfigIssueCode, String>? = when {
        !AdUnitId(id).isWellFormed ->
            ConfigIssueCode.AD_UNIT_ID_MALFORMED to "ad unit ID '$id' is not of the form ca-app-pub-<16 digits>/<10 digits>."
        production && GoogleDemoAdUnits.isDemo(id) ->
            ConfigIssueCode.DEMO_ID_IN_PRODUCTION to
                "ad unit ID '$id' is a Google demo ad unit, which is not allowed in PRODUCTION."
        else -> null
    }

    /**
     * Checks remote [overrides] against the local [config] with the same rules (§21). Returns the overrides that may
     * be applied and the problems found: an invalid placement override is dropped as a whole, never applied in part,
     * as is an override for a key that isn't configured; an invalid global setting is dropped on its own. Ad unit
     * lists are the exception: invalid IDs are dropped one by one, and a list left empty is ignored.
     */
    fun validateOverrides(overrides: AdOverrides, config: AdMobConfig): OverridesValidation {
        val issues = mutableListOf<ConfigIssue>()
        val production = config.environment == AdEnvironment.PRODUCTION
        val formats = config.placements.associate { it.placement.key to it.format }
        val accepted = LinkedHashMap<String, PlacementOverride>()
        for ((key, override) in overrides.placements) {
            val problems = mutableListOf<ConfigIssue>()
            fun problem(code: ConfigIssueCode, message: String) {
                problems += ConfigIssue(code, "Remote override for placement '$key' ignored: $message", key)
            }
            val format = formats[key]
            if (format == null) problem(ConfigIssueCode.UNKNOWN_PLACEMENT_OVERRIDE, "no such placement is configured.")
            override.minInterval?.let { checkNotNegative(it, "minInterval", ::problem) }
            if ((override.maxPerSession ?: 0) < 0) problem(ConfigIssueCode.INVALID_COUNT, "maxPerSession must not be negative.")
            if ((override.counter ?: 0) < 0) problem(ConfigIssueCode.INVALID_COUNT, "counter must not be negative.")
            override.splashTimeout?.let {
                if (!it.isPositive()) problem(ConfigIssueCode.INVALID_DURATION, "splashTimeout must be positive.")
            }
            val swapTo = override.format?.takeIf { it != format }
            if (swapTo != null && format != null && FormatSwap.partnerOf(format) != swapTo) {
                problem(ConfigIssueCode.FORMAT_MISMATCH, "a $format placement can't become $swapTo.")
            }
            val targetFormat = swapTo ?: format
            if (override.bannerSize != null && targetFormat != null && targetFormat != AdFormat.BANNER) {
                problem(ConfigIssueCode.FORMAT_MISMATCH, "a banner size is set for a $targetFormat placement.")
            }
            if (override.nativeStyle != null && targetFormat != null && targetFormat != AdFormat.NATIVE) {
                problem(ConfigIssueCode.FORMAT_MISMATCH, "a native style is set for a $targetFormat placement.")
            }
            var checked = if (swapTo == null && override.format != null) override.withoutSwap() else override
            override.effectiveAdUnitIds?.let { ids ->
                val rejected = ids.mapNotNull { id -> adUnitIdProblem(id, production) }
                val valid = ids.filter { adUnitIdProblem(it, production) == null }
                val keeping = if (valid.isEmpty()) " Keeping the configured ad units." else " It's skipped."
                rejected.forEach { (code, message) ->
                    issues += ConfigIssue(code, "Remote override for placement '$key': $message$keeping", key)
                }
                if (valid != ids || override.adUnitId != null) checked = checked.withAdUnitIds(valid.ifEmpty { null })
            }
            // The configured ad units belong to the old format: a swap needs its own, or test ads outside PRODUCTION.
            if (swapTo != null && production && checked.effectiveAdUnitIds == null) {
                issues += ConfigIssue(
                    ConfigIssueCode.SWAP_WITHOUT_AD_UNITS,
                    "Remote override for placement '$key': the swap to $swapTo is ignored in PRODUCTION because it " +
                        "sets no valid adUnitIds of that format. Its other fields still apply.",
                    key,
                )
                checked = checked.withoutSwap()
            }
            // An override left with nothing to change (only unusable ad units) isn't applied at all.
            if (problems.isEmpty() && checked != PlacementOverride()) accepted[key] = checked else issues += problems
        }
        var globalInterval = overrides.minIntervalBetweenFullScreenAds
        if (globalInterval != null && globalInterval.isNegative()) {
            issues += ConfigIssue(
                ConfigIssueCode.INVALID_DURATION,
                "Remote minIntervalBetweenFullScreenAds ignored: it must not be negative.",
            )
            globalInterval = null
        }
        var maxInterstitials = overrides.maxInterstitialsPerSession
        if (maxInterstitials != null && maxInterstitials < 0) {
            issues += ConfigIssue(
                ConfigIssueCode.INVALID_COUNT,
                "Remote maxInterstitialsPerSession ignored: it must not be negative.",
            )
            maxInterstitials = null
        }
        val result = AdOverrides(overrides.splash, overrides.adsEnabled, overrides.disabledFormats, accepted, globalInterval, maxInterstitials)
        return OverridesValidation(result, issues.toList())
    }

    private fun checkGlobalOptions(config: AdMobConfig, errors: MutableList<ConfigIssue>) {
        fun error(code: ConfigIssueCode, message: String) {
            errors += ConfigIssue(code, message)
        }
        checkNotNegative(config.frequency.minIntervalBetweenFullScreenAds, "frequency.minIntervalBetweenFullScreenAds", ::error)
        if (config.frequency.maxInterstitialsPerSession < 0) {
            error(ConfigIssueCode.INVALID_COUNT, "frequency.maxInterstitialsPerSession must not be negative.")
        }
        if (!config.advanced.showTimeout.isPositive()) {
            error(ConfigIssueCode.INVALID_DURATION, "advanced.showTimeout must be positive.")
        }
    }

    private fun checkNotNegative(value: Duration, name: String, error: (ConfigIssueCode, String) -> Unit) {
        if (value.isNegative()) error(ConfigIssueCode.INVALID_DURATION, "$name must not be negative.")
    }

    private fun checkRetryPolicy(policy: RetryPolicy, error: (ConfigIssueCode, String) -> Unit) {
        checkNotNegative(policy.initialDelay, "retry.initialDelay", error)
        checkNotNegative(policy.maxDelay, "retry.maxDelay", error)
        if (policy.maxAttempts < 0) error(ConfigIssueCode.INVALID_RETRY_POLICY, "retry.maxAttempts must not be negative.")
        if (policy.multiplier.isNaN() || policy.multiplier < 1.0) {
            error(ConfigIssueCode.INVALID_RETRY_POLICY, "retry.multiplier must be at least 1.")
        }
        if (policy.jitterRatio !in 0.0..1.0) {
            error(ConfigIssueCode.INVALID_RETRY_POLICY, "retry.jitterRatio must be in 0.0..1.0.")
        }
    }
}
