package com.company.admob.internal.config

import com.company.admob.AdFormat
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
import com.company.admob.UnavailableReason
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.PreloadStrategy
import com.company.admob.config.TestIds
import com.company.admob.config.adMobConfig
import com.company.admob.config.remote.AdConfigProvider
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import com.company.admob.events.AdEvent
import com.company.admob.fakes.FakeClock
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.logging.AdLog
import com.company.admob.logging.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OverridesApplierTest {
    private val level = InterstitialPlacement("level")
    private val menu = InterstitialPlacement("menu")
    private val feed = NativePlacement("feed")

    private fun config(environment: AdEnvironment = AdEnvironment.PRODUCTION): AdMobConfig = adMobConfig {
        this.environment = environment
        frequency { minIntervalBetweenFullScreenAds = 30.seconds }
        placements {
            interstitial(level, TestIds.unit(1)) { frequency { minInterval = 10.seconds } }
            interstitial(menu, TestIds.unit(2))
            native(feed, TestIds.unit(3))
        }
    }

    private class Setup(config: AdMobConfig, scope: TestScope, provider: AdConfigProvider = AdConfigProvider { flow { } }) {
        val errors = mutableListOf<String>()
        val log = AdLog({ level, _, message, _ -> if (level == LogLevel.ERROR) errors += message }, LogLevel.VERBOSE)
        val events = EventBus(AdLog({ _, _, _, _ -> }, LogLevel.NONE))
        val live = LiveConfig(EffectiveConfig.from(config), log)
        val changes = mutableListOf<EffectiveConfig>()
        val applier = OverridesApplier(live, provider, events, FakeClock(), scope.backgroundScope, log)

        init {
            live.addListener { _, current -> changes += current }
        }

        val warnings: List<String> get() = events.recent().filterIsInstance<AdEvent.ConfigurationWarning>().map { it.message }
    }

    @Test
    fun `apply, then remove by emitting empty overrides - the local values come back`() = runTest {
        val s = Setup(config(), this)
        s.applier.apply(
            AdOverrides(
                disabledFormats = setOf(AdFormat.NATIVE),
                placements = mapOf(
                    "level" to PlacementOverride(minInterval = 2.minutes, maxPerSession = 3, preload = PreloadStrategy.NONE),
                    "menu" to PlacementOverride(enabled = false, adUnitId = TestIds.unit(9)),
                ),
                minIntervalBetweenFullScreenAds = 90.seconds,
            ),
        )
        val applied = s.live.current
        assertEquals(2.minutes, applied.placement(level)!!.frequency.minInterval)
        assertEquals(3, applied.placement(level)!!.frequency.maxPerSession)
        assertEquals(PreloadStrategy.NONE, applied.placement(level)!!.preload)
        assertEquals(UnavailableReason.DISABLED_BY_REMOTE, applied.placement(menu)!!.disabledReason)
        assertEquals(TestIds.unit(9), applied.placement(menu)!!.adUnitId)
        assertEquals(UnavailableReason.DISABLED_BY_REMOTE, applied.placement(feed)!!.disabledReason)
        assertEquals(90.seconds, applied.minIntervalBetweenFullScreenAds)

        s.applier.apply(AdOverrides())
        val restored = s.live.current
        assertEquals(10.seconds, restored.placement(level)!!.frequency.minInterval)
        assertEquals(Int.MAX_VALUE, restored.placement(level)!!.frequency.maxPerSession)
        assertTrue(restored.placement(menu)!!.enabled)
        assertEquals(TestIds.unit(2), restored.placement(menu)!!.adUnitId)
        assertTrue(restored.placement(feed)!!.enabled)
        assertEquals(30.seconds, restored.minIntervalBetweenFullScreenAds)
        assertEquals(2, s.changes.size)
        assertTrue(s.errors.isEmpty())
    }

    @Test
    fun `an equal emission changes nothing`() = runTest {
        val s = Setup(config(), this)
        val overrides = AdOverrides(adsEnabled = false)
        s.applier.apply(overrides)
        s.applier.apply(AdOverrides(adsEnabled = false))
        assertEquals(1, s.changes.size)
        assertFalse(s.live.current.adsEnabled)
    }

    @Test
    fun `an invalid placement override is dropped whole, the rest applies, and it's reported`() = runTest {
        val s = Setup(config(), this)
        s.applier.apply(
            AdOverrides(
                placements = mapOf(
                    // Malformed ad unit: the valid minInterval in the same override must not apply either.
                    "level" to PlacementOverride(minInterval = 5.minutes, adUnitId = "not-an-ad-unit"),
                    "menu" to PlacementOverride(maxPerSession = -1),
                    "unknown_key" to PlacementOverride(enabled = false),
                    "feed" to PlacementOverride(enabled = false),
                ),
                minIntervalBetweenFullScreenAds = (-1).seconds,
            ),
        )
        val current = s.live.current
        assertEquals(10.seconds, current.placement(level)!!.frequency.minInterval)
        assertEquals(TestIds.unit(1), current.placement(level)!!.adUnitId)
        assertEquals(Int.MAX_VALUE, current.placement(menu)!!.frequency.maxPerSession)
        assertFalse(current.placement(feed)!!.enabled)
        assertEquals(30.seconds, current.minIntervalBetweenFullScreenAds)
        assertEquals(setOf("feed"), current.overrides.placements.keys)
        assertEquals(4, s.errors.size)
        assertEquals(s.errors, s.warnings)
        assertTrue(s.errors.any { "'level'" in it && "not-an-ad-unit" in it })
        assertTrue(s.errors.any { "'unknown_key'" in it })
    }

    @Test
    fun `a demo ad unit override is rejected in PRODUCTION`() = runTest {
        val s = Setup(config(AdEnvironment.PRODUCTION), this)
        s.applier.apply(AdOverrides(placements = mapOf("level" to PlacementOverride(adUnitId = GoogleDemoAdUnits.INTERSTITIAL))))
        assertEquals(TestIds.unit(1), s.live.current.placement(level)!!.adUnitId)
        assertTrue(s.errors.single().contains("demo ad unit"))
        assertTrue(s.changes.isEmpty())
    }

    @Test
    fun `DEVELOPMENT keeps substituting demo IDs whatever the remote ad unit`() = runTest {
        val s = Setup(config(AdEnvironment.DEVELOPMENT), this)
        s.applier.apply(AdOverrides(placements = mapOf("level" to PlacementOverride(adUnitId = TestIds.unit(7), minInterval = 1.minutes))))
        val p = s.live.current.placement(level)!!
        assertEquals(GoogleDemoAdUnits.INTERSTITIAL, p.adUnitId)
        assertEquals(1.minutes, p.frequency.minInterval)
    }

    @Test
    fun `a remote enable beats a local disable, and a disabled format beats a placement enable`() = runTest {
        val local = adMobConfig {
            environment = AdEnvironment.PRODUCTION
            placements {
                interstitial(level, TestIds.unit(1)) { enabled = false }
                native(feed, TestIds.unit(3))
            }
        }
        val s = Setup(local, this)
        assertEquals(UnavailableReason.DISABLED_BY_CONFIG, s.live.current.placement(level)!!.disabledReason)
        s.applier.apply(
            AdOverrides(
                disabledFormats = setOf(AdFormat.NATIVE),
                placements = mapOf("level" to PlacementOverride(enabled = true), "feed" to PlacementOverride(enabled = true)),
            ),
        )
        assertTrue(s.live.current.placement(level)!!.enabled)
        assertEquals(UnavailableReason.DISABLED_BY_REMOTE, s.live.current.placement(feed)!!.disabledReason)
    }

    @Test
    fun `the provider is collected, and a provider that throws keeps the last good overrides`() = runTest {
        val provider = AdConfigProvider {
            flow {
                emit(AdOverrides(placements = mapOf("level" to PlacementOverride(maxPerSession = 2))))
                error("remote config backend exploded")
            }
        }
        val s = Setup(config(), this, provider)
        s.applier.start()
        runCurrent()
        assertEquals(2, s.live.current.placement(level)!!.frequency.maxPerSession)
        assertTrue(s.errors.single().contains("failed; keeping the last good overrides"))
    }

    @Test
    fun `a provider whose overrides() throws is logged, and the local configuration stays`() = runTest {
        val s = Setup(config(), this) { throw IllegalStateException("not configured") }
        val before = s.live.current
        s.applier.start()
        runCurrent()
        assertSame(before, s.live.current)
        assertEquals(1, s.errors.size)
    }

    @Test
    fun `each emission replaces the previous one`() = runTest {
        val remote = MutableStateFlow(AdOverrides(placements = mapOf("level" to PlacementOverride(maxPerSession = 2))))
        val s = Setup(config(), this) { remote }
        s.applier.start()
        runCurrent()
        assertEquals(2, s.live.current.placement(level)!!.frequency.maxPerSession)
        remote.value = AdOverrides(placements = mapOf("menu" to PlacementOverride(maxPerSession = 4)))
        runCurrent()
        assertEquals(Int.MAX_VALUE, s.live.current.placement(level)!!.frequency.maxPerSession)
        assertEquals(4, s.live.current.placement(menu)!!.frequency.maxPerSession)
    }

    @Test
    fun `the summary shortens ad unit IDs in PRODUCTION only`() {
        val overrides = AdOverrides(adsEnabled = false, placements = mapOf("level" to PlacementOverride(adUnitId = TestIds.unit(42))))
        val production = OverridesApplier.summarize(overrides, EffectiveConfig.from(config(AdEnvironment.PRODUCTION)))
        val staging = OverridesApplier.summarize(overrides, EffectiveConfig.from(config(AdEnvironment.DEVELOPMENT)))
        assertFalse(TestIds.unit(42) in production)
        assertTrue("…0042" in production)
        assertTrue(TestIds.unit(42) in staging)
        assertEquals("none", OverridesApplier.summarize(AdOverrides(), EffectiveConfig.from(config())))
    }
}
