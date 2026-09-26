package com.company.admob.firebase

import com.company.admob.AdFormat
import com.company.admob.Reward
import com.company.admob.events.AdEvent
import com.company.admob.events.AdRevenue
import com.company.admob.events.AdSourceInfo
import com.company.admob.events.RevenuePrecision
import com.company.admob.firebase.internal.AnalyticsSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM: the mapping never touches Firebase or Android. */
class FirebaseAnalyticsEventListenerTest {
    private val logged = mutableListOf<Pair<String, Map<String, Any>>>()
    private val sink = AnalyticsSink { name, params -> logged += name to params }
    private val unit = "ca-app-pub-3940256099942544/1033173712"
    private val source = AdSourceInfo("AdMob Network", null, null, null, null, null, null, null, null)

    private fun listener(options: FirebaseAnalyticsOptions = FirebaseAnalyticsOptions()) =
        FirebaseAnalyticsEventListener(sink, options)

    private fun paid(micros: Long = 5_000) = AdEvent.Paid(
        "level_complete", AdFormat.INTERSTITIAL, unit, 1L, AdRevenue(micros, "USD", RevenuePrecision.PRECISE), source,
    )

    @Test
    fun `paid events become ad_impression with the standard ad revenue parameters`() {
        listener().onEvent(paid(micros = 5_000))

        assertEquals(
            listOf(
                "ad_impression" to mapOf(
                    "ad_platform" to "admob",
                    "ad_format" to "interstitial",
                    "ad_unit_name" to unit,
                    "placement" to "level_complete",
                    "ad_source" to "AdMob Network",
                    "currency" to "USD",
                    "value" to 0.005,
                ),
            ),
            logged,
        )
    }

    @Test
    fun `revenue logging can be turned off, for AdMob apps already linked to Firebase`() {
        listener(FirebaseAnalyticsOptions(logAdImpressionRevenue = false)).onEvent(paid())
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `custom events are logged only when chosen`() {
        val impression = AdEvent.Impression("feed_native", AdFormat.NATIVE, unit, 1L)
        val click = AdEvent.Clicked("feed_native", AdFormat.NATIVE, unit, 1L)
        val reward = AdEvent.RewardEarned("extra_life", AdFormat.REWARDED, unit, 1L, Reward("coins", 50), "id-1")

        listener().apply { onEvent(impression); onEvent(click); onEvent(reward) }
        assertTrue("none by default", logged.isEmpty())

        listener(FirebaseAnalyticsOptions(customEvents = FirebaseAdEvent.entries.toSet()))
            .apply { onEvent(impression); onEvent(click); onEvent(reward) }
        assertEquals(listOf("ad_view", "ad_click", "ad_reward"), logged.map { it.first })
        assertEquals("native", logged[0].second["ad_format"])
        assertEquals("coins", logged[2].second["reward_type"])
        assertEquals(50, logged[2].second["reward_amount"])
    }

    @Test
    fun `other events are ignored`() {
        listener(FirebaseAnalyticsOptions(customEvents = FirebaseAdEvent.entries.toSet())).apply {
            onEvent(AdEvent.LoadRequested("feed_native", AdFormat.NATIVE, unit, 1L))
            onEvent(AdEvent.ConfigurationWarning("x", 1L))
        }
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `a failing Firebase call is isolated from the ads`() {
        val failing = FirebaseAnalyticsEventListener(
            { _, _ -> throw IllegalStateException("Firebase isn't initialized") },
            FirebaseAnalyticsOptions(),
        )
        failing.onEvent(paid()) // must not throw
    }
}
