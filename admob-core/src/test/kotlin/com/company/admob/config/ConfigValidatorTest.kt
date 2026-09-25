package com.company.admob.config

import com.company.admob.AppOpenPlacement
import com.company.admob.BannerPlacement
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement
import com.company.admob.internal.config.ConfigIssueCode
import com.company.admob.internal.config.ConfigIssueCode.AD_UNIT_ID_MALFORMED
import com.company.admob.internal.config.ConfigIssueCode.APP_ID_MALFORMED
import com.company.admob.internal.config.ConfigIssueCode.APP_ID_MISMATCH
import com.company.admob.internal.config.ConfigIssueCode.APP_ID_MISSING
import com.company.admob.internal.config.ConfigIssueCode.DEBUG_GEOGRAPHY_IN_PRODUCTION
import com.company.admob.internal.config.ConfigIssueCode.DEMO_AD_UNITS_IN_PRODUCTION
import com.company.admob.internal.config.ConfigIssueCode.DEMO_ID_IN_PRODUCTION
import com.company.admob.internal.config.ConfigIssueCode.DUPLICATE_PLACEMENT_KEY
import com.company.admob.internal.config.ConfigIssueCode.FORMAT_MISMATCH
import com.company.admob.internal.config.ConfigIssueCode.INVALID_COUNT
import com.company.admob.internal.config.ConfigIssueCode.INVALID_DURATION
import com.company.admob.internal.config.ConfigIssueCode.INVALID_RETRY_POLICY
import com.company.admob.internal.config.ConfigIssueCode.NON_PRODUCTION_IN_RELEASE_BUILD
import com.company.admob.internal.config.ConfigIssueCode.NO_PLACEMENTS
import com.company.admob.internal.config.ConfigIssueCode.POOL_SIZE_OUT_OF_RANGE
import com.company.admob.internal.config.ConfigIssueCode.PRODUCTION_IN_DEBUGGABLE_BUILD
import com.company.admob.internal.config.ConfigIssueCode.STAGING_REAL_IDS_WITHOUT_TEST_DEVICES
import com.company.admob.internal.config.ConfigIssueCode.TEST_DEVICES_IN_PRODUCTION
import com.company.admob.internal.config.ConfigValidator
import com.company.admob.internal.config.GoogleDemoAdUnits
import com.company.admob.internal.config.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class ConfigValidatorTest {
    private val interstitial = InterstitialPlacement("level_complete")

    private fun validate(
        environment: AdEnvironment = AdEnvironment.PRODUCTION,
        manifestAppId: String? = TestIds.APP_ID,
        isDebuggable: Boolean = environment != AdEnvironment.PRODUCTION,
        withDefaultPlacement: Boolean = true,
        block: AdMobConfig.Builder.() -> Unit = {},
    ): ValidationResult {
        val config = adMobConfig {
            this.environment = environment
            if (withDefaultPlacement) placements { interstitial(interstitial, TestIds.unit(0)) }
            block()
        }
        return ConfigValidator.validate(config, manifestAppId, isDebuggable)
    }

    private fun ValidationResult.errorCodes(): List<ConfigIssueCode> = errors.map { it.code }

    private fun ValidationResult.warningCodes(): List<ConfigIssueCode> = warnings.map { it.code }

    private fun assertOnlyError(expected: ConfigIssueCode, result: ValidationResult) {
        assertEquals(result.toString(), listOf(expected), result.errorCodes())
    }

    // Baselines

    @Test
    fun `valid production config has no issues`() {
        val result = validate(AdEnvironment.PRODUCTION)
        assertTrue(result.toString(), result.isValid)
        assertEquals(emptyList<ConfigIssueCode>(), result.warningCodes())
    }

    @Test
    fun `valid development config has no issues`() {
        val result = validate(AdEnvironment.DEVELOPMENT)
        assertTrue(result.isValid)
        assertEquals(emptyList<ConfigIssueCode>(), result.warningCodes())
    }

    // App ID

    @Test
    fun `missing manifest app ID is an error`() {
        assertOnlyError(APP_ID_MISSING, validate(manifestAppId = null))
    }

    @Test
    fun `malformed manifest app ID is an error`() {
        assertOnlyError(APP_ID_MALFORMED, validate(manifestAppId = "ca-app-pub-1234567890123456/1234567890"))
    }

    @Test
    fun `malformed config app ID is an error`() {
        assertOnlyError(APP_ID_MALFORMED, validate { appId = "not-an-app-id" })
    }

    @Test
    fun `config app ID different from the manifest is an error`() {
        assertOnlyError(APP_ID_MISMATCH, validate { appId = "ca-app-pub-1234567890123456~9999999999" })
    }

    @Test
    fun `config app ID equal to the manifest is valid`() {
        assertTrue(validate { appId = TestIds.APP_ID }.isValid)
    }

    @Test
    fun `production config with the sample app ID fails`() {
        assertOnlyError(DEMO_ID_IN_PRODUCTION, validate(manifestAppId = GoogleDemoAdUnits.SAMPLE_APP_ID))
    }

    @Test
    fun `development config with the sample app ID is valid`() {
        assertTrue(validate(AdEnvironment.DEVELOPMENT, manifestAppId = GoogleDemoAdUnits.SAMPLE_APP_ID).isValid)
    }

    // Placements

    @Test
    fun `malformed ad unit ID is an error in every environment`() {
        AdEnvironment.entries.forEach { environment ->
            val result = validate(environment, withDefaultPlacement = false) {
                placements { interstitial(interstitial, "ca-app-pub-1234567890123456~1234567890") }
            }
            assertOnlyError(AD_UNIT_ID_MALFORMED, result)
            assertEquals("level_complete", result.errors.single().placementKey)
        }
    }

    @Test
    fun `duplicate placement key is an error`() {
        val result = validate { placements { interstitial(interstitial, TestIds.unit(1)) } }
        assertOnlyError(DUPLICATE_PLACEMENT_KEY, result)
    }

    @Test
    fun `duplicate key across formats is an error`() {
        val result = validate { placements { rewarded(RewardedPlacement("level_complete"), TestIds.unit(1)) } }
        assertOnlyError(DUPLICATE_PLACEMENT_KEY, result)
    }

    @Test
    fun `placement format different from its configuration is an error`() {
        // Unreachable through the typed DSL; the internal constructor simulates a future untyped path.
        val mismatched = InterstitialPlacementConfig(
            RewardedPlacement("extra_life"), AdUnitId(TestIds.unit(1)), true, FrequencyRule(),
            PreloadStrategy.AFTER_READY, null, null,
        )
        val config = adMobConfig { environment = AdEnvironment.PRODUCTION; placements { configs += mismatched } }
        assertOnlyError(FORMAT_MISMATCH, ConfigValidator.validate(config, TestIds.APP_ID, isDebuggable = false))
    }

    @Test
    fun `native pool size outside 1 to 3 is an error`() {
        listOf(0, 4, -1).forEach { size ->
            val result = validate { placements { native(NativePlacement("feed"), TestIds.unit(1)) { poolSize = size } } }
            assertOnlyError(POOL_SIZE_OUT_OF_RANGE, result)
        }
        (1..3).forEach { size ->
            assertTrue(validate { placements { native(NativePlacement("feed"), TestIds.unit(1)) { poolSize = size } } }.isValid)
        }
    }

    @Test
    fun `negative placement expiry is an error`() {
        assertOnlyError(INVALID_DURATION, validate(withDefaultPlacement = false) {
            placements { interstitial(interstitial, TestIds.unit(0)) { expiry = (-1).seconds } }
        })
    }

    @Test
    fun `negative placement minInterval is an error`() {
        assertOnlyError(INVALID_DURATION, validate(withDefaultPlacement = false) {
            placements { interstitial(interstitial, TestIds.unit(0)) { frequency { minInterval = (-1).seconds } } }
        })
    }

    @Test
    fun `negative maxPerSession is an error`() {
        assertOnlyError(INVALID_COUNT, validate(withDefaultPlacement = false) {
            placements { interstitial(interstitial, TestIds.unit(0)) { frequency { maxPerSession = -1 } } }
        })
    }

    @Test
    fun `negative global full-screen interval is an error`() {
        assertOnlyError(INVALID_DURATION, validate { frequency { minIntervalBetweenFullScreenAds = (-1).seconds } })
    }

    @Test
    fun `invalid app open durations and counts are errors`() {
        val placement = AppOpenPlacement("app_open")
        assertOnlyError(INVALID_DURATION, validate { placements { appOpen(placement, TestIds.unit(1)) { cooldown = (-1).seconds } } })
        assertOnlyError(INVALID_DURATION, validate {
            placements { appOpen(placement, TestIds.unit(1)) { suppressAfterFullScreenAd = (-1).seconds } }
        })
        assertOnlyError(INVALID_COUNT, validate {
            placements { appOpen(placement, TestIds.unit(1)) { minLaunchesBeforeFirstShow = -1 } }
        })
    }

    @Test
    fun `non-positive show timeout is an error`() {
        assertOnlyError(INVALID_DURATION, validate { advanced { showTimeout = 0.seconds } })
    }

    @Test
    fun `negative retry maxAttempts is an error but zero is valid`() {
        fun withAttempts(attempts: Int) = validate(withDefaultPlacement = false) {
            placements {
                interstitial(interstitial, TestIds.unit(0)) { retry = RetryPolicy(1.seconds, 2.0, 10.seconds, attempts, 0.2) }
            }
        }
        assertOnlyError(INVALID_RETRY_POLICY, withAttempts(-1))
        assertTrue(withAttempts(0).isValid)
    }

    @Test
    fun `negative retry delays are errors`() {
        val result = validate(withDefaultPlacement = false) {
            placements {
                interstitial(interstitial, TestIds.unit(0)) {
                    retry = RetryPolicy((-1).seconds, 2.0, (-1).seconds, 3, 0.2)
                }
            }
        }
        assertEquals(listOf(INVALID_DURATION, INVALID_DURATION), result.errorCodes())
    }

    @Test
    fun `retry multiplier below 1 or NaN and jitter outside 0 to 1 are errors`() {
        listOf(
            RetryPolicy(1.seconds, 0.5, 10.seconds, 3, 0.2),
            RetryPolicy(1.seconds, Double.NaN, 10.seconds, 3, 0.2),
            RetryPolicy(1.seconds, 2.0, 10.seconds, 3, 1.5),
            RetryPolicy(1.seconds, 2.0, 10.seconds, 3, -0.1),
        ).forEach { policy ->
            val result = validate(withDefaultPlacement = false) {
                placements { interstitial(interstitial, TestIds.unit(0)) { retry = policy } }
            }
            assertOnlyError(INVALID_RETRY_POLICY, result)
        }
    }

    // PRODUCTION-only rules

    @Test
    fun `production config containing a demo ID fails for every format`() {
        val result = validate(withDefaultPlacement = false) {
            placements {
                banner(BannerPlacement("b"), GoogleDemoAdUnits.ADAPTIVE_BANNER)
                banner(BannerPlacement("b_fixed"), GoogleDemoAdUnits.FIXED_SIZE_BANNER)
                interstitial(InterstitialPlacement("i"), GoogleDemoAdUnits.INTERSTITIAL)
                rewarded(RewardedPlacement("r"), GoogleDemoAdUnits.REWARDED)
                rewardedInterstitial(RewardedInterstitialPlacement("ri"), GoogleDemoAdUnits.REWARDED_INTERSTITIAL)
                appOpen(AppOpenPlacement("ao"), GoogleDemoAdUnits.APP_OPEN)
                native(NativePlacement("n"), GoogleDemoAdUnits.NATIVE)
                native(NativePlacement("n_video"), GoogleDemoAdUnits.NATIVE_VIDEO)
            }
        }
        assertEquals(List(8) { DEMO_ID_IN_PRODUCTION }, result.errorCodes())
    }

    @Test
    fun `demo IDs are allowed outside production`() {
        listOf(AdEnvironment.DEVELOPMENT, AdEnvironment.STAGING).forEach { environment ->
            val result = validate(environment, withDefaultPlacement = false) {
                placements { interstitial(interstitial, GoogleDemoAdUnits.INTERSTITIAL) }
            }
            assertTrue(result.toString(), result.isValid)
        }
    }

    @Test
    fun `useDemoAdUnits true in production is an error`() {
        assertOnlyError(DEMO_AD_UNITS_IN_PRODUCTION, validate { testing { useDemoAdUnits = true } })
    }

    @Test
    fun `debug geography in production is an error`() {
        DebugGeography.entries.filter { it != DebugGeography.DISABLED }.forEach { geography ->
            assertOnlyError(DEBUG_GEOGRAPHY_IN_PRODUCTION, validate { privacy { debugGeography = geography } })
        }
    }

    @Test
    fun `debug geography outside production is valid`() {
        assertTrue(validate(AdEnvironment.STAGING) { privacy { debugGeography = DebugGeography.EEA } }.isValid)
    }

    // STAGING

    @Test
    fun `staging with real IDs and no test devices fails`() {
        assertOnlyError(
            STAGING_REAL_IDS_WITHOUT_TEST_DEVICES,
            validate(AdEnvironment.STAGING) { testing { useDemoAdUnits = false } },
        )
    }

    @Test
    fun `staging with real IDs and test devices is valid`() {
        val result = validate(AdEnvironment.STAGING) {
            testing { useDemoAdUnits = false; testDeviceIds += "33BE2250B43518CCDA7DE426D04EE231" }
        }
        assertTrue(result.isValid)
    }

    @Test
    fun `staging with default demo ad units needs no test devices`() {
        assertTrue(validate(AdEnvironment.STAGING).isValid)
    }

    // Warnings

    @Test
    fun `test device IDs in production are a warning, not an error`() {
        val result = validate { testing { testDeviceIds += "33BE2250B43518CCDA7DE426D04EE231" } }
        assertTrue(result.isValid)
        assertEquals(listOf(TEST_DEVICES_IN_PRODUCTION), result.warningCodes())
    }

    @Test
    fun `non-production environment in a non-debuggable build is a warning`() {
        listOf(AdEnvironment.DEVELOPMENT, AdEnvironment.STAGING).forEach { environment ->
            val result = validate(environment, isDebuggable = false)
            assertTrue(result.isValid)
            assertEquals(listOf(NON_PRODUCTION_IN_RELEASE_BUILD), result.warningCodes())
        }
    }

    @Test
    fun `production in a debuggable build is a warning`() {
        val result = validate(AdEnvironment.PRODUCTION, isDebuggable = true)
        assertTrue(result.isValid)
        assertEquals(listOf(PRODUCTION_IN_DEBUGGABLE_BUILD), result.warningCodes())
    }

    @Test
    fun `zero placements is a warning`() {
        val result = validate(withDefaultPlacement = false)
        assertTrue(result.isValid)
        assertEquals(listOf(NO_PLACEMENTS), result.warningCodes())
    }

    @Test
    fun `validator collects several errors without throwing`() {
        val result = validate(manifestAppId = null) { appId = "x"; advanced { showTimeout = (-1).seconds } }
        assertEquals(listOf(APP_ID_MISSING, APP_ID_MALFORMED, INVALID_DURATION), result.errorCodes())
    }
}
