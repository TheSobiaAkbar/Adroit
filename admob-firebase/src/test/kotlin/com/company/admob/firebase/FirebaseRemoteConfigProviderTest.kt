package com.company.admob.firebase

import com.company.admob.AdFormat
import com.company.admob.BannerSize
import com.company.admob.CollapsibleBannerPosition
import com.company.admob.NativeStyle
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
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
        val read = mutableListOf<String>()

        override fun string(key: String): String? {
            read += key
            return values[key]
        }

        override fun addUpdateListener(onUpdate: () -> Unit): () -> Unit {
            listener = onUpdate
            return { removed = true }
        }

        override fun activate(onDone: () -> Unit) {
            pendingActivation?.let { values = it }
            onDone()
        }

        override fun fetchAndActivate(onDone: (Boolean) -> Unit) {
            fetches++
            activate { onDone(true) }
        }
    }

    private val formats = mapOf(
        "HistoryAd" to AdFormat.INTERSTITIAL,
        "HomeBanner" to AdFormat.BANNER,
        "FeedNative" to AdFormat.NATIVE,
    )

    private fun provider(source: FakeSource, autoFetch: Boolean = false, settingsKey: String = "AdSettings") =
        FirebaseRemoteConfigProvider(source, autoFetch, settingsKey) { formats }

    private suspend fun first(vararg values: Pair<String, String>): AdOverrides =
        provider(FakeSource(values.toMap())).overrides().first()

    @Test
    fun `no parameters set - no overrides`() = runTest {
        assertEquals(AdOverrides(), first())
    }

    @Test
    fun `reads one parameter per configured ad, plus the settings parameter`() = runTest {
        val source = FakeSource()
        provider(source).overrides().first()
        assertEquals(setOf("HistoryAd", "HomeBanner", "FeedNative", "AdSettings"), source.read.toSet())
    }

    @Test
    fun `each ad's JSON becomes its override, and the settings apply`() = runTest {
        val overrides = first(
            "HistoryAd" to """{ "enabled": true, "counter": 3, "timerSeconds": 25, "maxPerSession": 4,
                                "adUnitIds": ["ca-app-pub-1234567890123456/0000000001", "ca-app-pub-1234567890123456/0000000002"] }""",
            "HomeBanner" to """{ "style": "collapsible_top" }""",
            "FeedNative" to """{ "enabled": false, "style": "large_compact" }""",
            "AdSettings" to """{ "adsEnabled": true, "maxInterstitialsPerSession": 6, "minSecondsBetweenFullScreenAds": 20 }""",
        )
        assertEquals(
            PlacementOverride(
                enabled = true,
                minInterval = 25.seconds,
                maxPerSession = 4,
                adUnitIds = listOf("ca-app-pub-1234567890123456/0000000001", "ca-app-pub-1234567890123456/0000000002"),
                counter = 3,
            ),
            overrides.placements.getValue("HistoryAd"),
        )
        assertEquals(
            PlacementOverride(bannerSize = BannerSize.Collapsible(CollapsibleBannerPosition.TOP)),
            overrides.placements.getValue("HomeBanner"),
        )
        assertEquals(PlacementOverride(enabled = false, nativeStyle = NativeStyle.LARGE_COMPACT), overrides.placements.getValue("FeedNative"))
        assertEquals(true, overrides.adsEnabled)
        assertEquals(6, overrides.maxInterstitialsPerSession)
        assertEquals(20.seconds, overrides.minIntervalBetweenFullScreenAds)
    }

    @Test
    fun `an invalid value ignores only that ad`() = runTest {
        val overrides = first("HistoryAd" to "{ broken", "FeedNative" to """{ "enabled": false }""")
        assertEquals(setOf("FeedNative"), overrides.placements.keys)
    }

    @Test
    fun `empty values mean unset`() = runTest {
        assertEquals(AdOverrides(), first("HistoryAd" to " ", "AdSettings" to ""))
    }

    @Test
    fun `the settings parameter name is configurable`() = runTest {
        val source = FakeSource(mapOf("MyAds" to """{ "adsEnabled": false }"""))
        assertEquals(false, provider(source, settingsKey = "MyAds").overrides().first().adsEnabled)
    }

    @Test
    fun `emits the activated values, then each real-time update after activating it`() = runTest {
        val source = FakeSource(mapOf("AdSettings" to """{ "adsEnabled": true }"""))
        val emissions = mutableListOf<AdOverrides>()
        val job = launch { provider(source).overrides().take(2).toList(emissions) }
        runCurrent()

        source.pendingActivation = mapOf("AdSettings" to """{ "adsEnabled": false }""")
        checkNotNull(source.listener).invoke()
        job.join()

        assertEquals(listOf(true, false), emissions.map { it.adsEnabled })
        assertEquals(0, source.fetches)
        assertTrue("the Firebase listener is removed when collection ends", source.removed)
    }

    @Test
    fun `autoFetch fetches and activates when collected`() = runTest {
        val source = FakeSource().apply { pendingActivation = mapOf("FeedNative" to """{ "enabled": false }""") }
        val overrides = provider(source, autoFetch = true).overrides().first { it.placements.isNotEmpty() }
        assertEquals(1, source.fetches)
        assertEquals(PlacementOverride(enabled = false), overrides.placements.getValue("FeedNative"))
    }

    @Test
    fun `re-reads when a RemoteConfigSession activates new values`() = runTest {
        val source = FakeSource(mapOf("HistoryAd" to """{"counter": 2}"""))
        val emitted = mutableListOf<AdOverrides>()
        val job = launch { provider(source).overrides().take(2).toList(emitted) }
        runCurrent()
        source.values = mapOf("HistoryAd" to """{"counter": 5}""")
        com.company.admob.firebase.internal.ActivationSignal.notifyActivated()
        runCurrent()
        job.join()
        assertEquals(listOf(2, 5), emitted.map { it.placements.getValue("HistoryAd").counter })
    }
}
