package com.company.admob.internal.fullscreen

import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.AppOpenPlacement
import com.company.admob.BannerSize
import com.company.admob.FullScreenResult
import com.company.admob.NativeStyle
import com.company.admob.UnavailableReason
import com.company.admob.config.AdEnvironment
import com.company.admob.config.FrequencyRule
import com.company.admob.config.TestIds
import com.company.admob.config.adMobConfig
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import com.company.admob.internal.config.AdProfiles
import com.company.admob.internal.config.ConfigIssueCode
import com.company.admob.internal.config.ConfigValidator
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.config.GoogleDemoAdUnits
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Remote format swaps (docs/AD_PROFILES.md, "Swapping formats"): interstitial ↔ app open, banner ↔ native. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FormatSwapTest {
    private val formats = mapOf(
        "Inter" to AdFormat.INTERSTITIAL,
        "Open" to AdFormat.APP_OPEN,
        "Banner" to AdFormat.BANNER,
        "Native" to AdFormat.NATIVE,
        "Reward" to AdFormat.REWARDED,
    )

    private fun parse(key: String, json: String) = AdProfiles.parseRemote(formats, mapOf(key to json), null)

    @Test
    fun `a remote value may swap to the partner format, with the new format's style`() {
        val inter = parse("Inter", """{"format": "app_open", "adUnitIds": ["${TestIds.unit(9)}"]}""")
        assertEquals(emptyList<String>(), inter.issues)
        assertEquals(AdFormat.APP_OPEN, inter.overrides.placements.getValue("Inter").format)

        val banner = parse("Banner", """{"format": "native", "style": "large_compact"}""")
        assertEquals(emptyList<String>(), banner.issues)
        assertEquals(AdFormat.NATIVE, banner.overrides.placements.getValue("Banner").format)
        assertEquals(NativeStyle.LARGE_COMPACT, banner.overrides.placements.getValue("Banner").nativeStyle)

        val native = parse("Native", """{"format": "banner", "style": "collapsible_bottom"}""")
        assertEquals(emptyList<String>(), native.issues)
        assertTrue(native.overrides.placements.getValue("Native").bannerSize is BannerSize.Collapsible)

        // Its own format is no swap.
        assertNull(parse("Open", """{"format": "app_open", "enabled": false}""").overrides.placements.getValue("Open").format)
    }

    @Test
    fun `rewarded never swaps, and nothing swaps outside its pair`() {
        val reward = parse("Reward", """{"format": "interstitial", "enabled": false}""")
        assertTrue(reward.overrides.placements.isEmpty())
        assertEquals(1, reward.issues.size)

        val wrongPair = parse("Inter", """{"format": "native"}""")
        assertTrue(wrongPair.overrides.placements.isEmpty())
        assertTrue(wrongPair.issues.single().contains("can only become app_open"))
    }

    private fun productionConfig() = adMobConfig {
        environment = AdEnvironment.PRODUCTION
        appId = TestIds.APP_ID
        placements { interstitial(LEVEL_END, TestIds.unit(1)) }
    }

    @Test
    fun `in PRODUCTION a swap needs ad units of the new format, or only the swap is dropped`() {
        val noIds = AdOverrides(placements = mapOf("level_end" to PlacementOverride(format = AdFormat.APP_OPEN, counter = 4)))
        val dropped = ConfigValidator.validateOverrides(noIds, productionConfig())
        assertEquals(listOf(ConfigIssueCode.SWAP_WITHOUT_AD_UNITS), dropped.issues.map { it.code })
        val kept = dropped.accepted.placements.getValue("level_end")
        assertNull(kept.format)
        assertEquals(4, kept.counter)

        val withIds = AdOverrides(
            placements = mapOf("level_end" to PlacementOverride(format = AdFormat.APP_OPEN, adUnitIds = listOf(TestIds.unit(9)))),
        )
        val accepted = ConfigValidator.validateOverrides(withIds, productionConfig())
        assertEquals(emptyList<Any>(), accepted.issues)
        val effective = EffectiveConfig.from(productionConfig()).withOverrides(accepted.accepted)
        val active = effective.active("level_end")!!
        assertEquals(AdFormat.APP_OPEN, active.format)
        assertEquals(listOf(TestIds.unit(9)), active.adUnitIds)
    }

    @Test
    fun `outside PRODUCTION a swap without ad units uses Google's test ad unit of the new format`() {
        val config = adMobConfig {
            environment = AdEnvironment.STAGING
            testing {
                useDemoAdUnits = false
                testDeviceIds += "TEST-DEVICE"
            }
            placements { interstitial(LEVEL_END, TestIds.unit(1)) }
        }
        val overrides = AdOverrides(placements = mapOf("level_end" to PlacementOverride(format = AdFormat.APP_OPEN)))
        val accepted = ConfigValidator.validateOverrides(overrides, config).accepted
        val active = EffectiveConfig.from(config).withOverrides(accepted).active("level_end")!!
        assertEquals(listOf(GoogleDemoAdUnits.APP_OPEN), active.adUnitIds)
    }

    @Test
    fun `without a swap the partner placement exists but stays off, and a disabled key stays off when swapped`() {
        val config = engineConfig { placements { interstitial(LEVEL_END, TestIds.unit(1)) } }
        val base = EffectiveConfig.from(config)
        assertEquals(AdFormat.INTERSTITIAL, base.active("level_end")!!.format)
        assertFalse(base.placement(AppOpenPlacement("level_end"))!!.enabled)

        val offAndSwapped = base.withOverrides(
            AdOverrides(
                placements = mapOf(
                    "level_end" to PlacementOverride(enabled = false, format = AdFormat.APP_OPEN, adUnitIds = listOf(TestIds.unit(9))),
                ),
            ),
        )
        assertFalse(offAndSwapped.active("level_end")!!.enabled)
    }

    private fun TestScope.harness(): Pair<EngineHarness, FullScreenAdEngine> {
        val h = EngineHarness(
            this,
            engineConfig {
                placements {
                    interstitial(LEVEL_END, TestIds.unit(1)) {
                        retry = EXACT_RETRY
                        frequency = FrequencyRule(counter = 2)
                    }
                }
            },
        )
        val appOpen = h.engine(FullScreenFormatStrategy.AppOpen)
        h.ready()
        h.run(1_000)
        return h to appOpen
    }

    private fun EngineHarness.swap(format: AdFormat?, unit: Int = 9) {
        val override = if (format == null) PlacementOverride() else PlacementOverride(format = format, adUnitIds = listOf(TestIds.unit(unit)))
        live.update(effective.withOverrides(AdOverrides(placements = mapOf("level_end" to override))))
        run(1_000)
    }

    @Test
    fun `a swap releases the interstitial, loads an app open ad with the new unit, and back again`() = runTest {
        val (h, appOpen) = harness()
        val interstitialAd = h.adsSdk.fullScreenAds.first()
        assertTrue(h.engine.isReady(LEVEL_END))

        h.swap(AdFormat.APP_OPEN)

        assertTrue(interstitialAd.released)
        assertEquals(AdAvailability.Disabled, h.engine.availability(LEVEL_END).value)
        assertEquals(1, h.adsSdk.loadCount(TestIds.unit(9)))
        assertTrue(appOpen.isReady(AppOpenPlacement("level_end")))
        h.show()
        h.run()
        assertEquals(
            FullScreenResult.NotShown(AdError.Disabled(UnavailableReason.DISABLED_BY_REMOTE)),
            h.results.single(),
        )

        h.swap(null)

        assertFalse(appOpen.isReady(AppOpenPlacement("level_end")))
        assertEquals(2, h.adsSdk.loadCount(TestIds.unit(1)))
        assertTrue(h.engine.isReady(LEVEL_END))
    }

    @Test
    fun `a swapped ad keeps its counter and counts its shows as the configured ad`() = runTest {
        val (h, appOpen) = harness()
        h.swap(AdFormat.APP_OPEN)
        val placement = AppOpenPlacement("level_end")
        val results = mutableListOf<FullScreenResult>()

        appOpen.show(h.activity(), placement) { results += it }
        h.run()
        assertTrue("counter 2: the first call only counts", (results.single() as FullScreenResult.NotShown).error is AdError.CounterNotReached)

        appOpen.show(h.activity(), placement) { results += it }
        h.run()
        h.lastAd().simulateFullShow()
        h.run()
        assertTrue(results.last() is FullScreenResult.Dismissed)
        assertEquals(1, h.frequency.showsThisSession(LEVEL_END))
        assertEquals(0, h.frequency.showsThisSession(placement))
    }
}
