package com.company.admob.internal.config

import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobAppId
import com.company.admob.config.AdMobConfig
import com.company.admob.config.AppOpenPlacementConfig
import com.company.admob.config.DebugGeography
import com.company.admob.config.NativePlacementConfig
import com.company.admob.config.RetryPolicy
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
}

/** One configuration problem. [placementKey] is set when the problem belongs to a placement. */
internal data class ConfigIssue(val code: ConfigIssueCode, val message: String, val placementKey: String? = null)

/** The outcome of [ConfigValidator.validate]. The configuration is usable only when [errors] is empty. */
internal class ValidationResult(val errors: List<ConfigIssue>, val warnings: List<ConfigIssue>) {
    val isValid: Boolean get() = errors.isEmpty()

    override fun toString(): String = "ValidationResult(errors=$errors, warnings=$warnings)"
}

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
        checkPlacements(config, production, errors)
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
            val adUnitId = placementConfig.adUnitId
            if (!adUnitId.isWellFormed) {
                error(
                    ConfigIssueCode.AD_UNIT_ID_MALFORMED,
                    "ad unit ID '${adUnitId.value}' is not of the form ca-app-pub-<16 digits>/<10 digits>.",
                )
            } else if (production && GoogleDemoAdUnits.isDemo(adUnitId.value)) {
                error(
                    ConfigIssueCode.DEMO_ID_IN_PRODUCTION,
                    "ad unit ID '${adUnitId.value}' is a Google demo ad unit, which is not allowed in PRODUCTION.",
                )
            }

            checkNotNegative(placementConfig.frequency.minInterval, "frequency.minInterval", ::error)
            if (placementConfig.frequency.maxPerSession < 0) {
                error(ConfigIssueCode.INVALID_COUNT, "frequency.maxPerSession must not be negative.")
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

    private fun checkGlobalOptions(config: AdMobConfig, errors: MutableList<ConfigIssue>) {
        fun error(code: ConfigIssueCode, message: String) {
            errors += ConfigIssue(code, message)
        }
        checkNotNegative(config.frequency.minIntervalBetweenFullScreenAds, "frequency.minIntervalBetweenFullScreenAds", ::error)
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
