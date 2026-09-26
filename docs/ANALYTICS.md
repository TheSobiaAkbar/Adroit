# Analytics and events

Everything the library does is reported as an `AdEvent`: to listeners registered in the configuration, and as a
`Flow`. Events never contain personal data; they carry the placement key, format, ad unit ID and a wall-clock
timestamp.

## Event catalogue

| Event | Extra fields | When |
|---|---|---|
| `LoadRequested` | | An ad request was sent. |
| `Loaded` | `latencyMillis`, `source` | An ad loaded (for banners, also on each refresh). |
| `LoadFailed` | `error`, `attempt`, `willRetry` | A load failed; says whether a retry follows. |
| `ShowRequested` | | `show` was called for a full-screen ad. |
| `NotShown` | `error` | A show was refused (not ready, capped, disabled…). |
| `Shown` | | A full-screen ad appeared. |
| `Impression` | | Google counted an impression. |
| `Clicked` | | The user clicked the ad. |
| `Dismissed` | | A full-screen ad closed. |
| `RewardEarned` | `reward`, `rewardId` | A rewarded or rewarded interstitial reward was earned. |
| `Paid` | `revenue`, `source` | Impression-level revenue ([below](#paid-events-and-ilrd)). |
| `Expired` | | A cached ad was dropped for age. |
| `Destroyed` | | A banner or native ad was destroyed and its resources released. |
| `ConsentUpdated` | `consent` | The user's consent state changed. |
| `StateChanged` | `state` | `AdsState` changed. |
| `ConfigurationWarning` | `message` | A configuration problem that doesn't stop ads. |

All but the last three are placement events (`AdEvent.PlacementEvent`), with `placementKey`, `format` and
`adUnitId`. `source` (`AdSourceInfo`) names the ad network, instance, mediation group and A/B test, when Google
reports them. In PRODUCTION logs, ad unit IDs are shortened to their last 4 characters; events carry the full ID.

## Listeners

Register listeners in the configuration: `adMobConfig { eventListeners += AnalyticsForwarder(analytics) }`. They're
called on the main thread, in order, for every event from the start. Keep them fast; exceptions are caught and
logged. A Firebase Analytics or Amplitude mapping looks like this (`Analytics` stands for your SDK):

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AnalyticsAndRemote.kt#analytics-listener -->
```kotlin
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
```

## The Flow

`CompanyAdMob.events` is a `SharedFlow<AdEvent>`, the same before and after initialization. Collect it from a scope
that lives as long as you need; there's no replay:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AnalyticsAndRemote.kt#analytics-flow -->
```kotlin
class RevenueViewModel : ViewModel() {
    init {
        viewModelScope.launch {
            CompanyAdMob.events
                .filterIsInstance<AdEvent.Paid>()
                .collect { paid -> println("${paid.placementKey}: ${paid.revenue.valueMicros} micros") }
        }
    }
}
```

`CompanyAdMob.diagnostics.recentEvents` keeps the last events for debugging, and `diagnostics.snapshot()` summarizes
the state of every placement.

## Google Analytics for Firebase

The optional `com.company:admob-firebase` module provides `FirebaseAnalyticsEventListener`, registered like any
listener (full setup in [Remote config](REMOTE_CONFIG.md#firebase-remote-config)):

```kotlin
eventListeners += FirebaseAnalyticsEventListener(context, FirebaseAnalyticsOptions())
```

- `Paid` becomes Firebase's `ad_impression` event with the standard ad revenue parameters
  ([Firebase](https://firebase.google.com/docs/analytics/measure-ad-revenue)): `ad_platform` = `admob`, `ad_source`,
  `ad_format`, `ad_unit_name` (the ad unit ID), `currency`, and `value` in currency units.
- **Double counting:** when your AdMob app is linked to Firebase, the Firebase SDK logs `ad_impression` for AdMob
  itself (same page). If those events already appear in Analytics, set
  `FirebaseAnalyticsOptions(logAdImpressionRevenue = false)`.
- `customEvents` adds `ad_view` (impression), `ad_click` and `ad_reward` (with `reward_type` and `reward_amount`).
  None by default.
- Every event carries `placement`, the placement key. A Firebase failure is logged, and never affects ads.

## Paid events and ILRD

`Paid` carries `AdRevenue(valueMicros, currencyCode, precision)`: the value in millionths of the currency unit (5,000
micros = 0.005), and a precision of `PRECISE`, `ESTIMATED`, `PUBLISHER_PROVIDED` or `UNKNOWN`. Google only sends paid
events once **impression-level ad revenue** is turned on in the AdMob UI
([Google](https://developers.google.com/admob/android/next-gen/impression-level-ad-revenue),
[how to turn it on](https://support.google.com/admob/answer/11322405)).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
