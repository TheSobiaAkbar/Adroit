# Changelog

All notable changes to CompanyAdMob are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/)
with the rules in [docs/RELEASE.md](docs/RELEASE.md). Google SDK upgrades get their own "Google SDK" section.

## [Unreleased]

### Added
- `NetworkMonitor` (admob-core): the connection state as a `StateFlow<NetworkState>`: `Unavailable`,
  `ConnectedNoInternet` (for example Wi-Fi behind a sign-in page) or `Connected` over Wi-Fi, mobile data or ethernet.
- `RemoteConfigSession` (admob-firebase): fetches Remote Config once per app session without blocking. After a
  failure it tries again on each new Activity and when the device comes back online. `FirebaseRemoteConfigProvider`
  re-reads its values as soon as the session activates them.
- Ad definitions (`docs/AD_PROFILES.md`): an enum implementing `AdDefinition`, each entry holding a typed
  `AdProfile` default (`AdProfile.interstitial(...)`, `banner(...)`, …), registered with
  `adMobConfig { profiles(AppAds.entries, AdSettings(...)) }`. The entry name is the ad's Firebase parameter, which
  overrides the defaults as with an ads file. `AdDefinitions.firebaseTemplate(...)` writes a Remote Config template
  for the console's "Publish from a file"; outside PRODUCTION, `initialize` logs every ad's JSON.
- Format swapping (`docs/AD_PROFILES.md`, "Swapping formats"): a Firebase value with `"format"` turns an interstitial
  into an app open ad or back, and a banner into a native ad or back, with ad unit IDs of the new format (PRODUCTION
  ignores a swap without them). The ad keeps its counter, timer and caps. `AdSlots.showFullScreen`,
  `AdSlots.showOnSplash`, `AdSlotView` (admob-views) and `AdSlot` (admob-compose) show an ad in its current format;
  a swapped app open ad shows its interstitial on returns to the app. `PlacementOverride.format`, through a new
  constructor; the 1.0.0-rc1 constructor is unchanged.

### Changed
- An ad without ad unit IDs (in a definition or an ads file) uses Google's test ad unit for its format instead of
  being a configuration error. PRODUCTION logs a warning for it.

## [1.0.0-rc1] - 2026-09-28

The public API is frozen for 1.0.0 (`docs/audits/API_STABILITY_AUDIT.md`): from here on, the rules in
`docs/RELEASE.md` "API stability" apply.

### Added
- `AppOpenApi.showOnColdStart(activity, maxWaitMillis, onResult)`, for Java callers (ADR-013).
- `@JvmOverloads` on the public constructors with default arguments: `BannerOptions`, `AdError.Internal`,
  `FirebaseAnalyticsOptions`, `FirebaseAnalyticsEventListener`, `FirebaseRemoteConfigProvider`, and admob-testing's
  `FakeNativeAdHandle` (and its `block`).
- Ad profiles (`docs/AD_PROFILES.md`): `adMobConfig { profiles(context, R.raw.ads_config) }` defines every ad in one
  JSON file, and each ad can be changed from a Firebase Remote Config parameter of the same name.
- Several ad unit IDs per placement (`adUnitIds`, and a list overload for every format in `placements { }`), used in
  rotation: each load request takes the next one.
- `FrequencyRule.counter`: an interstitial or rewarded interstitial shows at most on every Nth `show` call, and new
  `AdError.CounterNotReached`.
- `FrequencyOptions.maxInterstitialsPerSession`: an overall interstitial limit per session, splash ones not counted.
- `InterstitialApi.showOnSplash`, and `splash`/`splashTimeout` for interstitial placements.
- `BannerSize.Collapsible` (Google's collapsible banners) and `CollapsibleBannerPosition`; the XML sizes
  `collapsibleBottom` and `collapsibleTop`.
- `NativeStyle`, a native placement's `style`, `NativeApi.style(placement)` and `NativeTemplates.forStyle(style)`.
- `admob-firebase` (optional): `FirebaseRemoteConfigProvider`, which reads one Remote Config parameter per ad (and an
  `AdSettings` parameter) as remote overrides, and `FirebaseAnalyticsEventListener`, which logs paid events as Firebase
  `ad_impression` events (and optional custom ad events). Firebase BoM 34.19.0.

### Changed (breaking)
- The placement classes implement `AdPlacement` directly: the public `AdPlacementBase` superclass is gone. Code
  that named `AdPlacementBase` must use `AdPlacement`; recompile against this version.
- `FrequencyRule` is a regular class, no longer a data class (ADR-011): `copy` and `componentN` are gone, so construct
  a new rule instead. It also gained `counter` (see Added), which changed its constructor.
- `AppOpenApi.showOnColdStart`'s callback parameter is `onResult` (was `onComplete`), like every other show call.
  Only calls that name the argument change.

### Changed
- A banner's size comes from its placement (or ad profile) unless the code passes one: `BannerOptions.size` and
  `AdBannerView`'s `company_admob_bannerSize` are now optional. The placement-level `size` had no effect before.
- A native ad's template comes from its placement's `style` unless the code passes a layout: the `layout` parameters
  of `AdNative`, `attachNativeAd` and `NativeAdHolder.bindTo`, and `AdNativeView`'s `company_admob_template`, are
  now optional (the default style is MEDIUM, as the default template was).
- Placement keys may contain capital letters (`[A-Za-z0-9_]{1,40}`), so they can match Firebase parameter names.
- Remote overrides drop an invalid ad unit ID on its own, instead of the placement's whole override.

### Fixed
- The AARs asked apps to compile against API 37 (AGP 9 publishes the library's compileSdk as `minCompileSdk`). They
  now ask for 34, what their dependencies need, so apps on compileSdk 34 to 36 and AGP 8.1.1 or newer can use
  admob-core and admob-views. admob-compose still needs compileSdk 37 and AGP 9.1, from Compose 1.12.
- Sealed types and enums state in their KDoc whether they may gain values (include an `else` branch) or are closed.
- No disk I/O from the library on the main thread (`docs/audits/PERFORMANCE_AUDIT.md`): `initialize()` reads and
  updates the launch count and loads UMP's stored consent state on a background thread, and `start()` writes
  `gad_rdp` on the SDK's initialization thread, still before the SDK starts.
- `initialize()` no longer triggers the `ServiceLoader` lookup of `Dispatchers.Main`, which opened the APK on the
  main thread (60 to 80 ms per cold start in debuggable builds).
- The views and Compose modules log through the host's configured logger and level, like core.
- `docs/CONSENT.md`: the Consent Mode section had the order wrong. Firebase must be initialized before UMP starts,
  and UMP 3.2.0 or newer applies the consent choices itself.

### Security
- In PRODUCTION, every log line shortens full ad unit IDs, including retry messages that printed them in full when
  the log level was lowered.
- A `GoogleRequestCustomizer` that fails with a linkage error (for example `NoSuchMethodError` after a Google SDK
  upgrade) is contained like an exception, instead of crashing the load.

## [0.1.0] - 2026-09-26

First internal release.

### Added
- `admob-core`:
  - One configuration DSL (`adMobConfig { }`, plus a Java builder) with DEVELOPMENT, STAGING and PRODUCTION
    environments, test-ad safety rules and full validation.
  - Consent with UMP before any ad request: the privacy options entry point, debug geography, age treatment, max ad
    content rating, restricted data processing and consent sync.
  - Initialization state (`AdsState`) and a `start()` that's safe to call from any launcher.
  - Interstitial, rewarded, rewarded interstitial and app open ads: preloading, retries with backoff, expiry, frequency
    limits, one full-screen ad at a time, and a result callback (or `showAndAwait`) that's always called exactly once.
  - Rewards identified by `rewardId` for exactly-once granting, with `unclaimedResults` for rewards earned after the
    screen is gone.
  - App open ads on foreground and on cold start, with cooldown, launch-count rule, Activity exclusions,
    `skipNextForeground` and `suppressWhile`.
  - Banners (anchored adaptive, inline adaptive, medium rectangle, standard) and native ads with pools and
    lifecycle-bound handles.
  - The ad-free switch, remote overrides (`AdConfigProvider`), the `AdEvent` stream and listeners (including paid
    events), pluggable logging, diagnostics and Ad Inspector.
- `admob-views`: `AdBannerView`, `AdNativeView`, `attachBanner`, `attachNativeAd`, `NativeAdHolder` and three native
  templates.
- `admob-compose`: `AdBanner`, `AdNative`, `rememberNativeAd`, `rememberAdAvailability` and
  `rememberFullScreenAdLauncher`.
- `admob-testing`: `FakeAdsClient`, fake handles and `CompanyAdMobTestRule`.
- Consumer R8 rules: one rule, for WorkManager's database, which the Google SDK brings in with Room 2.2.5. Without it,
  every minified app crashed at launch (see `docs/architecture/R8_REPORT.md`).

### Google SDK
- Built against Google Mobile Ads Next-Gen SDK `ads-mobile-sdk` 1.4.0 and UMP 4.0.0.

[Unreleased]: https://github.com/TheSobiaAkbar/AdsPlugin/compare/v1.0.0-rc1...HEAD
[1.0.0-rc1]: https://github.com/TheSobiaAkbar/AdsPlugin/compare/v0.1.0...v1.0.0-rc1
[0.1.0]: https://github.com/TheSobiaAkbar/AdsPlugin/releases/tag/v0.1.0
