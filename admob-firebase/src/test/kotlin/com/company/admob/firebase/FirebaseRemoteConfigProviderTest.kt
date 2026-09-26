package com.company.admob.firebase

import com.company.admob.AdFormat
import com.company.admob.config.PreloadStrategy
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import com.company.admob.firebase.internal.OverridesParser
import com.company.admob.firebase.internal.RemoteConfigSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

/** Robolectric: org.json is part of the Android platform. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FirebaseRemoteConfigProviderTest {
    private class FakeSource(var values: Map<String, String> = emptyMap()) : RemoteConfigSource {
        var listener: (() -> Unit)? = null
        var removed = false
        var fetches = 0
        var pendingActivation: Map<String, String>? = null

        override fun string(key: String): String? = values[key]

        override fun addUpdateListener(onUpdate: () -> Unit): () -> Unit {
            listener = onUpdate
            return { removed = true }
        }

        override fun activate(onDone: () -> Unit) {
            pendingActivation?.let { values = it }
            onDone()
        }

        override fun fetchAndActivate(onDone: () -> Unit) {
            fetches++
            activate(onDone)
        }
    }

    private fun parse(vararg values: Pair<String, String>) = OverridesParser.parse(FakeSource(values.toMap()))

    @Test
    fun `no keys set - no overrides`() {
        assertEquals(AdOverrides(), parse())
    }

    @Test
    fun `every documented key maps to its override`() {
        val overrides = parse(
            "company_admob_enabled" to "false",
            "company_admob_disabled_formats" to "native, App_Open",
            "company_admob_min_fullscreen_interval_seconds" to "45",
            "company_admob_placements_json" to """
                {
                  "level_complete": {"enabled": false, "minIntervalSeconds": 90, "maxPerSession": 3,
                                     "preload": "on_demand", "adUnitId": "ca-app-pub-0000000000000000/0000000009"},
                  "home_banner": {}
                }
            """.trimIndent(),
        )
        assertEquals(false, overrides.adsEnabled)
        assertEquals(setOf(AdFormat.NATIVE, AdFormat.APP_OPEN), overrides.disabledFormats)
        assertEquals(45.seconds, overrides.minIntervalBetweenFullScreenAds)
        assertEquals(
            PlacementOverride(false, 90.seconds, 3, PreloadStrategy.ON_DEMAND, "ca-app-pub-0000000000000000/0000000009"),
            overrides.placements["level_complete"],
        )
        assertEquals(PlacementOverride(), overrides.placements["home_banner"])
    }

    @Test
    fun `invalid JSON ignores only that key`() {
        val overrides = parse(
            "company_admob_enabled" to "true",
            "company_admob_placements_json" to "{ not json",
        )
        assertEquals(true, overrides.adsEnabled)
        assertTrue(overrides.placements.isEmpty())
    }

    @Test
    fun `a placement that isn't an object ignores the placements key`() {
        assertTrue(parse("company_admob_placements_json" to """{"level_complete": 5}""").placements.isEmpty())
    }

    @Test
    fun `invalid fields and values are skipped, the rest applies`() {
        val overrides = parse(
            "company_admob_enabled" to "maybe",
            "company_admob_disabled_formats" to "BANNER,POPUP",
            "company_admob_min_fullscreen_interval_seconds" to "soon",
            "company_admob_placements_json" to """{"level_complete": {"enabled": "nope", "maxPerSession": 2, "preload": "SOMETIMES"}}""",
        )
        assertEquals(null, overrides.adsEnabled)
        assertEquals(setOf(AdFormat.BANNER), overrides.disabledFormats)
        assertEquals(null, overrides.minIntervalBetweenFullScreenAds)
        assertEquals(PlacementOverride(maxPerSession = 2), overrides.placements["level_complete"])
    }

    @Test
    fun `empty values mean unset`() {
        assertEquals(AdOverrides(), parse("company_admob_enabled" to " ", "company_admob_placements_json" to ""))
    }

    @Test
    fun `emits the activated values, then each real-time update after activating it`() = runTest {
        val source = FakeSource(mapOf("company_admob_enabled" to "true"))
        val provider = FirebaseRemoteConfigProvider(source, autoFetch = false)
        val emissions = mutableListOf<AdOverrides>()
        val job = launch { provider.overrides().take(2).toList(emissions) }
        runCurrent()

        source.pendingActivation = mapOf("company_admob_enabled" to "false")
        checkNotNull(source.listener).invoke()
        job.join()

        assertEquals(listOf(true, false), emissions.map { it.adsEnabled })
        assertEquals(0, source.fetches)
        assertTrue("the Firebase listener is removed when collection ends", source.removed)
    }

    @Test
    fun `autoFetch fetches and activates when collected`() = runTest {
        val source = FakeSource().apply { pendingActivation = mapOf("company_admob_disabled_formats" to "NATIVE") }
        val provider = FirebaseRemoteConfigProvider(source, autoFetch = true)

        val overrides = provider.overrides().first { it.disabledFormats.isNotEmpty() }

        assertEquals(1, source.fetches)
        assertEquals(setOf(AdFormat.NATIVE), overrides.disabledFormats)
    }
}
