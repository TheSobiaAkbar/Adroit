# Remote configuration

Remote overrides change ads at runtime without a release: a kill switch, turning off a format or a placement,
changing intervals, or moving a placement to a new ad unit. They're applied live: the next load, show or banner uses
the new values.

## `AdConfigProvider`

The library collects your provider's `Flow<AdOverrides>` after initialization and applies each emission on the main
thread. **Each emission replaces the previous one entirely.** Register it with `adMobConfig { remoteConfig = … }`.

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AnalyticsAndRemote.kt#remote-provider -->
```kotlin
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
```

The provider must not throw. The default, `StaticConfigProvider`, emits nothing.

## `AdOverrides`

| Field | Effect |
|---|---|
| `adsEnabled` | `false` turns every ad off: `AdsState.Unavailable(DISABLED_BY_REMOTE)`. `null` leaves ads as configured. |
| `disabledFormats` | Formats to turn off, for example `setOf(AdFormat.NATIVE)`. |
| `placements` | Per placement key, a `PlacementOverride`: `enabled`, `minInterval`, `maxPerSession`, `preload`, `adUnitIds` (a rotation list; `adUnitId` for one), `counter`, `bannerSize`, `nativeStyle` and `splashTimeout`. `null` fields keep the local value. |
| `minIntervalBetweenFullScreenAds` | Replaces the global full-screen interval. |
| `maxInterstitialsPerSession` | Replaces the overall interstitial limit per session. |

When a placement or format is turned off, its cached ads are destroyed, its banners hide
(`Hidden(DISABLED_BY_REMOTE)`), and shows return `NotShown(Disabled(DISABLED_BY_REMOTE))`. When it's turned back on,
loading resumes.

## Precedence

From strongest to weakest:

1. An ad-free user (`CompanyAdMob.setAdFree(true)`): no ads, whatever the remote says.
2. A remote `adsEnabled = false`.
3. Remote per-placement overrides and disabled formats.
4. The local configuration.

## Validation

Overrides are validated like the local configuration, but an invalid value never stops ads: it's dropped with a log,
and the rest of the emission still applies. For example, an unknown placement key, a malformed ad unit ID, a Google
demo ad unit in PRODUCTION, or a negative interval or count. `CompanyAdMob.diagnostics.snapshot().activeOverrides`
shows what's currently applied.

## Firebase Remote Config

The optional `com.company:admob-firebase` module provides `FirebaseRemoteConfigProvider`. Your app keeps initializing
Firebase itself (the google-services plugin); the module doesn't apply it.

```kotlin
implementation("com.company:admob-firebase:1.0.0-rc2")
```

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Firebase.kt#firebase-setup -->
```kotlin
class FirebaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase itself is initialized by the google-services plugin, before this runs.
        CompanyAdMob.initialize(
            this,
            adMobConfig {
                environment = AdEnvironment.PRODUCTION
                remoteConfig = FirebaseRemoteConfigProvider() // one parameter per ad, plus AdSettings
                eventListeners += FirebaseAnalyticsEventListener(
                    context = this@FirebaseApp,
                    options = FirebaseAnalyticsOptions(
                        logAdImpressionRevenue = true, // false if AdMob is linked to Firebase (see below)
                        customEvents = setOf(FirebaseAdEvent.CLICK, FirebaseAdEvent.REWARD),
                    ),
                )
                placements { /* … */ }
            },
        )
    }
}
```

The provider reads **one JSON parameter per ad**, named like the placement key (for example `HistoryAd`), plus a
settings parameter named `AdSettings`. The values use the same JSON as the ads file: every field and rule is in
[Ad profiles](AD_PROFILES.md#firebase). All parameters are optional; an unset or empty one leaves that ad as
configured, and a field left out of a value keeps its configured value.

```json
{ "enabled": true, "counter": 3, "timerSeconds": 0, "maxPerSession": 5,
  "adUnitIds": ["ca-app-pub-xxxxxxxxxxxxxxxx/yyyyyyyyyy", "ca-app-pub-xxxxxxxxxxxxxxxx/zzzzzzzzzz"] }
```

- When collected, the provider emits the currently **activated** values, then again after each real-time update, which
  it activates.
- It doesn't fetch unless you pass `FirebaseRemoteConfigProvider(remoteConfig, autoFetch = true)`. Prefer
  `RemoteConfigSession` ([below](#fetching-once-per-session)), or call `fetchAndActivate()` on your own schedule.
- An invalid value is logged and ignored; the other parameters still apply. An invalid field ignores just that field.
- Pass a `FirebaseRemoteConfig` instance for apps with several Firebase apps, and `settingsKey` to rename
  `AdSettings`.

### Fetching once per session

`RemoteConfigSession.start(application)` in `Application.onCreate` fetches and activates at once, without making
anything wait. After the first success it never fetches again in that process (`isFetched` turns true). After a
failure it tries again whenever an Activity is created and whenever the device comes back online, one attempt at a
time, and skips attempts while offline. Each activation reaches `FirebaseRemoteConfigProvider` at once, so the next ad
request, and the next splash timer, use the new values. Use it instead of `autoFetch = true`.

Set a short `fetchTimeoutInSeconds` (for example 10) in your `FirebaseRemoteConfigSettings`, so a slow attempt
doesn't hold off the next screen's retry, and a `minimumFetchIntervalInSeconds` of 0 in debug builds to see console
changes on the next launch.

## Your app's own values

The same fetch also brings the app's own parameters: flags, numbers, texts and JSON that have nothing to do with ads.
Declare each once with its default, in one place:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/RemoteValues.kt#remote-values -->
```kotlin
/** The app's own Firebase parameters, each with the value used until Firebase sets one. */
object AppConfig {
    val showRateDialog = RemoteValue.boolean("show_rate_dialog", default = false)
    val freeLevels = RemoteValue.long("free_levels", default = 5)
    val coinPrice = RemoteValue.double("coin_price", default = 0.99)
    val welcomeText = RemoteValue.string("welcome_text", default = "Welcome!")
    val features = RemoteValue.json("features", default = JSONObject().put("chat", false))
}
```

In the Firebase console, add a parameter with the same name, of the matching data type: **Boolean**, **Number**,
**String** or **JSON**. Then read `.value` anywhere:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/RemoteValues.kt#remote-values-read -->
```kotlin
fun onLevelFinished(level: Int, openPaywall: () -> Unit, showRateDialog: () -> Unit) {
    // .value is the value in force now: it never waits for Firebase.
    if (level >= AppConfig.freeLevels.value) openPaywall()
    if (AppConfig.showRateDialog.value) showRateDialog()
    val chatEnabled = AppConfig.features.value.optBoolean("chat")
}
```

- `.value` never waits. Until this session's fetch lands, it's the value from the last launch's fetch, or the default
  on the very first launch. The fetch runs once per launch (`RemoteConfigSession`), so a value changes at most once
  while the app runs, when that fetch lands.
- A parameter missing from Firebase reads as the default. So does one that isn't a valid value of its type (a text
  in a number parameter, a JSON array in a JSON parameter): it's logged and ignored.
- `json` returns a new `JSONObject` on each read. For a JSON object with a fixed shape, read it into your own class
  with `custom`:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/RemoteValues.kt#remote-values-custom -->
```kotlin
data class Offer(val percent: Int, val title: String)

/** A JSON object read into the app's own class; an invalid value reads as the default. */
val offer = RemoteValue.custom("offer", default = Offer(0, "")) { text ->
    val json = JSONObject(text)
    Offer(json.getInt("percent"), json.getString("title"))
}
```

To follow a value on a screen that stays open, collect `updates()`: it emits the value now, then the new value if
the fetch changes it.

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/RemoteValues.kt#remote-values-flow -->
```kotlin
fun showWelcome(scope: LifecycleCoroutineScope, label: TextView) {
    // The value now, then the new one if this session's fetch changes it while the screen is open.
    scope.launch { AppConfig.welcomeText.updates().collect { label.text = it } }
}
```

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-29
