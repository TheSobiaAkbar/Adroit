# Ad profiles: one ads file, changed from Firebase

Define every ad in one file, `res/raw/ads_config.json`. Each ad then has a Firebase Remote Config parameter of
the same name, so you can turn it off, and change its ad unit IDs, counter, timer, limits and style, without a
release. For the design and the reasons behind it, see
[architecture/05_AD_PROFILES.md](architecture/05_AD_PROFILES.md).

## Setup

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AdProfiles.kt#profiles-setup -->
```kotlin
class AdsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CompanyAdMob.initialize(
            this,
            adMobConfig {
                // Debug builds show only Google's test ads, whatever IDs the file lists.
                environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
                profiles(this@AdsApp, R.raw.ads_config) // every ad and its default values
                remoteConfig = FirebaseRemoteConfigProvider() // Firebase values override the file, ad by ad
            },
        )
    }
}
```

- `FirebaseRemoteConfigProvider` comes from the optional `com.company:admob-firebase` module. Without it, the file
  alone configures the ads.
- `profiles()` can be combined with a `placements { }` block, but each key may be defined only once.
- `DEVELOPMENT` swaps every ad unit ID for Google's test ads. Debug builds therefore need no separate IDs: they show
  only test ads, whatever the file or Firebase lists.
- A missing or invalid file is a configuration error. Outside PRODUCTION `initialize` throws, so you find out in
  development; in PRODUCTION, ads turn off and the app keeps running.

Refer to each ad by its key:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AdProfiles.kt#profiles-placements -->
```kotlin
object Ads {
    val Splash = InterstitialPlacement("SplashAd")
    val History = InterstitialPlacement("HistoryAd")
    val HomeBanner = BannerPlacement("HomeBanner")
    val FeedNative = NativePlacement("FeedNative")
}
```

Keys use letters, digits and underscores (up to 40), which is also the rule for Firebase parameter names.

## The file

```json
{
  "settings": {
    "adsEnabled": true,
    "maxInterstitialsPerSession": 0,
    "minSecondsBetweenFullScreenAds": 0
  },
  "ads": {
    "SplashAd": {
      "format": "interstitial",
      "enabled": true,
      "splash": true,
      "splashTimeoutSeconds": 8,
      "adUnitIds": ["ca-app-pub-0000000000000000/0000000001"]
    },
    "HistoryAd": {
      "format": "interstitial",
      "enabled": true,
      "adUnitIds": ["ca-app-pub-0000000000000000/0000000002", "ca-app-pub-0000000000000000/0000000003"],
      "counter": 3,
      "timerSeconds": 0,
      "maxPerSession": 5
    },
    "HomeBanner": {
      "format": "banner",
      "enabled": true,
      "style": "collapsible_bottom",
      "adUnitIds": ["ca-app-pub-0000000000000000/0000000004"]
    },
    "FeedNative": {
      "format": "native",
      "enabled": true,
      "style": "large",
      "adUnitIds": ["ca-app-pub-0000000000000000/0000000005"]
    }
  }
}
```

Replace the placeholder IDs with your real ad unit IDs.

### Fields for every ad

| Field | Type | Default | Meaning |
|---|---|---|---|
| `format` | text | required | `interstitial`, `rewarded`, `rewarded_interstitial`, `app_open`, `banner` or `native` |
| `enabled` | true/false | `true` | `false` turns this ad off |
| `adUnitIds` | list of IDs | required | Used one after another: each load takes the next one, and after the last one the first again. A single ID can be written as text. |

### Interstitial and rewarded interstitial

| Field | Type | Default | Meaning |
|---|---|---|---|
| `counter` | number | `0` | 0 = off. With N, an ad shows at most on every Nth `show` call: with 3, on the 3rd, 6th, 9th call. Other calls return `NotShown(CounterNotReached)`. |
| `timerSeconds` | number | `0` | 0 = off. The minimum time between two showings of this ad. Ignored while `counter` is set. |
| `maxPerSession` | number | `0` | 0 = no limit. Showings of this ad per app session. |
| `splash` | true/false | `false` | Interstitial only, file only. The splash ad: shown with `showOnSplash`, not counted in the overall limit, and no counter. |
| `splashTimeoutSeconds` | number | `8` | How long `showOnSplash` waits for an ad. |

### Rewarded, app open, banner, native

| Format | Field | Meaning |
|---|---|---|
| rewarded | `maxPerSession` | 0 = no limit |
| app_open | `timerSeconds` | The cooldown between app open ads (default 4 minutes) |
| app_open | `maxPerSession` | 0 = no limit |
| app_open | `excludedActivities` | File only. Activity class names never covered by an app open ad, for example your splash screen |
| banner | `style` | `adaptive` (default), `collapsible_bottom`, `collapsible_top`, `rectangle` (300×250), `standard` (320×50) or `inline_adaptive` |
| native | `style` | `small`, `medium` (default) or `large`: the library's templates |
| native | `poolSize` | File only. Ads kept loaded, 1 to 3 (default 1) |

### Settings

| Field | Type | Default | Meaning |
|---|---|---|---|
| `adsEnabled` | true/false | `true` | `false` turns every ad off |
| `maxInterstitialsPerSession` | number | `0` | 0 = no limit. Interstitials per session, splash ones not counted. When reached, no other interstitial shows, even if its own `maxPerSession` allows more. |
| `minSecondsBetweenFullScreenAds` | number | `0` | The minimum time between any two full-screen ads |

Unknown fields are ignored, so an older app version accepts newer JSON.

## Firebase

In the Firebase console, open Remote Config:

1. Add a parameter for each ad, named exactly like its key (`HistoryAd`), with the data type **JSON**. Its value is
   the ad's JSON object, or just the fields you want to change: `{ "counter": 5 }` changes only the counter, and
   `{ "enabled": false }` turns just that ad off.
2. Optionally, add a parameter named `AdSettings` (JSON) with any of the settings.
3. Publish. The app picks the values up when Firebase activates them: usually at the next launch after a fetch, or
   at once with real-time updates.

Rules:
- An ad without a Firebase parameter (or with an empty one) uses the file's values.
- A Firebase value can't change an ad's `format`: a value with another format is ignored.
- The file-only fields (`splash`, `excludedActivities`, `poolSize`) are ignored in Firebase values.
- An invalid value, or a value that isn't JSON, is ignored with a warning in the log. An invalid field is dropped,
  and the rest of the value still applies.
- An invalid ad unit ID is skipped and the others are used. If none is valid, the file's IDs stay.
- In PRODUCTION, Google's demo IDs are rejected.
- Ad-free users see no ads, whatever Firebase says: `setAdFree(true)` always wins. Precedence is as in
  [Remote configuration](REMOTE_CONFIG.md#precedence).

`FirebaseRemoteConfigProvider(remoteConfig, autoFetch = true)` fetches when ads start; otherwise your app calls
`fetchAndActivate()` itself. Pass `settingsKey` to use a name other than `AdSettings`.

## Showing the ads

Splash screen:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AdProfiles.kt#profiles-splash -->
```kotlin
CompanyAdMob.start(this) {
    // Waits up to SplashAd's splashTimeoutSeconds, then shows it or gives up. Continue either way.
    CompanyAdMob.interstitial.showOnSplash(this, Ads.Splash) { goToMain() }
}
```

Interstitials with a counter: call `show` every time the event happens, and let the counter decide.

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AdProfiles.kt#profiles-counter -->
```kotlin
// With "counter": 3 in HistoryAd, this shows an ad on the 3rd, 6th, 9th… call. Other calls return at once
// with NotShown(CounterNotReached), and the app just continues.
CompanyAdMob.interstitial.show(this, Ads.History) { result: FullScreenResult -> openHistory() }
```

Banners and native ads take their size and template from `style` when your code doesn't pass one:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AdProfiles.kt#profiles-ui -->
```kotlin
@Composable
fun HomeScreenAds() {
    AdBanner(Ads.HomeBanner) // size from HomeBanner's "style"
    AdNative(Ads.FeedNative) // template from FeedNative's "style"
}
```

With Views, the same applies to `AdBannerView` and `AdNativeView` without a `company_admob_bannerSize` or
`company_admob_template` attribute, and to `attachBanner`, `attachNativeAd` and `NativeAdHolder.bindTo` without a
size or layout. A new style applies to banners and native ads created after the new values arrive; one already on
screen keeps its style.

## How the limits combine

For an interstitial `show`, the first "no" wins:

1. Ads can run (consent, ad-free, `adsEnabled`) and this ad is `enabled`. Otherwise `NotShown(Disabled)`.
2. The counter, when set: the call is counted. Not yet due → `NotShown(CounterNotReached)`.
3. `maxInterstitialsPerSession` (not for splash ads) → `NotShown(FrequencyCapped)`.
4. The ad's `maxPerSession` → `NotShown(FrequencyCapped)`.
5. `timerSeconds`, when no counter is set → `NotShown(FrequencyCapped)`.
6. `minSecondsBetweenFullScreenAds` → `NotShown(FrequencyCapped)`.
7. An ad is loaded; otherwise `NotShown(NotReady)`, and one loads for next time.

A due counter stays due until an ad actually shows. Only real showings count toward the limits, and they're
counted per app session (per process), in memory.

These limits are a user-experience safeguard, not a replacement for AdMob's own frequency capping.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-27
