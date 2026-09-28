# CompanyAdMob

[![CI](https://github.com/TheSobiaAkbar/AdsPlugin/actions/workflows/ci.yml/badge.svg)](https://github.com/TheSobiaAkbar/AdsPlugin/actions/workflows/ci.yml)
[![Instrumentation](https://github.com/TheSobiaAkbar/AdsPlugin/actions/workflows/instrumentation.yml/badge.svg)](https://github.com/TheSobiaAkbar/AdsPlugin/actions/workflows/instrumentation.yml)

CompanyAdMob is the company's Android library for AdMob. It wraps the
[Google Mobile Ads Next-Gen SDK](https://developers.google.com/admob/android/next-gen/quick-start) and the
User Messaging Platform (UMP) behind one small Kotlin API. It covers consent, initialization, preloading and retries,
frequency limits, lifecycle-safe banners and native ads, full-screen ads that always call you back, rewards granted
exactly once, test-ad safety, analytics events and remote kill switches. No Google type appears in its API.

## Modules

| Artifact | Use it for |
|---|---|
| `com.company:admob-core` | Everything: configuration, consent, every ad format, events. Required. |
| `com.company:admob-views` | XML and View apps: `AdBannerView`, `AdNativeView`, `attachBanner`, `NativeAdHolder`, native templates. |
| `com.company:admob-compose` | Jetpack Compose: `AdBanner`, `AdNative`, `rememberAdAvailability`, `rememberFullScreenAdLauncher`. |
| `com.company:admob-testing` | Unit tests without Google: `FakeAdsClient`, `CompanyAdMobTestRule`. Test scope only. |
| `com.company:admob-firebase` | Optional: each ad controlled by its own Firebase Remote Config parameter, and ad events to Google Analytics for Firebase. |

`admob-views` and `admob-compose` bring `admob-core` with them.

## Quick start

```kotlin
// build.gradle.kts
dependencies { implementation("com.company:admob-compose:0.1.0") }

// Application.onCreate
CompanyAdMob.initialize(this, adMobConfig {
    environment = AdEnvironment.DEVELOPMENT // Google's demo ads while developing
    placements { banner(BannerPlacement("home_banner"), "ca-app-pub-0000000000000000/0000000001") }
})

// Launcher Activity.onCreate: consent, then SDK initialization
CompanyAdMob.start(this)

// A screen
AdBanner(BannerPlacement("home_banner"))
```

Add your AdMob app ID to the manifest too: the Google SDKs require it. See
[Getting started](docs/GETTING_STARTED.md) for the full setup.

To define every ad in one enum and change each one from Firebase without a release (ad unit ID rotation, format swaps,
counters, timers, limits, banner and native styles), see [Ad profiles](docs/AD_PROFILES.md). For a splash screen with consent, a timer and a splash ad that never gets stuck, see
[Splash screen](docs/SPLASH.md).

## Supported versions

| | |
|---|---|
| minSdk | 24 |
| Kotlin (consumers) | 2.1 or newer (the libraries' metadata targets Kotlin 2.2; tested with 2.1.20 and 2.2.10) |
| Java bytecode | 11 |
| Google Mobile Ads Next-Gen SDK | `ads-mobile-sdk` 1.4.0 (a runtime dependency) |
| UMP | `user-messaging-platform` 4.0.0 (a runtime dependency) |
| compileSdk (consumers) | 34 or newer; 37 with `admob-compose` (Compose 1.12 requires it) |
| Android Gradle plugin (consumers) | 8.1.1 or newer (tested with 8.1.4, 8.7.2, 8.13.2 and 9.1.1); 9.1.0 or newer with `admob-compose` (Compose 1.12 requires it) |

Apps using AdMob Mediation must exclude the legacy `play-services-ads`: see [Mediation](docs/MEDIATION.md).

## Documentation

- [Getting started](docs/GETTING_STARTED.md) · [Architecture overview](docs/ARCHITECTURE.md) ·
  [Configuration](docs/CONFIGURATION.md) · [Consent](docs/CONSENT.md)
- Formats: [Banner](docs/BANNER.md) · [Interstitial](docs/INTERSTITIAL.md) · [Rewarded](docs/REWARDED.md) ·
  [Rewarded interstitial](docs/REWARDED_INTERSTITIAL.md) · [App open](docs/APP_OPEN.md) · [Native](docs/NATIVE.md)
- UI toolkits: [Compose](docs/COMPOSE.md) · [XML and Views](docs/XML.md)
- [Ad profiles](docs/AD_PROFILES.md) · [Splash screen](docs/SPLASH.md) · [Mediation](docs/MEDIATION.md) · [Analytics](docs/ANALYTICS.md) ·
  [Remote config](docs/REMOTE_CONFIG.md) ·
  [Testing](docs/TESTING.md) · [Troubleshooting](docs/TROUBLESHOOTING.md) · [Migration](docs/MIGRATION.md) ·
  [Releasing](docs/RELEASE.md) · [Changelog](CHANGELOG.md)
- Samples: `sample-xml` (Views and Fragments), `sample-compose` (the AdMob Playground), `sample-mixed` (both, plus
  mediation).

## Policy

CompanyAdMob follows Google's documented requirements as of 2026-09-26. Your app remains responsible for its own
policy configuration and product usage: your AdMob account settings, consent messages, ad placement and content
choices, and your Google Play data safety form.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
