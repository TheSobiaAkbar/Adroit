# Changelog

All notable changes to CompanyAdMob are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/)
with the rules in [docs/RELEASE.md](docs/RELEASE.md). Google SDK upgrades get their own "Google SDK" section.

## [Unreleased]

### Added
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

### Changed
- A banner's size comes from its placement (or ad profile) unless the code passes one: `BannerOptions.size` and
  `AdBannerView`'s `company_admob_bannerSize` are now optional. The placement-level `size` had no effect before.
- A native ad's template comes from its placement's `style` unless the code passes a layout: the `layout` parameters
  of `AdNative`, `attachNativeAd` and `NativeAdHolder.bindTo`, and `AdNativeView`'s `company_admob_template`, are
  now optional (the default style is MEDIUM, as the default template was).
- Placement keys may contain capital letters (`[A-Za-z0-9_]{1,40}`), so they can match Firebase parameter names.
- Remote overrides drop an invalid ad unit ID on its own, instead of the placement's whole override.
- **Binary change:** `FrequencyRule` gained `counter`, which changes its constructor and `copy`. Recompile against
  this version.

### Fixed
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

[Unreleased]: https://github.com/TheSobiaAkbar/AdsPlugin/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/TheSobiaAkbar/AdsPlugin/releases/tag/v0.1.0
