# Configuration

Everything is configured once, in `Application.onCreate`, with `adMobConfig { }` (or the Java builder), and passed to
`CompanyAdMob.initialize`. The configuration is fixed for the life of the process: a second `initialize` with a
different configuration is ignored, with an error log and an `AdEvent.ConfigurationWarning`. Change placements,
formats and intervals at runtime with [remote overrides](REMOTE_CONFIG.md) instead.

## Full reference

Every option, with a typical value. Only `environment` and `placements` are usually needed.

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Configuration.kt#full-dsl -->
```kotlin
fun fullConfig(analytics: AdEventListener): AdMobConfig = adMobConfig {
    environment = AdEnvironment.STAGING
    // Optional: must equal the manifest's com.google.android.gms.ads.APPLICATION_ID when set.
    appId = "ca-app-pub-0000000000000000~0000000000"

    testing {
        testDeviceIds += "33BE2250B43518CCDA7DE426D04EE231" // printed in Logcat by the Google SDK
        useDemoAdUnits = false // STAGING with real ad units: test devices are then required
    }
    privacy {
        ageTreatment = AgeTreatment.UNSPECIFIED
        maxAdContentRating = MaxAdContentRating.T
        restrictedDataProcessing = false
        debugGeography = DebugGeography.EEA // test devices only; must be DISABLED in PRODUCTION
        consentSyncId = null
    }
    frequency {
        minIntervalBetweenFullScreenAds = 30.seconds
        includeRewardedInGlobalInterval = false
    }
    logging { level = LogLevel.DEBUG }
    advanced {
        showTimeout = 5.seconds
        failFast = null // null: throw on invalid config in DEVELOPMENT and STAGING only
        disableSdkCrashReporting = false
    }
    eventListeners += analytics
    remoteConfig = StaticConfigProvider // the default: no remote overrides

    placements {
        banner(BannerPlacement("home_banner"), "ca-app-pub-0000000000000000/0000000001")
        interstitial(InterstitialPlacement("level_complete"), "ca-app-pub-0000000000000000/0000000002") {
            frequency {
                minInterval = 60.seconds
                maxPerSession = 10
            }
            preload = PreloadStrategy.AFTER_READY
        }
        rewarded(RewardedPlacement("extra_life"), "ca-app-pub-0000000000000000/0000000003")
        rewardedInterstitial(
            RewardedInterstitialPlacement("bonus_round"),
            "ca-app-pub-0000000000000000/0000000004",
        )
        appOpen(AppOpenPlacement("app_open"), "ca-app-pub-0000000000000000/0000000005") {
            cooldown = 4.minutes
            minLaunchesBeforeFirstShow = 2
            showOnColdStartAllowed = true
            suppressAfterFullScreenAd = 5.seconds
            excludeActivities(ConfigSplashActivity::class.java)
            expiry = 4.hours
        }
        native(NativePlacement("feed_native"), "ca-app-pub-0000000000000000/0000000006") {
            poolSize = 2
            enabled = true
        }
    }
}
```

| Option | Default | Meaning |
|---|---|---|
| `environment` | `DEVELOPMENT` | See [Environments](#environments). |
| `appId` | none | Optional cross-check: must equal the manifest's app ID. |
| `testing.testDeviceIds` | empty | Devices that get test ads for real ad units, in DEVELOPMENT and STAGING. Emulators are always test devices ([Google](https://developers.google.com/admob/android/next-gen/test-ads)). |
| `testing.useDemoAdUnits` | environment default | STAGING only: true (default) uses Google's demo ad units; false uses your real ones. |
| `privacy.ageTreatment` | `UNSPECIFIED` | `CHILD`, `TEEN` or `UNSPECIFIED`. See [Consent](CONSENT.md#age-treatment-and-content-rating). |
| `privacy.maxAdContentRating` | not set | `G`, `PG`, `T` or `MA`. |
| `privacy.restrictedDataProcessing` | false | Restricted data processing for US state privacy laws. |
| `privacy.debugGeography` | `DISABLED` | Forces UMP's geography on test devices. Must be `DISABLED` in PRODUCTION. |
| `privacy.consentSyncId` | none | Syncs consent choices across your apps for the same user. |
| `frequency.minIntervalBetweenFullScreenAds` | 30 s | Between any two interstitial, rewarded interstitial and app open ads. |
| `frequency.includeRewardedInGlobalInterval` | false | Whether rewarded ads (which users ask for) also respect it. |
| `logging.level` | DEBUG, or WARN in PRODUCTION | Minimum level logged. |
| `logging.logger` | Logcat | Your own `AdLogger`, for example to forward to Timber or a crash reporter. |
| `advanced.showTimeout` | 5 s | How long a show may wait for the SDK to report that the ad appeared. |
| `advanced.failFast` | true, or false in PRODUCTION | Whether an invalid configuration throws from `initialize`. |
| `advanced.disableSdkCrashReporting` | false | Asks the Google SDK not to report its own crashes. |
| `advanced.googleRequestCustomizer` | none | Escape hatch to edit Google ad requests; needs `@OptIn(GoogleSdkEscapeHatch::class)`. |
| `eventListeners` | empty | [Analytics](ANALYTICS.md) listeners. |
| `remoteConfig` | `StaticConfigProvider` | [Remote overrides](REMOTE_CONFIG.md). |

### Per-placement options

Every placement takes an optional block:

| Option | Formats | Default |
|---|---|---|
| `enabled` | all | true |
| `preload` | all | `AFTER_READY` (load as soon as ads are ready); `ON_DEMAND` (first `preload()` or show); `NONE` (only on explicit load) |
| `frequency { minInterval; maxPerSession }` | full-screen | no limit |
| `retry` | all | see [Defaults](#defaults) |
| `expiry` | cached formats | see [Defaults](#defaults) |
| `poolSize` | native | 1 (1 to 3) |
| `cooldown`, `minLaunchesBeforeFirstShow`, `showOnColdStartAllowed`, `suppressAfterFullScreenAd`, `excludeActivities(...)` | app open | 4 min, 2, true, 5 s, none |

A banner's size is chosen where you show it (`AdBannerView`'s `company_admob_bannerSize`, or `BannerOptions`), not in
the placement. The placement-level `size` has no effect in 0.1.0.

## Environments

Pick the environment per build type:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Configuration.kt#per-build -->
```kotlin
val environment = when (buildType) {
    "debug" -> AdEnvironment.DEVELOPMENT // Google's demo ad units, whatever is configured
    "staging" -> AdEnvironment.STAGING // demo units by default; real units need test devices
    else -> AdEnvironment.PRODUCTION // real ad units only; demo IDs are rejected
}
```

| Environment | Ad units requested | Test devices | Invalid configuration |
|---|---|---|---|
| `DEVELOPMENT` | **Always Google's demo ad units** for each format, whatever you configure | applied | throws from `initialize` (failFast) |
| `STAGING` | Google's demo ad units by default; your real ones with `testing { useDemoAdUnits = false }`, which then **requires** test devices | applied | throws from `initialize` (failFast) |
| `PRODUCTION` | your real ad units only | ignored, with a warning | the state becomes `Failed(InvalidConfiguration)`, ads stay off, and **the app keeps running** |

### Test-ad safety rules

- Never click live ads while testing: use DEVELOPMENT, or STAGING with test devices.
- Google's demo ad units and sample app ID (`ca-app-pub-3940256099942544`) are rejected in PRODUCTION.
- `useDemoAdUnits = true` and a debug geography are rejected in PRODUCTION.
- A non-PRODUCTION environment in a release (non-debuggable) build logs an error and emits a
  `ConfigurationWarning`: only test ads would be served, so the app would earn nothing.
- PRODUCTION in a debuggable build logs a warning: live ads will be requested.

## Validation

`initialize` validates the configuration and the manifest. **Errors** stop ads (and throw when failFast is on). The
state becomes `Failed(InvalidConfiguration)`, whose `reason` lists every problem. **Warnings** only log and emit
`AdEvent.ConfigurationWarning`.

| Code | Kind | When |
|---|---|---|
| `APP_ID_MISSING` | error | The manifest has no `com.google.android.gms.ads.APPLICATION_ID` meta-data. |
| `APP_ID_MALFORMED` | error | The manifest or `appId` value isn't `ca-app-pub-<16 digits>~<10 digits>`. |
| `APP_ID_MISMATCH` | error | `appId` differs from the manifest's. |
| `AD_UNIT_ID_MALFORMED` | error | An ad unit ID isn't `ca-app-pub-<16 digits>/<10 digits>`. |
| `DUPLICATE_PLACEMENT_KEY` | error | A key is configured twice. |
| `FORMAT_MISMATCH` | error | A placement is configured with another format's builder. |
| `POOL_SIZE_OUT_OF_RANGE` | error | A native `poolSize` outside 1..3. |
| `INVALID_DURATION` | error | A negative interval, cooldown or expiry, or a `showTimeout` that isn't positive. |
| `INVALID_COUNT` | error | A negative `maxPerSession` or `minLaunchesBeforeFirstShow`. |
| `INVALID_RETRY_POLICY` | error | Negative attempts, a multiplier below 1, or jitter outside 0..1. |
| `UNKNOWN_PLACEMENT_OVERRIDE` | ignored override | A remote override names a key that isn't configured. |
| `DEMO_ID_IN_PRODUCTION` | error (PRODUCTION) | A Google demo ad unit or the sample app ID. |
| `DEMO_AD_UNITS_IN_PRODUCTION` | error (PRODUCTION) | `useDemoAdUnits = true`. |
| `DEBUG_GEOGRAPHY_IN_PRODUCTION` | error (PRODUCTION) | `debugGeography` isn't `DISABLED`. |
| `STAGING_REAL_IDS_WITHOUT_TEST_DEVICES` | error (STAGING) | `useDemoAdUnits = false` with no test devices. |
| `TEST_DEVICES_IN_PRODUCTION` | warning | Test devices configured in PRODUCTION (ignored; Google advises removing them). |
| `NON_PRODUCTION_IN_RELEASE_BUILD` | warning (error-level log) | DEVELOPMENT or STAGING in a non-debuggable build. |
| `PRODUCTION_IN_DEBUGGABLE_BUILD` | warning | PRODUCTION in a debuggable build. |
| `NO_PLACEMENTS` | warning | No placement configured. |

Invalid remote overrides are dropped with a log, and never stop ads. See [Remote config](REMOTE_CONFIG.md#validation).

## Defaults

| Format | Retry: first delay, multiplier, max delay, attempts | Expiry |
|---|---|---|
| Interstitial, rewarded, rewarded interstitial | 5 s, ×2, 120 s, 5 | 55 min |
| App open | 10 s, ×2, 300 s, 3 | 3 h 55 min |
| Native | 10 s, ×2, 120 s, 3 | 55 min |
| Banner | 15 s, ×2, 120 s, 4 (only while attached and visible) | none: the SDK refreshes banners |

Every retry has ±20 % jitter, and a no-fill is never retried sooner than 30 seconds. Expiries are a few minutes
shorter than Google's limits, which are one hour for most formats and four hours for app open
([app open guidance](https://developers.google.com/admob/android/next-gen/app-open)).

| Frequency | Default |
|---|---|
| Between any two full-screen ads | 30 s |
| Per placement | no limit |
| App open cooldown | 4 min |
| App open first show | after the 2nd launch |
| App open after another full-screen ad | not within 5 s |

## Designing placements

- One placement per distinct place in the app (`level_complete`, `home_banner`), not per screen class or ad unit.
  Placements are how remote config, frequency limits and analytics identify ads.
- Keys are lowercase letters, digits and `_`, at most 40 characters. They're stable identifiers: don't rename them in
  a release that remote config or dashboards already use.
- Several placements may share an ad unit ID; each one keeps its own cache and limits.
- Declare placements in one Kotlin `object` and refer to them everywhere, including from XML by key.

## Java

<!-- snippet: docs-snippets/src/main/java/com/company/admob/docs/JavaConfiguration.java#java-builder -->
```java
static AdMobConfig create() {
    AdMobConfig.Builder builder = new AdMobConfig.Builder()
            .environment(AdEnvironment.PRODUCTION)
            .addBanner(new BannerPlacement("home_banner"), "ca-app-pub-0000000000000000/0000000001")
            .addInterstitial(new InterstitialPlacement("level_complete"), "ca-app-pub-0000000000000000/0000000002");
    builder.getPrivacy().setAgeTreatment(AgeTreatment.UNSPECIFIED);
    builder.getLogging().setLevel(LogLevel.WARN);
    return builder.build();
}
```

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
