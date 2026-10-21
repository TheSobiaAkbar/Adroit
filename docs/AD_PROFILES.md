# Ad profiles: every ad in one enum, changed from Firebase

Define every ad of your app as an entry of one enum, with its default settings. Each ad then has a Firebase Remote
Config parameter named after its entry, so you can turn it off, and change its ad unit IDs, counter, timer, limits and
style, without a release. For the design and the reasons behind it, see
[architecture/05_AD_PROFILES.md](architecture/05_AD_PROFILES.md).

## Setup

List your ads:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/AdProfiles.kt#profiles-enum -->
```kotlin
enum class AppAds(override val defaults: AdProfile) : AdDefinition {
    SplashAd(AdProfile.interstitial(ids = listOf("ca-app-pub-0000000000000000/0000000001"), splash = true)),
    HistoryAd(
        AdProfile.interstitial(
            ids = listOf("ca-app-pub-0000000000000000/0000000002", "ca-app-pub-0000000000000000/0000000003"),
            counter = 3,
            maxPerSession = 5,
            loadAgain = true, // the next ad unit loads right after each show
        ),
    ),
    HomeBanner(
        AdProfile.banner(
            ids = listOf("ca-app-pub-0000000000000000/0000000004"),
            style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM),
        ),
    ),
    FeedNative(AdProfile.native(ids = listOf("ca-app-pub-0000000000000000/0000000005"), style = NativeStyle.LARGE_COMPACT)),
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

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/AdProfiles.kt#profiles-setup -->
```kotlin
class AdsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Adroit.initialize(
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

- `FirebaseRemoteConfigProvider` comes from the optional `io.github.thesobiaakbar:adroit-firebase` module. Without
  it, the enum alone configures the ads. It starts the shared Remote Config session itself: one fetch per app session,
  retried on later screens until it succeeds, never making the app wait ([Remote configuration](REMOTE_CONFIG.md#fetching-once-per-session)).
- `AdSettings(...)` holds the defaults of the `AdSettings` Firebase parameter (see [Settings](#settings)).
- `profiles()` can be combined with a `placements { }` block, but each key may be defined only once.
- `DEVELOPMENT` swaps every ad unit ID for Google's test ads. Debug builds therefore need no separate IDs: they show
  only test ads, whatever the enum or Firebase lists.
- An invalid definition (a negative counter, a pool size out of range…) is a configuration error. Outside PRODUCTION
  `initialize` throws, so you find out in development; in PRODUCTION, ads turn off and the app keeps running.

Refer to each ad through its entry:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/AdProfiles.kt#profiles-placements -->
```kotlin
object Ads {
    val Splash = AppAds.SplashAd.interstitialPlacement()
    val History = AppAds.HistoryAd.interstitialPlacement()
    val HomeBanner = AppAds.HomeBanner.bannerPlacement()
    val FeedNative = AppAds.FeedNative.nativePlacement()
}
```

`interstitialPlacement()` and the others throw if the entry's format is another one. In XML, use the entry name:
`app:adroit_placement="HomeBanner"`.

### Getting the JSON for Firebase

- Outside PRODUCTION, `initialize` logs every ad's JSON at debug level (`Firebase parameter HistoryAd: {…}`), ready to
  paste as the value of that ad's parameter.
- `AdDefinitions.firebaseTemplate(AppAds.entries, settings)` returns a whole Remote Config template: one JSON
  parameter per ad, plus `AdSettings`. Save it as a file and import it in the Firebase console (Remote Config, ⋮,
  **Publish from a file**) to create every parameter at once. Importing replaces every parameter of the project, so on
a project that already uses Remote Config, add the parameters one by one instead. `sample-xml/firebase/remote_config_template.json` is
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
      "maxPerSession": 5,
      "loadAgain": true
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
      "style": "large_compact",
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
| `loadAgain` | true/false | `false` | `true` loads the next ad (the next ID of `adUnitIds`) right after each show; see [Loading](#loading). Not for banners. |
| `loadTimeoutSeconds` | number | from `AdSettings` (`8`) | Full-screen ads: how long a show waits for an ad that isn't loaded yet, then gives up; see [Loading](#loading). `0` doesn't wait. |
| `showLoading` | true/false | from `AdSettings` (`true`) | A ["Loading ad…" screen](#loading) before a full-screen ad; a [shimmer](#loading) while a banner or native ad loads. |
| `loadingSeconds` | number | from `AdSettings` (`1`) | Full-screen ads: how long the loading screen stays up at least, when the ad is already loaded. |

### Interstitial and rewarded interstitial

| Field | Type | Default | Meaning |
|---|---|---|---|
| `counter` | number, or list of numbers | `0` | 0 = off. The max counter: with 3, an ad shows on the 3rd, 6th, 9th `show` call. Other calls return `NotShown(CounterNotReached)`. A list, `[3, 2]`, is several max counters used one after the other. See [Counter](#counter). |
| `counterStart` | number | `0` | The counter's value when the app starts. Decimals allowed. |
| `counterStep` | number | `1` | What each `show` call adds to the counter. Above 0; decimals allowed (`0.5`, `1.25`). |
| `counterResetOnFail` | true/false | `false` | When the ad is due and can't be shown: `false` tries again on the very next call, `true` counts again from 0. |
| `loadAhead` | number | `1` | How many calls before it's due the ad loads: `0` on the call it's due, `1` one call earlier, `2` two calls earlier… See [Where the ad loads](#where-the-ad-loads). |
| `timerSeconds` | number | `0` | 0 = off. The [timer](#timer): the minimum time between two showings of this ad, and when its next ad is requested. Ignored while `counter` is set. |
| `maxPerSession` | number | `0` | 0 = no limit. Showings of this ad per app session. |
| `splash` | true/false | `false` | Interstitial only, file only. The splash ad: shown with `showOnSplash`, not counted in the overall limit, and no counter. |
| `splashTimeoutSeconds` | number | `8` | How long `showOnSplash` waits for an ad. |

### Rewarded, app open, banner, native

| Format | Field | Meaning |
|---|---|---|
| rewarded | `maxPerSession` | 0 = no limit |
| app_open | `timerSeconds` | The cooldown between app open ads (default 0: no cooldown) |
| app_open | `maxPerSession` | 0 = no limit |
| app_open | `excludedActivities` | File only. Activity class names never covered by an app open ad, for example your splash screen |
| banner | `style` | `adaptive` (default), `collapsible_bottom`, `collapsible_top`, `rectangle` (300×250), `standard` (320×50) or `inline_adaptive` |
| native | `style` | A library style: `small_button_end`, `small_button_bottom`, `large_button_bottom` (default), `large_compact` or `media_start` ([Native](NATIVE.md#templates-and-custom-layouts)). Or the name of your own layout, registered with `NativeTemplates.register` ([Native](NATIVE.md#your-own-layout-picked-by-name)). |
| native | `ctaColors` | One color, or two for a left-to-right gradient: the call-to-action's background, `["#FF6D00", "#FFC107"]` |
| native | `ctaTextColor`, `attributionColor`, `attributionTextColor` | The button's text, and the "Ad" badge's background and text: `"#RRGGBB"` or `"#AARRGGBB"` ([Native](NATIVE.md#colors-from-firebase)) |
| native | `poolSize` | File only. Ads kept loaded, 1 to 3 (default 1) |

### Settings

| Field | Type | Default | Meaning |
|---|---|---|---|
| `adsEnabled` | true/false | `true` | `false` turns every ad off |
| `maxInterstitialsPerSession` | number | `0` | 0 = no limit. Interstitials per session, splash ones not counted. When reached, no other interstitial shows, even if its own `maxPerSession` allows more. |
| `minSecondsBetweenFullScreenAds` | number | `0` | The minimum time between any two full-screen ads |
| `splashSeconds` | number | `11` | The splash timer ([Splash screen](SPLASH.md)) |
| `premiumSplashSeconds` | number | `3` | The splash timer for a premium user |
| `offlineSplashSeconds` | number | `3` | The splash timer without internet |
| `consentTimeoutSeconds` | number | `8` | How long the consent form may take to appear on the splash |
| `splashAdMode` | text | `"show"` | `"show"`: the splash shows its ad. `"preload"`: it loads the ad for the next screen. `"later"`: it doesn't load it. The last two use the short splash timer ([Splash screen](SPLASH.md#showing-the-splash-ad-later)) |
| `loadTimeoutSeconds` | number | `8` | The load timeout of every full-screen ad that doesn't set its own |
| `showLoading` | true/false | `true` | The loading screen and shimmer of every ad that doesn't set its own |
| `loadingSeconds` | number | `1` | The loading screen's minimum time for every full-screen ad that doesn't set its own |
| `ctaColors`, `ctaTextColor`, `attributionColor`, `attributionTextColor` | colors | the app's resources | The button and "Ad" badge colors of every native ad that doesn't set its own ([Native](NATIVE.md#colors-from-firebase)) |

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
- A Firebase value can change an ad's `format` only within its pair; see [Swapping formats](#swapping-formats).
- The file-only fields (`splash`, `excludedActivities`, `poolSize`) are ignored in Firebase values.
- An invalid value, or a value that isn't JSON, is ignored with a warning in the log. An invalid field is dropped,
  and the rest of the value still applies.
- An invalid ad unit ID is skipped and the others are used. If none is valid, the file's IDs stay.
- In PRODUCTION, Google's demo IDs are rejected.
- Ad-free users see no ads, whatever Firebase says: `setAdFree(true)` always wins. Precedence is as in
  [Remote configuration](REMOTE_CONFIG.md#precedence).

The values are fetched once per session, without making anything wait, and with no call from the app
([Remote configuration](REMOTE_CONFIG.md#fetching-once-per-session)). Pass `settingsKey` to use a name other than
`AdSettings`.

## Counter and timer

An interstitial or a rewarded interstitial follows one of three strategies. Its `counter` and `timerSeconds` choose
it, and Firebase can change them at any time:

| `counter` | `timerSeconds` | Strategy |
|---|---|---|
| `0` | `0` | None. Every `show` call may show an ad. |
| set | anything | [Counter](#counter). The timer is ignored. |
| `0` | set | [Timer](#timer). |

### Counter

Call `AdSlots.showFullScreen` every time the event happens (a tap, a level end); the counter decides.

1. The counter starts at `counterStart` when the app starts.
2. Every `show` call adds `counterStep` to it.
3. When it reaches the max counter (`counter`), the ad is due and shows.
4. When the ad was really shown, the counter goes back to 0 and counting starts again, toward the next max counter
   if `counter` is a list.

The plain case, an ad on every 3rd call:

```json
{ "counter": 3 }
```

Several max counters, with a start. The counter starts at 1, so the first ad comes on the 2nd call; the next one 2
calls later, the one after 3 calls later, then 2, then 3, and so on:

```json
{ "counter": [3, 2], "counterStart": 1, "counterStep": 1 }
```

| Call | Counter | Max counter | Result |
|---|---|---|---|
| 1 | 1 → 2 | 3 | no ad |
| 2 | 2 → 3 | 3 | **ad**, counter back to 0 |
| 3 | 0 → 1 | 2 | no ad |
| 4 | 1 → 2 | 2 | **ad**, counter back to 0 |
| 5, 6 | 0 → 1 → 2 | 3 | no ad |
| 7 | 2 → 3 | 3 | **ad**; the next max counter is 2 again |

A smaller step makes the same max counter take more calls: with `"counter": 3, "counterStep": 0.5`, the ad shows on
the 6th call. `counterStart` counts once, at the start of the session; after every showing the counter restarts at 0.

**When a due ad can't be shown** (it didn't load, the user is offline, another limit said no), the counter isn't
lost. `counterResetOnFail` chooses what happens next:

| `counterResetOnFail` | After a due ad wasn't shown |
|---|---|
| `false` (default) | The counter keeps its value: the very next call tries to show the ad again. |
| `true` | The counter goes back to 0: the ad is due again after a full count to the same max counter. |

The counter only goes back to 0 and moves to the next max counter when an ad was really shown (its impression).

#### Where the ad loads

`loadAhead` says how many calls before the ad is due the library requests it, so it's ready in time without loading
ads nobody will see:

| You want the ad to load… | Set |
|---|---|
| One call before it's due (the default: with a max counter of 3, on the 2nd call) | `"loadAhead": 1` |
| Two calls before it's due, for slow networks | `"loadAhead": 2` (and so on) |
| Only on the call it's due, and show as soon as it arrives | `"loadAhead": 0`. The show waits up to `loadTimeoutSeconds` behind the "Loading ad…" screen. |
| Only on the call it's due, without ever making the user wait | `"loadAhead": 0, "loadTimeoutSeconds": 0`. That call returns `NotShown(NotReady)`; the next call shows the ad. |
| Right after each ad is shown, so one is always ready | `"loadAgain": true` |

An `AdSlots.load` call from your app still loads the ad at once, whatever `loadAhead` says.

### Timer

With `"counter": 0` and, for example, `"timerSeconds": 30`:

1. The ad is shown (its first load is your `AdSlots.load` call, or the first `show`, which waits for it).
2. The timer starts. For 30 seconds this ad isn't shown again (`NotShown(FrequencyCapped)`), and nothing is requested,
   not even by `AdSlots.load`.
3. After 30 seconds the library requests the next ad, once.
4. That ad stays loaded, however long it takes, until a `show` call shows it. No other request is made meanwhile.
5. Once it's shown, the timer starts again: back to step 2.

```json
{ "counter": 0, "timerSeconds": 30 }
```

With `"loadAgain": true` as well, the next ad is requested right after each show instead of when the timer ends; the
timer then only spaces the showings. A request that fails when the timer ends isn't repeated by itself: the next
`AdSlots.load` or `show` call asks again. For an app open ad, `timerSeconds` works the same way.

## Loading

Ads load only when your app asks, so no ad loads that the app won't show, which keeps AdMob's show rate (shown ads
out of loaded ones) high:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/AdProfiles.kt#profiles-load -->
```kotlin
override fun onResume() {
    super.onResume()
    // Ads load only when asked: load HistoryAd now, so it's ready when the user opens the history. After a failed
    // load, the next resume asks again. Nothing happens if it's loaded or loading already.
    AdSlots.load(AppAds.HistoryAd)
}
```

- `AdSlots.load` does nothing when the ad is loaded or loading already, so calling it on every `onResume` is fine. A
  banner or native ad also loads when its view asks for one, and the splash ad when `SplashFlow` requests it.
- **Banners and native ads, ahead of their screen.** `AdSlots.load` works for them too: load the next screen's ads
  before it opens, and its views show them at once.

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/AdProfiles.kt#profiles-preload-inline -->
```kotlin
fun preloadNextScreenAds() {
    // The next screen's banner and native ad, loaded now. Its AdSlotView (or any banner or native view) shows each at
    // once, or when it arrives if it's still loading; without this, each loads when its view appears.
    AdSlots.load(AppAds.HomeBanner)
    AdSlots.load(AppAds.FeedNative)
}
```

A view created while its ad is still loading shows it when it arrives, without a second request; if that load
fails, the view loads its own. A banner is loaded at the window's width, for a full-width banner: a banner view of
another width (more than 8dp off) loads its own, and the preloaded one is dropped. A loaded ad waits at most an hour,
and none waits while ads are off (ad-free, consent). Refresh (`load(refresh = true)`) always loads a new ad.

- A failed load isn't retried: the next `AdSlots.load` is the retry.
- **Load timeout.** When `AdSlots.showFullScreen` (or a rewarded show) finds no ad ready, it requests one and waits
  up to `loadTimeoutSeconds` (8 by default). If the ad arrives in time it shows; otherwise, or if the load fails,
  the result is `NotShown(Timeout)` (or the load's error) and your callback moves the app on, so a slow network never
  leaves the user stuck. An ad that arrives after the timeout is kept for the next show. While a show waits, no other
  full-screen ad can start. `0` doesn't wait: `NotShown(NotReady)` at once.
- **Loading screen.** With `showLoading` (the default), a "Loading ad…" screen covers the app from the show call:
  for `loadingSeconds` (1 by default) when the ad is ready, or for the whole wait when it isn't. It goes the moment
  the ad opens, and also when the show fails, times out, or the user leaves the screen. The splash ad never gets one:
  the splash is its loading screen. To change its look, declare a layout named `adroit_loading_ad` in your app
  (and the text with a `adroit_loading_ad` string); the library only inflates it.
- **Shimmer.** With `showLoading`, a banner or native ad shows a shimmer while it loads: the ad's whole area as a
  card, with grey blocks shaped like the ad that will appear, and a highlight sweeping across both. A banner's
  shimmer has the banner's height (adaptive, collapsible, standard, rectangle or inline, as its `style` says now); a
  native ad's is built at runtime from the layout it will show in, any style or your own layout, with a block over
  each of its views ([Native](NATIVE.md#the-shimmer)). A Firebase change of style or format changes the shimmer too. It goes when the ad appears,
  and when the load fails (the slot then collapses as before). This works in `AdSlotView`, `AdBannerView`,
  `AdNativeView`, `attachBanner`, `attachNativeAd`, `NativeAdHolder`, and the `AdSlot`, `AdBanner` and `AdNative`
  composables; a composable's own `placeholder` replaces it. Its colours are the `adroit_shimmer_card`, `adroit_shimmer_base` and
  `adroit_shimmer_highlight` colour resources (with night variants), which your app can override.
- An ad that expires unshown (after an hour for most formats) isn't replaced until the next `AdSlots.load`.
- An ad with a **counter** also loads by itself shortly before it's due (`loadAhead`), and an ad on a **timer** when
  its timer ends: see [Counter and timer](#counter-and-timer).
- After a show, an ad with **`loadAgain`** set loads the next one straight away: with `adUnitIds` of `[A, B]`, showing
  the ad from A requests one from B. Without it (the default), the ad loads again only at the next `AdSlots.load`. For
  a native ad, "shown" means handed to a view. Firebase can turn it on or off per ad: `{ "loadAgain": true }`.
- When the splash ends without showing its ad, a load still in progress is dropped, so it's never shown later.

## Showing the ads

Show every interstitial, app open and inline ad through `AdSlots` (or `AdSlotView` and the `AdSlot` composable): they
show each ad in its current format, so a [format swap](#swapping-formats) from Firebase needs no release.

Splash screen:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/AdProfiles.kt#profiles-splash -->
```kotlin
Adroit.start(this) {
    // Waits up to SplashAd's splashTimeoutSeconds, then shows it (as an interstitial, or an app open ad if
    // Firebase swapped it) or gives up. Continue either way.
    AdSlots.showOnSplash(this, AppAds.SplashAd) { goToMain() }
}
```

Interstitials with a counter: call `showFullScreen` every time the event happens, and let the counter decide.

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/AdProfiles.kt#profiles-counter -->
```kotlin
// With "counter": 3 in HistoryAd, this shows an ad on the 3rd, 6th, 9th… call. Other calls return at once
// with NotShown(CounterNotReached), and the app just continues.
AdSlots.showFullScreen(this, AppAds.HistoryAd) { result: FullScreenResult -> openHistory() }
```

Banners and native ads take their size and template from `style`:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/AdProfiles.kt#profiles-ui -->
```kotlin
@Composable
fun HomeScreenAds() {
    AdSlot(AppAds.HomeBanner) // a banner sized by HomeBanner's "style", or a native ad if Firebase swaps it
    AdSlot(AppAds.FeedNative) // a native ad in FeedNative's template, or a banner if Firebase swaps it
}
```

With Views, declare an `AdSlotView`:

```xml
<io.github.thesobiaakbar.adroit.views.AdSlotView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:adroit_placement="HomeBanner" />
```

### One ad per slot, and refresh

A banner or native slot never requests a second ad while it shows one or one is loading: attaching again, calling
`load()` again, recomposing or rotating reuses it. It requests one when it has none (its load failed). To replace a
shown ad with a new one, ask for a refresh: the new ad loads in the background while the shown one stays on screen
(no shimmer), and takes its place once loaded. If it fails, the shown ad stays.

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/AdProfiles.kt#profiles-refresh-views -->
```kotlin
fun refreshHomeAd(homeSlot: AdSlotView) {
    // Never a second request while the slot has an ad or one is loading. With refresh, the shown ad stays until a
    // new one has loaded and replaces it; if the new one fails, the shown one stays.
    homeSlot.load(refresh = true)
}
```

`AdBannerView`, `AdNativeView` and `NativeAdHolder` have the same `load(refresh)`. In Compose, change `refreshKey`:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/AdProfiles.kt#profiles-refresh-compose -->
```kotlin
@Composable
fun RefreshableHomeAd(refreshCount: Int) {
    // Each new refreshCount asks for a new ad, replaced the same way; recomposition alone never requests one.
    AdSlot(AppAds.HomeBanner, refreshKey = refreshCount)
}
```

`AdBanner` and `AdNative` take `refreshKey` too. `attachBanner` and `attachNativeAd` reuse the ad already in their
container for the same placement; use a view or a `NativeAdHolder` to refresh.

The format-specific APIs (`Adroit.interstitial`, `AdBanner`, `AdNative`, `AdBannerView`, `AdNativeView`,
`attachBanner`, `NativeAdHolder`…) keep working, but always use the ad's configured format:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/AdProfiles.kt#profiles-typed -->
```kotlin
@Composable
fun TypedAds() {
    // The format-specific APIs still work, but don't follow a swap: this is always a banner.
    AdBanner(Ads.HomeBanner)
    AdNative(Ads.FeedNative)
}
```

Without a `adroit_bannerSize` or `adroit_template` attribute, or a size or layout in code, they also
take the style from the profile. A new style applies to banners and native ads created after the new values arrive;
one already on screen keeps its style.

## Swapping formats

A Firebase value can give an ad another format, within these pairs:

| Configured as | Can become |
|---|---|
| `interstitial` | `app_open` |
| `app_open` | `interstitial` |
| `banner` | `native` |
| `native` | `banner` |

Rewarded and rewarded interstitial ads never swap: the user chose to watch them for a reward. Any other format in a
Firebase value is ignored with a warning, and so is the whole value.

Put the new `format` and **ad unit IDs of the new format** in the same value. An AdMob ad unit serves one format only,
so an interstitial unit can't show an app open ad:

```json
{ "format": "app_open", "adUnitIds": ["ca-app-pub-1234567890123456/1234567890"] }
```

- Without `adUnitIds`, PRODUCTION ignores the swap (with a warning) and keeps the configured format; the value's other
  fields still apply. Outside PRODUCTION, Google's test ad unit of the new format is used.
- The ad keeps its own counter, timer and per-session limit, and its shows count as the same ad, whatever the format.
  An interstitial swapped to app open still counts toward `maxInterstitialsPerSession`.
- The ad of the old format is released when the swap arrives, and one of the new format loads. Remove `format` (or
  set the configured one) to swap back.
- `AdSlots.showFullScreen` and `AdSlots.showOnSplash` show the ad in its current format. A splash interstitial swapped
  to app open waits for an app open ad instead.
- The app's own app open ad, swapped to an interstitial, shows that interstitial when the user returns to the app,
  with the same rules (excluded screens, launch count, cooldown).
- `AdSlotView` and `AdSlot` replace the banner with a native ad, or back, as soon as the swap arrives.
- `style` in the value is read for the new format: `{ "format": "native", "style": "large_compact", "adUnitIds": [...] }`.
- `AdSlots.currentFormat(key)` and `AdSlots.formatChanges(key)` tell you an ad's format now.

AdMob intends app open ads for app launch and returns to the app. Showing one at another moment, by swapping an
interstitial, is your choice: check the AdMob policies for your app.

## How the limits combine

For an interstitial `show`, the first "no" wins:

1. Ads can run (consent, ad-free, `adsEnabled`) and this ad is `enabled`. Otherwise `NotShown(Disabled)`.
2. The [counter](#counter), when set: the call adds `counterStep`. Not yet due → `NotShown(CounterNotReached)`.
3. `maxInterstitialsPerSession` (not for splash ads) → `NotShown(FrequencyCapped)`.
4. The ad's `maxPerSession` → `NotShown(FrequencyCapped)`.
5. `timerSeconds` (the [timer](#timer)), when no counter is set → `NotShown(FrequencyCapped)`.
6. `minSecondsBetweenFullScreenAds` → `NotShown(FrequencyCapped)`.
7. An ad is loaded; otherwise `NotShown(NotReady)`, and one loads for next time.

A due counter stays due until an ad actually shows (or, with `counterResetOnFail`, starts again from 0). Only real
showings count toward the limits, and they're counted per app session (per process), in memory.

These limits are a user-experience safeguard, not a replacement for AdMob's own frequency capping.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-10-05
