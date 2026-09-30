package io.github.thesobiaakbar.adroit.config

import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AppOpenPlacement
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.RewardedInterstitialPlacement
import io.github.thesobiaakbar.adroit.RewardedPlacement
import io.github.thesobiaakbar.adroit.internal.config.EffectiveConfig
import io.github.thesobiaakbar.adroit.internal.config.GoogleDemoAdUnits
import io.github.thesobiaakbar.adroit.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class EffectiveConfigTest {
    private val banner = BannerPlacement("home_banner")
    private val inlineBanner = BannerPlacement("inline_banner")
    private val standardBanner = BannerPlacement("standard_banner")
    private val mrecBanner = BannerPlacement("mrec_banner")
    private val interstitial = InterstitialPlacement("level_complete")
    private val rewarded = RewardedPlacement("extra_life")
    private val rewardedInterstitial = RewardedInterstitialPlacement("bonus_round")
    private val appOpen = AppOpenPlacement("app_open")
    private val native = NativePlacement("article_native")

    /** Every format and banner size, with real (non-demo) ad unit IDs. */
    private fun allFormats(environment: AdEnvironment, block: AdMobConfig.Builder.() -> Unit = {}) = adMobConfig {
        this.environment = environment
        placements {
            banner(banner, TestIds.unit(1))
            banner(inlineBanner, TestIds.unit(2)) { size = BannerSize.InlineAdaptive(maxHeightDp = 200) }
            banner(standardBanner, TestIds.unit(3)) { size = BannerSize.Standard }
            banner(mrecBanner, TestIds.unit(4)) { size = BannerSize.MediumRectangle }
            interstitial(interstitial, TestIds.unit(5))
            rewarded(rewarded, TestIds.unit(6))
            rewardedInterstitial(rewardedInterstitial, TestIds.unit(7))
            appOpen(appOpen, TestIds.unit(8))
            native(native, TestIds.unit(9))
        }
        block()
    }

    private fun EffectiveConfig.adUnit(placement: io.github.thesobiaakbar.adroit.AdPlacement): String =
        requireNotNull(placement(placement)).adUnitId

    @Test
    fun `development substitutes the demo ID per format and banner size`() {
        val effective = EffectiveConfig.from(allFormats(AdEnvironment.DEVELOPMENT))
        assertEquals("ca-app-pub-3940256099942544/9214589741", effective.adUnit(banner))
        assertEquals("ca-app-pub-3940256099942544/9214589741", effective.adUnit(inlineBanner))
        assertEquals("ca-app-pub-3940256099942544/6300978111", effective.adUnit(standardBanner))
        assertEquals("ca-app-pub-3940256099942544/6300978111", effective.adUnit(mrecBanner))
        assertEquals("ca-app-pub-3940256099942544/1033173712", effective.adUnit(interstitial))
        assertEquals("ca-app-pub-3940256099942544/5224354917", effective.adUnit(rewarded))
        assertEquals("ca-app-pub-3940256099942544/5354046379", effective.adUnit(rewardedInterstitial))
        assertEquals("ca-app-pub-3940256099942544/9257395921", effective.adUnit(appOpen))
        assertEquals("ca-app-pub-3940256099942544/2247696110", effective.adUnit(native))
    }

    @Test
    fun `development config with production IDs never exposes a production ID`() {
        // Whatever the testing options say, DEVELOPMENT only ever resolves to demo IDs.
        listOf<Boolean?>(null, true, false).forEach { useDemo ->
            val effective = EffectiveConfig.from(allFormats(AdEnvironment.DEVELOPMENT) { testing { useDemoAdUnits = useDemo } })
            assertTrue(effective.usesDemoAdUnits)
            assertEquals(9, effective.configuredPlacements.size)
            effective.allPlacements.forEach {
                assertTrue("${it.placement} resolved to ${it.adUnitId}", GoogleDemoAdUnits.isDemo(it.adUnitId))
            }
        }
    }

    @Test
    fun `staging uses demo IDs by default`() {
        val effective = EffectiveConfig.from(allFormats(AdEnvironment.STAGING))
        assertTrue(effective.usesDemoAdUnits)
        effective.allPlacements.forEach { assertTrue(GoogleDemoAdUnits.isDemo(it.adUnitId)) }
    }

    @Test
    fun `staging with useDemoAdUnits false uses the configured IDs`() {
        val effective = EffectiveConfig.from(allFormats(AdEnvironment.STAGING) { testing { useDemoAdUnits = false } })
        assertFalse(effective.usesDemoAdUnits)
        assertEquals(TestIds.unit(5), effective.adUnit(interstitial))
        assertEquals(TestIds.unit(3), effective.adUnit(standardBanner))
    }

    @Test
    fun `production uses the configured IDs`() {
        val effective = EffectiveConfig.from(allFormats(AdEnvironment.PRODUCTION))
        assertFalse(effective.usesDemoAdUnits)
        effective.allPlacements.forEach { assertEquals(it.config.adUnitId.value, it.adUnitId) }
    }

    @Test
    fun `test device IDs apply outside production only`() {
        val devices: AdMobConfig.Builder.() -> Unit = { testing { testDeviceIds += "DEVICE" } }
        assertEquals(setOf("DEVICE"), EffectiveConfig.from(allFormats(AdEnvironment.DEVELOPMENT, devices)).testDeviceIds)
        assertEquals(setOf("DEVICE"), EffectiveConfig.from(allFormats(AdEnvironment.STAGING, devices)).testDeviceIds)
        assertEquals(emptySet<String>(), EffectiveConfig.from(allFormats(AdEnvironment.PRODUCTION, devices)).testDeviceIds)
    }

    @Test
    fun `retry defaults per format match architecture section 25`() {
        val effective = EffectiveConfig.from(allFormats(AdEnvironment.PRODUCTION))
        val fullScreen = RetryPolicy(5.seconds, 2.0, 120.seconds, 5, 0.2)
        assertEquals(fullScreen, effective.placement(interstitial)?.retry)
        assertEquals(fullScreen, effective.placement(rewarded)?.retry)
        assertEquals(fullScreen, effective.placement(rewardedInterstitial)?.retry)
        assertEquals(RetryPolicy(10.seconds, 2.0, 300.seconds, 3, 0.2), effective.placement(appOpen)?.retry)
        assertEquals(RetryPolicy(10.seconds, 2.0, 120.seconds, 3, 0.2), effective.placement(native)?.retry)
        assertEquals(RetryPolicy(15.seconds, 2.0, 120.seconds, 4, 0.2), effective.placement(banner)?.retry)
    }

    @Test
    fun `expiry defaults per format match architecture section 12`() {
        val effective = EffectiveConfig.from(allFormats(AdEnvironment.PRODUCTION))
        assertEquals(55.minutes, effective.placement(interstitial)?.expiry)
        assertEquals(55.minutes, effective.placement(rewarded)?.expiry)
        assertEquals(55.minutes, effective.placement(rewardedInterstitial)?.expiry)
        assertEquals(3.hours + 55.minutes, effective.placement(appOpen)?.expiry)
        assertEquals(55.minutes, effective.placement(native)?.expiry)
        assertNull(effective.placement(banner)?.expiry)
    }

    @Test
    fun `placement retry, expiry and frequency override the defaults`() {
        val retry = RetryPolicy(1.seconds, 1.5, 9.seconds, 2, 0.0)
        val effective = EffectiveConfig.from(adMobConfig {
            placements {
                interstitial(interstitial, TestIds.unit(1)) {
                    this.retry = retry
                    expiry = 10.minutes
                    frequency { minInterval = 60.seconds; maxPerSession = 3 }
                }
            }
        })
        val resolved = requireNotNull(effective.placement(interstitial))
        assertEquals(retry, resolved.retry)
        assertEquals(10.minutes, resolved.expiry)
        assertEquals(FrequencyRule(60.seconds, 3), resolved.frequency)
    }

    @Test
    fun `frequency, preload and enabled defaults`() {
        val resolved = requireNotNull(EffectiveConfig.from(allFormats(AdEnvironment.PRODUCTION)).placement(interstitial))
        assertEquals(FrequencyRule(minInterval = 0.seconds, maxPerSession = Int.MAX_VALUE), resolved.frequency)
        assertEquals(PreloadStrategy.AFTER_READY, resolved.preload)
        assertTrue(resolved.enabled)
        assertEquals(AdFormat.INTERSTITIAL, resolved.format)
    }

    @Test
    fun `failFast and log level resolve per environment`() {
        fun of(environment: AdEnvironment, block: AdMobConfig.Builder.() -> Unit = {}) =
            EffectiveConfig.from(adMobConfig { this.environment = environment; block() })

        assertTrue(of(AdEnvironment.DEVELOPMENT).failFast)
        assertTrue(of(AdEnvironment.STAGING).failFast)
        assertFalse(of(AdEnvironment.PRODUCTION).failFast)
        assertTrue(of(AdEnvironment.PRODUCTION) { advanced { failFast = true } }.failFast)
        assertFalse(of(AdEnvironment.DEVELOPMENT) { advanced { failFast = false } }.failFast)

        assertEquals(LogLevel.DEBUG, of(AdEnvironment.DEVELOPMENT).logLevel)
        assertEquals(LogLevel.DEBUG, of(AdEnvironment.STAGING).logLevel)
        assertEquals(LogLevel.WARN, of(AdEnvironment.PRODUCTION).logLevel)
        assertEquals(LogLevel.ERROR, of(AdEnvironment.DEVELOPMENT) { logging { level = LogLevel.ERROR } }.logLevel)
    }

    @Test
    fun `unknown placement resolves to null and duplicates keep the first entry`() {
        val effective = EffectiveConfig.from(adMobConfig {
            environment = AdEnvironment.PRODUCTION
            placements {
                interstitial(interstitial, TestIds.unit(1))
                interstitial(interstitial, TestIds.unit(2))
            }
        })
        assertNull(effective.placement(InterstitialPlacement("unknown")))
        assertEquals(TestIds.unit(1), effective.adUnit(interstitial))
        assertEquals(1, effective.configuredPlacements.size)
    }
}
