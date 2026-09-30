# Ad profiles: one JSON per ad, controlled from Firebase

Design for PROMPT 25A (ADR-018). Status: **accepted** (2026-09-27). It extends
`02_ARCHITECTURE.md` §20 (configuration) and §21 (remote configuration), and replaces the unreleased
`adroit_*` Firebase keys from PROMPT 24.

## 1. Goals

The user's requirements (2026-09-27):

1. Add the library to any app with as few per-project changes as possible, ideally one file.
2. Each ad has its own Firebase Remote Config parameter, for example `HistoryAd`, whose JSON value holds:
   - on or off;
   - a list of ad unit IDs, used one after another (round robin);
   - a counter;
   - a timer;
   - a maximum per session.
3. One overall limit on interstitials per session. It doesn't count the splash interstitial (0 = unlimited) and
   wins over each ad's own limit.
4. Banners and native ads can change style from Firebase: collapsible, rectangle and so on for banners; small,
   medium or large for native ads.
5. Debug builds use Google's test ads automatically: there are no separate debug values in Firebase.
6. A non-technical integration guide, written last.

Non-goals:
- Adding a brand-new ad to the app from Firebase alone. Every ad needs a line in a screen that says where it
  appears, which no library can remove.
- Firebase A/B-testing features beyond what Remote Config conditions already give.

## 2. What changes for an app

Per project you touch:

| What | Where | Changes per project? |
|---|---|---|
| Ads config file | `res/raw/ads_config.json`: every ad's key, format and default values, including the real ad unit IDs | **Yes: the one ads file** |
| AdMob app ID | One `<meta-data>` line in `AndroidManifest.xml` (UMP reads it there) | Yes, one line |
| Firebase project | `google-services.json`, which Firebase itself requires | Yes, but it's Firebase's file |
| Setup | One call in `Application.onCreate` | No: identical in every project |
| Where ads appear | For example `AdBanner(BannerPlacement("HomeBanner"))` in a screen | Only where the app's screens differ |

The Firebase parameters use **the same JSON** as the file: the file is also the copy-paste source for the
Firebase console. Values in the file are the defaults, used until Firebase has delivered values: on the first
launch, offline, or when a Firebase parameter is missing or invalid.

```kotlin
// Application.onCreate: the same in every project
Adroit.initialize(this, adMobConfig {
    environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
    profiles(R.raw.ads_config)                  // the ads file
    remoteConfig = FirebaseRemoteConfigProvider() // Firebase values override the file, key by key
})
```

`DEVELOPMENT` already swaps every ad unit ID for Google's test ads (architecture §20.3). That covers the debug set:
debug builds read the same JSON and still show only test ads, whatever real IDs it lists.

## 3. The ads file and the Firebase values

### 3.1 File layout

```json
{
  "settings": {
    "adsEnabled": true,
    "maxInterstitialsPerSession": 0,
    "minSecondsBetweenFullScreenAds": 0
  },
  "ads": {
    "SplashAd": {
      "format": "interstitial", "enabled": true, "splash": true, "splashTimeoutSeconds": 8,
      "adUnitIds": ["ca-app-pub-0000000000000000/0000000001"]
    },
    "HistoryAd": {
      "format": "interstitial", "enabled": true,
      "adUnitIds": ["ca-app-pub-0000000000000000/0000000002", "ca-app-pub-0000000000000000/0000000003"],
      "counter": 3, "timerSeconds": 0, "maxPerSession": 5
    },
    "HomeBanner": {
      "format": "banner", "enabled": true, "style": "collapsible_bottom",
      "adUnitIds": ["ca-app-pub-0000000000000000/0000000004"]
    },
    "FeedNative": {
      "format": "native", "enabled": true, "style": "large",
      "adUnitIds": ["ca-app-pub-0000000000000000/0000000005"]
    }
  }
}
```

### 3.2 In Firebase

- **One parameter per ad**, named exactly like its key in the file (`SplashAd`, `HistoryAd`, `HomeBanner`,
  `FeedNative`). Its value is that ad's JSON object, of type JSON.
- **One parameter for the settings**, named `AdSettings` by default (configurable), whose value is the
  `settings` object.
- Anything left out of a Firebase value keeps the file's value. For example, `{ "enabled": false }` turns just that
  ad off.
- A Firebase value can't change an ad's `format`. A value with a different format is ignored, with a warning.

Keys follow Firebase's own rule: letters, digits and underscores, starting with a letter or underscore, up to 40
characters. Placement keys today allow only `[a-z0-9_]`, so they're widened to `[A-Za-z0-9_]{1,40}`. That's a
relaxation, and every existing key stays valid.

### 3.3 Fields

Every ad:

| Field | Type | Default | Meaning |
|---|---|---|---|
| `format` | string | required (file only) | `interstitial`, `rewarded`, `rewarded_interstitial`, `app_open`, `banner` or `native` |
| `enabled` | bool | `true` | `false` turns this ad off |
| `adUnitIds` | list of strings | required | Used one after another, see §4.1. In the file, an invalid ID is a configuration error, like one in `placements { }` (see below). In a Firebase value, an invalid ID is skipped with a warning; if none is left, the file's IDs stay. |

Interstitial and rewarded interstitial:

| Field | Type | Default | Meaning |
|---|---|---|---|
| `counter` | int | `0` | 0 = off. N = shows at most on every Nth call, see §4.2. |
| `timerSeconds` | int | `0` | 0 = off. The minimum gap between two showings of **this** ad. Ignored when `counter` > 0. |
| `maxPerSession` | int | `0` | 0 = no limit for this ad. The overall limit can still stop it. |
| `splash` | bool | `false` | Interstitial only. Marks the splash interstitial: not counted in the overall limit, and shown with `showOnSplash` (§4.4). |
| `splashTimeoutSeconds` | int | `8` | How long `showOnSplash` waits for an ad before letting the app continue. |

Rewarded: `maxPerSession` only (0 = no limit). The user asks for a rewarded ad, so no counter or timer.

App open: `timerSeconds` (the cooldown) and `maxPerSession`, plus `excludedActivities` (Activity class names, file
only), so the splash screen can be excluded without code. The launch count and cold-start rules keep their defaults.

Banner:

| Field | Values | Default |
|---|---|---|
| `style` | `adaptive` (anchored adaptive), `collapsible_bottom`, `collapsible_top`, `rectangle` (300×250), `standard` (320×50), `inline_adaptive` | `adaptive` |

A collapsible banner is an adaptive banner requested with Google's `collapsible` extra. Google's rules: only
Google demand collapses (mediated ads show as normal banners), and automatic refreshes aren't collapsible.
(Source: developers.google.com/admob/android/next-gen/banner/collapsible.)

Native:

| Field | Values | Default |
|---|---|---|
| `style` | `small`, `medium`, `large` (the library's three templates) | `medium` |
| `poolSize` | 1 to 3, file only | `1` |

`settings`:

| Field | Type | Default | Meaning |
|---|---|---|---|
| `adsEnabled` | bool | `true` | `false` turns every ad off |
| `maxInterstitialsPerSession` | int | `0` | 0 = unlimited. Interstitials shown this session, not counting splash ones. When reached, no more non-splash interstitials show, whatever each ad's own `maxPerSession` says. |
| `minSecondsBetweenFullScreenAds` | int | `0` | The existing global gap between any two full-screen ads. 0 = off. |

Unknown fields are ignored, so an older app accepts newer JSON.

- **In a Firebase value**, an invalid field (wrong type, negative number, unknown style) is ignored with a warning,
  and the rest of the value still applies.
- **In the file**, any problem (an invalid field, a missing `format` or `adUnitIds`, a malformed or, in PRODUCTION,
  demo ad unit ID) is a configuration error. Validation reports it like a problem in `placements { }`: `initialize`
  throws outside PRODUCTION, so it's caught in development. In PRODUCTION, ads turn off and the app keeps running.
- **File-only fields** (`splash`, `excludedActivities`, `poolSize`) are ignored in Firebase values, with a warning.

## 4. Behaviour

### 4.1 Ad unit ID rotation

- Every load request for an ad (preload, reload after a show, retry after a failure) uses the **next** ID in its
  `adUnitIds` list. After the last one, it goes back to the first.
- A failed load therefore moves on to the next ID, like a simple waterfall.
- The position is kept in memory and starts at the first ID on every app launch. It isn't stored: it's not an
  identifier.
- Ads with the same format and the same ID list share one cache and one position, as ads with the same single ID
  share a cache today.
- When Firebase changes the list, the next load starts at the new list's first ID. An ad already loaded from the
  old list is kept until shown, unless its ad is turned off.
- Banners: each new banner load (a banner appearing, or a reload after a size change) uses the next ID. Google's
  automatic refresh reuses the same ID.
- In debug builds every ID is replaced by Google's test ID, but debug logs still show the position
  ("HistoryAd: ID 2 of 3").

### 4.2 Counter

- Every `show(activity, placement)` call counts as one "trigger" for that ad.
- With `counter = N`, an ad shows only once N triggers have been counted since its last showing. For example, with
  3 it can show on the 3rd call, then the 6th, and so on.
- Before that, the call returns `NotShown(CounterNotReached(remaining))`, and the app simply continues.
- Once the counter is due, it **stays due** until an ad actually shows. If the ad wasn't ready, or another limit
  applied, the next call tries again.
- After a showing, counting starts again from 0.
- Counts are in memory and restart every launch.
- While `counter` > 0, `timerSeconds` is ignored, as requested. The overall limit, `maxPerSession` and the global
  gap still apply.

### 4.3 Order of checks for an interstitial `show`

Each step stops at the first "no":

1. Ads can run (consent, ad-free, `adsEnabled`) and this ad is `enabled`. Otherwise `Disabled`.
2. Counter, when `counter` > 0: count the trigger. Not yet due → `CounterNotReached`.
3. The overall `maxInterstitialsPerSession` (skipped for splash ads). Reached → `FrequencyCapped`.
4. This ad's `maxPerSession`. Reached → `FrequencyCapped`.
5. `timerSeconds`, only when `counter` = 0. Not elapsed → `FrequencyCapped(remaining)`.
6. The global gap, `minSecondsBetweenFullScreenAds`.
7. An ad is loaded. Otherwise `NotReady`, and a load starts.

Only a real showing counts toward the limits and resets the counter.

### 4.4 Splash interstitial

- `Adroit.interstitial.showOnSplash(activity, placement, onResult)` waits up to `splashTimeoutSeconds` for
  the library to be ready (consent first) and for an ad to load. It then shows the ad, or gives up and lets the
  app continue.
- The result arrives exactly once, like every show.
- A splash ad isn't counted in `maxInterstitialsPerSession`, and doesn't use the counter.
- It's modelled on the existing `appOpen.showOnColdStart`.

### 4.5 Banner and native style

- `AdBanner(placement)`, `AdBannerView` and `attachBanner` take their size from the ad's `style` when the code
  doesn't pass one.
- `AdNative(placement)` and `AdNativeView` take their template from `style` when the code doesn't pass a layout.
- A size or layout passed in code still wins, which is useful for a custom layout. The guide will say: don't pass
  one if Firebase should control it.
- A new style applies to banners and native ads **created after** the new Firebase values are active. One already
  on screen keeps its style until it's shown again.

### 4.6 Live updates

- Firebase values apply when Firebase activates them (§21): usually at the next launch after a fetch, or at once
  with real-time updates.
- The precedence is unchanged: ad-free, then the remote kill switch, then remote per-ad values, then the file.

## 5. Library changes (summary)

| Module | Change |
|---|---|
| adroit-core | Placements get a list of ad unit IDs (`adUnitIds`; `adUnitId` stays as the first entry). The profile JSON parser (on the platform's `org.json`, with no new dependency). `adMobConfig { profiles(rawRes) }` builds the placements and settings from the file. Rotation in the full-screen, native and banner engines. Counter and overall interstitial limit in `FrequencyGate`. `showOnSplash`. `BannerSize.Collapsible(position)` and the style-to-size mapping. A native style value. New `AdError.CounterNotReached`. Remote overrides carry the new fields. Placement keys widened. |
| adroit-views, adroit-compose | Size and template default to the ad's style. |
| adroit-firebase | `FirebaseRemoteConfigProvider` reads one parameter per ad key from the ads file, plus `AdSettings`, parsed by core's parser. The unreleased `adroit_*` keys are removed. |
| samples | sample-xml switches to an `ads_config.json` with a static provider (no Firebase, per the sample rules). The docs snippets show the Firebase setup. |
| docs | `docs/AD_PROFILES.md` (reference: every field, the check order, examples), plus updates to CONFIGURATION, REMOTE_CONFIG, BANNER, NATIVE and INTERSTITIAL. The plain-language `docs/INTEGRATION_GUIDE.md` comes after PROMPT 27. |

Public API additions (reviewed in PROMPT 27):
- `profiles(...)` in the configuration DSL;
- `PlacementConfig.adUnitIds`;
- `counter`, `splash` and the collapsible banner size;
- the native style;
- `showOnSplash`;
- `AdError.CounterNotReached`.

Adding a subtype to the sealed `AdError` breaks an exhaustive `when` in app code. That's acceptable before 1.0,
and it goes in the CHANGELOG.

## 6. Decisions confirmed by the user (2026-09-27)

1. The file is `res/raw/ads_config.json`, and the settings parameter is `AdSettings`.
2. `maxInterstitialsPerSession` counts interstitials only, not splash ones. Rewarded interstitials and app open ads
   keep their own limits.
3. Counter: with 3, the ad shows on the 3rd, 6th, 9th… call.
