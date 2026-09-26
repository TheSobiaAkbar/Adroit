@file:Suppress("unused")

package com.company.admob.docs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.admob.AdFormat
import com.company.admob.CompanyAdMob
import com.company.admob.config.remote.AdConfigProvider
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import com.company.admob.events.AdEvent
import com.company.admob.events.AdEventListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Stands in for your analytics SDK (Firebase Analytics, Amplitude…). */
interface Analytics {
    fun log(name: String, params: Map<String, Any?>)
}

// region analytics-listener
/** Registered with adMobConfig { eventListeners += AnalyticsForwarder(analytics) }. Main thread, keep it fast. */
class AnalyticsForwarder(private val analytics: Analytics) : AdEventListener {
    override fun onEvent(event: AdEvent) {
        when (event) {
            is AdEvent.Impression -> analytics.log(
                "ad_impression",
                mapOf("placement" to event.placementKey, "format" to event.format.name),
            )
            is AdEvent.Clicked -> analytics.log("ad_click", mapOf("placement" to event.placementKey))
            is AdEvent.Paid -> analytics.log(
                "ad_revenue",
                mapOf(
                    "placement" to event.placementKey,
                    "value" to event.revenue.valueMicros / 1_000_000.0,
                    "currency" to event.revenue.currencyCode,
                    "precision" to event.revenue.precision.name,
                    "ad_source" to event.source?.name,
                ),
            )
            is AdEvent.LoadFailed -> analytics.log(
                "ad_load_failed",
                mapOf("placement" to event.placementKey, "code" to event.error.code, "will_retry" to event.willRetry),
            )
            else -> Unit
        }
    }
}
// endregion

// region analytics-flow
class RevenueViewModel : ViewModel() {
    init {
        viewModelScope.launch {
            CompanyAdMob.events
                .filterIsInstance<AdEvent.Paid>()
                .collect { paid -> println("${paid.placementKey}: ${paid.revenue.valueMicros} micros") }
        }
    }
}
// endregion

/** Stands in for your remote config SDK: a stream of parsed values. */
data class RemoteAdValues(
    val adsEnabled: Boolean?,
    val interstitialIntervalSeconds: Long?,
    val disabledPlacements: Set<String>,
    val nativeDisabled: Boolean,
)

// region remote-provider
class MyRemoteConfigProvider(private val values: Flow<RemoteAdValues>) : AdConfigProvider {
    override fun overrides(): Flow<AdOverrides> = values.map { remote ->
        AdOverrides(
            adsEnabled = remote.adsEnabled,
            disabledFormats = if (remote.nativeDisabled) setOf(AdFormat.NATIVE) else emptySet(),
            placements = remote.disabledPlacements.associateWith { PlacementOverride(enabled = false) },
            minIntervalBetweenFullScreenAds = remote.interstitialIntervalSeconds?.seconds,
        )
    }
}
// endregion
