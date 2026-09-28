# Ad profiles: every ad in one enum, changed from Firebase

Define every ad of your app as an entry of one enum, with its default settings. Each ad then has a Firebase Remote
Config parameter named after its entry, so you can turn it off, and change its ad unit IDs, counter, timer, limits and
style, without a release. For the design and the reasons behind it, see
[architecture/05_AD_PROFILES.md](architecture/05_AD_PROFILES.md).

## Setup

List your ads:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AdProfiles.kt#profiles-enum -->
```kotlin
enum class AppAds(override val defaults: AdProfile) : AdDefinition {
    SplashAd(AdProfile.interstitial(ids = listOf("ca-app-pub-0000000000000000/0000000001"), splash = true)),
    HistoryAd(
        AdProfile.interstitial(
            ids = listOf("ca-app-pub-0000000000000000/0000000002", "ca-app-pub-0000000000000000/0000000003"),
            counter = 3,
            maxPerSession = 5,
        ),
    ),
    HomeBanner(
        AdProfile.banner(
            ids = listOf("ca-app-pub-0000000000000000/0000000004"),
            style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM),
        ),
    ),
    FeedNative(AdProfile.native(ids = listOf("ca-app-pub-0000000000000000/0000000005"), style = NativeStyle.LARGE)),
}
```

- The entry name (`HistoryAd`) is the ad's key and its Firebase parameter name. Use letters, digits and underscores
  (up to 40), which is also the rule for Firebase parameter names.
- Every argument of `AdProfile.interstitial(...)`, `rewarded(...)`, `rewardedInterstitial(...)`, `appOpen(...)`,
  `banner(...)` and `native(...)` has the default listed in [the fields](#fields-for-every-ad).
- **No IDs means test ads.** An ad whose `ids` list is empty (the default) uses Google's test ad unit for its format,
  in every build. That lets you add an ad before its real ad unit exists. In PRODUCTION, `initialize` logs a warning
  for each such ad, since test ads earn nothing: set its IDs in the enum or in Firebase before release.

Then register them:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AdProfiles.kt#profiles-setup -->
```kotlin
class AdsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RemoteConfigSession.start(this) // fetches Firebase once this session, without making anything wait
        CompanyAdMob.initialize(
            this,
            adMobConfig {
                // Debug builds show only Google's test ads, whatever IDs the definitions list.
                environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
                profiles(AppAds.entries, AdSettings()) // every ad and its default values
                remoteConfig = FirebaseRemoteConfigProvider() // Firebase values override the defaults, ad by ad
            },
        )
    }
}
```

- `FirebaseRemoteConfigProvider` and `RemoteConfigSession` come from the optional `com.company:admob-firebase`
  module. Without it, the enum alone configures the ads. See [Remote configuration](REMOTE_CONFIG.md) for the
  session: it fetches once per app session, retries on later screens until it succeeds, and never makes the app wait.
- `AdSettings(...)` holds the defaults of the `AdSettings` Firebase parameter (see [Settings](#settings)).
- `profiles()` can be combined with a `placements { }` block, but each key may be defined only once.
- `DEVELOPMENT` swaps every ad unit ID for Google's test ads. Debug builds therefore need no separate IDs: they show
  only test ads, whatever the enum or Firebase lists.
- An invalid definition (a negative counter, a pool size out of range…) is a configuration error. Outside PRODUCTION
  `initialize` throws, so you find out in development; in PRODUCTION, ads turn off and the app keeps running.

Refer to each ad through its entry:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AdProfiles.kt#profiles-placements -->
```kotlin
object Ads {
    val Splash = AppAds.SplashAd.interstitialPlacement()
    val History = AppAds.HistoryAd.interstitialPlacement()
    val HomeBanner = AppAds.HomeBanner.bannerPlacement()
    val FeedNative = AppAds.FeedNative.nativePlacement()
}
```

`interstitialPlacement()` and the others throw if the entry's format is another one. In XML, use the entry name:
`app:company_admob_placement="HomeBanner"`.

### Getting the JSON for Firebase

- Outside PRODUCTION, `initialize` logs every ad's JSON at debug level (`Firebase parameter HistoryAd: {…}`), ready to
  paste as the value of that ad's parameter.
- `AdDefinitions.firebaseTemplate(AppAds.entries, settings)` returns a whole Remote Config template: one JSON
  parameter per ad, plus `AdSettings`. Save it as a file and import it in the Firebase console (Remote Config, ⋮,
  **Publish from a file**) to create every parameter at once. `sample-xml/firebase/remote_config_template.json` is
  the sample's, kept up to date by a unit test.
- `profile.toJson()` gives one ad's value. It leaves out the fields Firebase can't change (`splash`,
  `excludedActivities`, `poolSize`).

### Alternative: an ads file

Instead of an enum, the same ads can be listed in `res/raw/ads_config.json` and registered with
`profiles(context, R.raw.ads_config)`. Refer to them with `InterstitialPlacement("HistoryAd")` and so on. The fields
are the same as in Firebase, plus `format`. An entry without `adUnitIds` also gets Google's test ad unit.

## The JSON

The ads file, and what `AdDefinitions.firebaseTemplate` writes for each ad, looks like this:

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

Replace the placeholder IDs with your real ad unit IDs, or leave `adUnitIds` out for Google's test ads.

### Fields for every ad

| Field | Type | Default | Meaning |
|---|---|---|---|
| `format` | text | required | `interstitial`, `rewarded`, `rewarded_interstitial`, `app_open`, `banner` or `native` |
| `enabled` | true/false | `true` | `false` turns this ad off |
| `adUnitIds` | list of IDs | test ads | Used one after another: each load takes the next one, and after the last one the first again. A single ID can be written as text. Empty or left out: Google's test ad unit for the format. In Firebase, an empty list keeps the defaults. |

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
