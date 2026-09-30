# Adroit

[![CI](https://github.com/TheSobiaAkbar/Adroit/actions/workflows/ci.yml/badge.svg)](https://github.com/TheSobiaAkbar/Adroit/actions/workflows/ci.yml)
[![Instrumentation](https://github.com/TheSobiaAkbar/Adroit/actions/workflows/instrumentation.yml/badge.svg)](https://github.com/TheSobiaAkbar/Adroit/actions/workflows/instrumentation.yml)

**AdMob ads for Android apps, done right in a few lines of Kotlin.** Created by
[Sobia Akbar](https://github.com/TheSobiaAkbar).

Adroit wraps the [Google Mobile Ads Next-Gen SDK](https://developers.google.com/admob/android/next-gen/quick-start)
and Google's consent SDK (UMP) behind one small API, for XML apps and Jetpack Compose apps alike. You list your ads in
one Kotlin file, and every ad can then be tuned from Firebase without releasing a new version.

## Start here

| Your app | Step-by-step guide |
|---|---|
| **Kotlin + XML (Views, Fragments)** | **[Adding ads to a new Kotlin + XML app](docs/NEW_APP_XML.md)** |
| **Jetpack Compose** | **[Adding ads to a new Jetpack Compose app](docs/NEW_APP_COMPOSE.md)** |

Adroit is private: to add it to your build you need an invite and a GitHub token.
**[Getting access](docs/ACCESS.md)** takes two minutes.

Each guide goes from an empty app to a splash screen with consent, banners, native ads, interstitials, rewarded ads and
app open ads, with complete files you can copy and a check after every step. The `sample-xml` and `sample-compose`
apps in this repository are the finished result of each guide.

## Features

- **Every AdMob format:** banner (adaptive, collapsible, inline), native (five ready-made styles, or your own layout),
  interstitial, rewarded, rewarded interstitial and app open.
- **Consent built in:** Google's consent form (GDPR, US states) shown before any ad, the privacy options entry point,
  and no ad request until consent allows it.
- **A splash screen that never gets stuck:** consent, then a splash ad or app open ad, with a timeout.
- **Ads you control from Firebase:** per ad, change ad unit IDs, swap formats, set counters, timers, frequency limits
  and banner or native styles, or turn it off, without a new release.
- **Lifecycle-safe:** banners and native ads survive rotation, scrolling in lists and switching tabs with no new
  request, and are destroyed with their screen. No leaks (checked with LeakCanary).
- **Reliable full-screen ads:** preloading, retries with backoff, and a callback you can count on, whether the ad
  showed or not. Rewards are granted exactly once.
- **Safe while developing:** debug builds always show Google's test ads, so you can't click your own real ads.
- **Loading states:** shimmer placeholders while an ad loads, and space that collapses when none is available.
- **Events and diagnostics:** every load, show, click and failure as a stream, optional Google Analytics logging, and
  a diagnostics snapshot for debug screens.
- **No Google types in your code:** the Google SDKs are internal, so an SDK upgrade doesn't touch your app.

## Which module do I add?

| Artifact | Add it when |
|---|---|
| `io.github.thesobiaakbar:adroit-views` | Your app uses XML layouts: `AdBannerView`, `AdNativeView`, `AdSlotView`. |
| `io.github.thesobiaakbar:adroit-compose` | Your app uses Jetpack Compose: `AdBanner`, `AdNative`, `AdSlot`, `rememberFullScreenAdLauncher`. |
| `io.github.thesobiaakbar:adroit-firebase` | Optional: each ad controlled from Firebase Remote Config, and ad events sent to Google Analytics. |
| `io.github.thesobiaakbar:adroit-testing` | Unit tests without Google: `FakeAdsClient`, `AdroitTestRule`. Test scope only. |

`adroit-views` and `adroit-compose` both bring `adroit-core`, the engine that does the work. An app with both XML and
Compose screens adds `adroit-compose`, which includes `adroit-views`.

## Quick look

```kotlin
// build.gradle.kts
dependencies { implementation("io.github.thesobiaakbar:adroit-compose:1.0.0-rc2") }

// Application.onCreate
Adroit.initialize(this, adMobConfig {
    environment = AdEnvironment.DEVELOPMENT // Google's test ads while developing
    placements { banner(BannerPlacement("home_banner"), "ca-app-pub-0000000000000000/0000000001") }
})

// Your first Activity's onCreate: consent, then the ads SDK
Adroit.start(this)

// A Compose screen
AdBanner(BannerPlacement("home_banner"))
```

```xml
<!-- An XML layout -->
<io.github.thesobiaakbar.adroit.views.AdBannerView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:adroit_placement="home_banner" />
```

The guides above set this up properly, with every ad in one enum and Firebase control.

## Documentation

**Start here:**
[Getting access](docs/ACCESS.md) · [Getting started](docs/GETTING_STARTED.md) ·
[New XML app](docs/NEW_APP_XML.md) · [New Compose app](docs/NEW_APP_COMPOSE.md) ·
[Integration checklist](docs/INTEGRATION_GUIDE.md)

**Ad formats:**
[Banner](docs/BANNER.md) · [Native](docs/NATIVE.md) · [Interstitial](docs/INTERSTITIAL.md) ·
[Rewarded](docs/REWARDED.md) · [Rewarded interstitial](docs/REWARDED_INTERSTITIAL.md) · [App open](docs/APP_OPEN.md)

**UI toolkits:**
[Jetpack Compose](docs/COMPOSE.md) · [XML and Views](docs/XML.md)

**Configuration and Firebase:**
[Configuration](docs/CONFIGURATION.md) · [Consent](docs/CONSENT.md) · [Splash screen](docs/SPLASH.md) ·
[Ad profiles](docs/AD_PROFILES.md) · [Remote config](docs/REMOTE_CONFIG.md) · [Analytics](docs/ANALYTICS.md) ·
[Mediation](docs/MEDIATION.md)

**Testing and help:**
[Testing](docs/TESTING.md) · [Troubleshooting](docs/TROUBLESHOOTING.md) · [Migration](docs/MIGRATION.md)

**Reference:**
[Architecture](docs/ARCHITECTURE.md) · [Releasing](docs/RELEASE.md) · [Changelog](CHANGELOG.md)

## Supported versions

| | |
|---|---|
| minSdk | 24 |
| Kotlin | 2.1 or newer (tested with 2.1.20 and 2.2.10) |
| Java bytecode | 11 |
| Google Mobile Ads Next-Gen SDK | `ads-mobile-sdk` 1.4.0 (included) |
| UMP | `user-messaging-platform` 4.0.0 (included) |
| compileSdk | 34 or newer; 37 with `adroit-compose` (Compose 1.12 requires it) |
| Android Gradle plugin | 8.1.1 or newer; 9.1.0 or newer with `adroit-compose` (Compose 1.12 requires it) |

Apps using AdMob Mediation must exclude the legacy `play-services-ads`: see [Mediation](docs/MEDIATION.md).

## Policy

Adroit follows Google's documented requirements as of 2026-09-26. Your app remains responsible for its own
policy configuration and product usage: your AdMob account settings, consent messages, ad placement and content
choices, and your Google Play data safety form.

## License

Copyright © 2026 Sobia Akbar. All rights reserved. Adroit is not open source: you may use it only with the author's
written permission, under the terms in [LICENSE](LICENSE). For access, see [Getting access](docs/ACCESS.md).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-30
