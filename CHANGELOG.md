# Changelog

All notable changes to CompanyAdMob are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/)
with the rules in [docs/RELEASE.md](docs/RELEASE.md). Google SDK upgrades get their own "Google SDK" section.

## [Unreleased]

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
