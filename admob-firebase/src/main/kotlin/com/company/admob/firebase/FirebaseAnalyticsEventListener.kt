package com.company.admob.firebase

import android.annotation.SuppressLint
import android.content.Context
import com.company.admob.AdFormat
import com.company.admob.events.AdEvent
import com.company.admob.events.AdEventListener
import com.company.admob.firebase.internal.AnalyticsSink
import com.company.admob.firebase.internal.FirebaseAnalyticsSink
import com.company.admob.firebase.internal.log
import com.company.admob.logging.LogLevel
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Sends CompanyAdMob events to Google Analytics for Firebase. Register it with
 * `adMobConfig { eventListeners += FirebaseAnalyticsEventListener(context) }`.
 *
 * - `AdEvent.Paid` → Firebase's `ad_impression` event with the standard ad revenue parameters (`ad_platform` =
 *   "admob", `ad_source`, `ad_format`, `ad_unit_name`, `currency`, and `value` in currency units), when
 *   [FirebaseAnalyticsOptions.logAdImpressionRevenue] is on. If your AdMob app is linked to Firebase and
 *   `ad_impression` already appears in Analytics, turn it off to avoid counting revenue twice.
 * - `Impression`, `Clicked` and `RewardEarned` → the custom events `ad_view`, `ad_click` and `ad_reward`, when
 *   listed in [FirebaseAnalyticsOptions.customEvents].
 *
 * Every event also carries `placement` (the placement key). A failure in Firebase is logged and never affects ads.
 *
 * @param analytics the instance to log to.
 */
public class FirebaseAnalyticsEventListener internal constructor(
    private val sink: AnalyticsSink,
    private val options: FirebaseAnalyticsOptions,
) : AdEventListener {
    public constructor(analytics: FirebaseAnalytics, options: FirebaseAnalyticsOptions = FirebaseAnalyticsOptions()) :
        this(FirebaseAnalyticsSink(analytics), options)

    /**
     * Uses `FirebaseAnalytics.getInstance(context)`.
     *
     * The INTERNET, ACCESS_NETWORK_STATE and WAKE_LOCK permissions lint asks for are declared by Firebase's own
     * manifests (play-services-measurement), which merge into the app.
     */
    @SuppressLint("MissingPermission")
    public constructor(context: Context, options: FirebaseAnalyticsOptions = FirebaseAnalyticsOptions()) :
        this(FirebaseAnalytics.getInstance(context), options)

    override fun onEvent(event: AdEvent) {
        val (name, params) = map(event) ?: return
        try {
            sink.logEvent(name, params)
        } catch (e: Exception) {
            log(LogLevel.WARN, "Firebase Analytics couldn't log $name: $e")
        }
    }

    private fun map(event: AdEvent): Pair<String, Map<String, Any>>? = when (event) {
        is AdEvent.Paid -> if (options.logAdImpressionRevenue) {
            FirebaseAnalytics.Event.AD_IMPRESSION to buildMap {
                putAll(common(event))
                event.source?.name?.let { put(FirebaseAnalytics.Param.AD_SOURCE, it) }
                put(FirebaseAnalytics.Param.CURRENCY, event.revenue.currencyCode)
                put(FirebaseAnalytics.Param.VALUE, event.revenue.valueMicros / MICROS_PER_UNIT)
            }
        } else {
            null
        }
        is AdEvent.Impression -> custom(FirebaseAdEvent.VIEW, event)
        is AdEvent.Clicked -> custom(FirebaseAdEvent.CLICK, event)
        is AdEvent.RewardEarned -> custom(FirebaseAdEvent.REWARD, event)?.let { (name, params) ->
            name to params + mapOf("reward_type" to event.reward.type, "reward_amount" to event.reward.amount)
        }
        else -> null
    }

    private fun custom(type: FirebaseAdEvent, event: AdEvent.PlacementEvent): Pair<String, Map<String, Any>>? =
        if (type in options.customEvents) type.eventName to common(event) else null

    private fun common(event: AdEvent.PlacementEvent): Map<String, Any> = mapOf(
        FirebaseAnalytics.Param.AD_PLATFORM to AD_PLATFORM,
        FirebaseAnalytics.Param.AD_FORMAT to event.format.analyticsName(),
        FirebaseAnalytics.Param.AD_UNIT_NAME to event.adUnitId,
        PARAM_PLACEMENT to event.placementKey,
    )

}

private const val AD_PLATFORM = "admob"
private const val PARAM_PLACEMENT = "placement"
private const val MICROS_PER_UNIT = 1_000_000.0

private fun AdFormat.analyticsName(): String = when (this) {
    AdFormat.BANNER -> "banner"
    AdFormat.INTERSTITIAL -> "interstitial"
    AdFormat.REWARDED -> "rewarded"
    AdFormat.REWARDED_INTERSTITIAL -> "rewarded_interstitial"
    AdFormat.APP_OPEN -> "app_open"
    AdFormat.NATIVE -> "native"
}

/** Custom Analytics events [FirebaseAnalyticsEventListener] can log, besides the ad revenue event. */
public enum class FirebaseAdEvent(public val eventName: String) {
    /** `AdEvent.Impression` → `ad_view`. */
    VIEW("ad_view"),

    /** `AdEvent.Clicked` → `ad_click`. */
    CLICK("ad_click"),

    /** `AdEvent.RewardEarned` → `ad_reward`, with `reward_type` and `reward_amount`. */
    REWARD("ad_reward"),
}

/**
 * What [FirebaseAnalyticsEventListener] logs.
 *
 * @property logAdImpressionRevenue whether paid events become Firebase `ad_impression` events. Default true.
 * @property customEvents which custom ad events to log. Default none.
 */
public class FirebaseAnalyticsOptions(
    public val logAdImpressionRevenue: Boolean = true,
    public val customEvents: Set<FirebaseAdEvent> = emptySet(),
) {
    override fun equals(other: Any?): Boolean =
        other is FirebaseAnalyticsOptions && other.logAdImpressionRevenue == logAdImpressionRevenue &&
            other.customEvents == customEvents

    override fun hashCode(): Int = 31 * logAdImpressionRevenue.hashCode() + customEvents.hashCode()

    override fun toString(): String =
        "FirebaseAnalyticsOptions(logAdImpressionRevenue=$logAdImpressionRevenue, customEvents=$customEvents)"
}
