# Setup: dependencies, manifest, initialization

## Contents
1. Dependencies (Maven Central, or JitPack for unreleased fixes)
2. SDK and toolchain requirements
3. Manifest: the AdMob app ID
4. Firebase project
5. The Application class
6. Environments
7. Import map

## 1. Dependencies

**Default: Maven Central.** Needs no token; `settings.gradle.kts` only has to list `google()` and `mavenCentral()`.

```toml
# gradle/libs.versions.toml
[versions]
adroit = "1.0.0-rc6"
googleServices = "4.5.0"

[libraries]
adroit-compose  = { module = "io.github.thesobiaakbar:adroit-compose",  version.ref = "adroit" } # Compose (includes views + core)
adroit-views    = { module = "io.github.thesobiaakbar:adroit-views",    version.ref = "adroit" } # XML only (includes core)
adroit-firebase = { module = "io.github.thesobiaakbar:adroit-firebase", version.ref = "adroit" } # Remote Config + Analytics
adroit-update   = { module = "io.github.thesobiaakbar:adroit-update",   version.ref = "adroit" } # Play in-app update
adroit-testing  = { module = "io.github.thesobiaakbar:adroit-testing",  version.ref = "adroit" } # testImplementation only

[plugins]
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

| Module | Add when |
|---|---|
| `adroit-compose` | Any Compose screen. Brings `AdSlot`, `AdBanner`, `AdNative`, `rememberNativeAd`, `rememberAdAvailability` and `rememberFullScreenAdLauncher`, plus everything in views and core. |
| `adroit-views` | XML only, with no Compose. Brings `AdSlotView`, `AdBannerView`, `AdNativeView`, `attachAd`, `attachBanner`, `attachNativeAd`, `NativeAdHolder` and `NativeTemplates`. |
| `adroit-firebase` | Firebase in the app. Every ad is then controlled from Remote Config, events go to Analytics, and the app gets `RemoteValue` for its own switches. It brings `adroit-remote-config`, which does one fetch per session for everything. |
| `adroit-update` | Google Play's in-app update, driven by the Firebase `AppUpdate` parameter. |
| `adroit-testing` | `FakeAdsClient`, `AdroitTestRule`. Test scope only. |

Don't add `play-services-ads`, `ads-mobile-sdk` or `user-messaging-platform` yourself: the Adroit artifacts bring
the versions they were tested with. Apply the `google-services` plugin in `:app`, and put `google-services.json` in
`app/`.

**Firebase:**
- `adroit-firebase` exposes `firebase-config` and `firebase-analytics` through its own Firebase BoM, so
  `FirebaseRemoteConfig` and `remoteConfigSettings` compile without any other dependency.
- If the app already declares a Firebase BoM and Firebase libraries, keep them. Gradle resolves to the higher BoM.
  Don't remove the app's own Firebase dependencies (Crashlytics, Messaging and so on).
- When the app already uses Remote Config for other keys, Adroit shares the same `FirebaseRemoteConfig` instance and
  the same fetch.

**XML apps also need AndroidX:**
- `androidx.activity:activity-ktx`, for `by viewModels()`.
- `androidx.lifecycle:lifecycle-runtime-ktx`, for `lifecycleScope` / `repeatOnLifecycle`.
- `androidx.lifecycle:lifecycle-viewmodel-ktx`, for `viewModelScope`.

`adroit-views` uses the lifecycle ones internally but doesn't expose them, and AppCompat brings `activity`. Add
whichever ones the project doesn't already have, at the versions it already uses.

**Version: what Maven Central's `1.0.0-rc6` lacks.** These fixes are on the Adroit master branch but in no release
tag yet:
- `2577f26` / `51d7c2f`: the splash timer pauses in the background, and the splash ad is requested only once the
  splash is in front. Android 15+ blocks a background app's network.
- `c6c4298` / `af73065`: an expanded collapsible banner stays on its own screen, instead of covering the next one.
- `b0df3c8`: an ad-free user sees no banner shimmer.

The public API is identical. Until the next release ships, take a commit at `b0df3c8` or later from JitPack (below),
as PhotoRecovery does, for any app with a splash ad, a collapsible banner or premium users. In practice that is most
apps.

**Alternative: JitPack, pinned to a commit.** Use this when the app needs a fix that is on the Adroit master branch
but not yet released (PhotoRecovery does this):

```kotlin
// settings.gradle.kts, dependencyResolutionManagement.repositories: only Adroit comes from JitPack
maven("https://jitpack.io") { content { includeGroup("com.github.thesobiaakbar.adroit") } }
```
```toml
adroit = "b0df3c8fda"   # a commit of github.com/TheSobiaAkbar/Adroit that contains jitpack.yml (d0f86f3 or later)
adroit-compose = { group = "com.github.thesobiaakbar.adroit", name = "adroit-compose", version.ref = "adroit" }
# same group for every other adroit-* artifact
```
- To take an Adroit change: push it to GitHub, then put its commit hash here. The first build of a new commit is slow,
  because JitPack builds it then.
- Release tags up to `v1.0.0-rc6` don't contain `jitpack.yml`, so use a commit hash, not a tag.
- The Kotlin package is `io.github.thesobiaakbar.adroit.*` whichever way you install it; only the Maven group differs.

## 2. Requirements

| | |
|---|---|
| minSdk | 24 |
| Kotlin | 2.1 or newer |
| compileSdk | 34 or newer; **37** with `adroit-compose` (Compose 1.12) |
| AGP | 8.1.1 or newer; **9.1** or newer with `adroit-compose` |
| Java bytecode | 11 |

With `adroit-compose` on AGP 9, you don't apply `org.jetbrains.kotlin.android`: AGP 9 has Kotlin built in. Keep the
project's own plugin setup.

## 3. Manifest

The ads SDK and UMP both read the app ID from the manifest. Without it, `start()` fails with `APP_ID_MISSING`.

```xml
<application android:name=".MyApp" ...>
    <activity android:name=".SplashActivity" android:exported="true"
        android:theme="@style/Theme.MyApp.Splash">          <!-- the launcher -->
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
    <activity android:name=".MainActivity" />                 <!-- no launcher intent-filter -->

    <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="${admobAppId}" />
</application>
```
```kotlin
// app/build.gradle.kts: one app ID per build type, so debug can use Google's sample ID
android {
    defaultConfig { manifestPlaceholders["admobAppId"] = "ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" } // the real one
    buildTypes { debug { manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713" } } // Google's sample
    buildFeatures { buildConfig = true } // for BuildConfig.DEBUG
}
```
- The ID has the format `ca-app-pub-<16 digits>~<10 digits>`.
- PRODUCTION rejects Google's sample ID.
- Debug can use the real ID too: DEVELOPMENT still serves only test ads.
- The libraries declare `INTERNET` and `ACCESS_NETWORK_STATE` themselves.

## 4. Firebase

1. Create the Firebase project, with Analytics on.
2. Add an Android app with the **exact** `applicationId`, and download `google-services.json` into `app/`.
3. If debug has an `applicationIdSuffix`, register that package too, and re-download: one file holds both. A
   debug-only project is also possible: put its file at `app/src/debug/google-services.json`.
4. Skip the console's "Add Firebase SDK" step: Adroit brings the SDKs.

No fetch code is needed. The first Adroit Firebase module starts one fetch per launch, in the background, for ads and
billing together, and nothing waits for it.

## 5. The Application class

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Optional: in debug, a console change is visible on the next launch.
        FirebaseRemoteConfig.getInstance().setConfigSettingsAsync(remoteConfigSettings {
            if (BuildConfig.DEBUG) minimumFetchIntervalInSeconds = 0
            fetchTimeoutInSeconds = 10
        })
        Adroit.initialize(this, adMobConfig {
            environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
            profiles(AppAds.entries, AppAds.settings)
            remoteConfig = FirebaseRemoteConfigProvider()      // leave out without adroit-firebase
            logging { level = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN }
        })
        // Ad-free before anything asks for an ad. With adroit-billing, use its bridge instead (adroit-billing skill).
        // Adroit.setAdFree(savedPremiumFlag)
    }
}
```
- `initialize` is synchronous and offline. Consent and the SDK start later, from the splash (`SplashFlow`, or
  `Adroit.start(activity)`). A second call is ignored and logged.
- **Inside `adMobConfig { }`, `remoteConfig` is the builder's property.** A local variable named `remoteConfig` shadows
  it, and the ads silently ignore Firebase.
- **Firebase may be optional at runtime.** Some builds have no `google-services.json` for a flavour. In that case,
  check `FirebaseApp.getApps(this).isNotEmpty()` and set `remoteConfig` only when it is true. PhotoRecovery does this.
- **Benchmark and baseline-profile builds** should run with no ads. Add a `buildConfigField("boolean", "AD_FREE", …)`
  that is true only for those build types, and call `Adroit.setAdFree(true)` when it is set.
- Other builder options:
  - `testing { testDeviceIds += "…"; useDemoAdUnits = … }`
  - `privacy { ageTreatment; maxAdContentRating; restrictedDataProcessing; debugGeography; debugBuildGeography }`
  - `frequency { minIntervalBetweenFullScreenAds; maxInterstitialsPerSession; includeRewardedInGlobalInterval }`
  - `advanced { showTimeout; failFast }`
  - `eventListeners += …`
  - With profiles, prefer `AdSettings` over `frequency { }`.

## 6. Environments

| `AdEnvironment` | Ads served | Use |
|---|---|---|
| `DEVELOPMENT` | Google's test units always, whatever is configured. A debuggable build also pretends to be in the EEA, so consent shows everywhere. | debug |
| `STAGING` | Test units, unless `testing { useDemoAdUnits = false }`. Real units then need test device IDs. | QA builds on real units |
| `PRODUCTION` | Real units only. Demo IDs, the sample app ID and debug geography are rejected; an invalid config gives `AdsState.Failed(InvalidConfiguration)`, and the app keeps running with no ads. Each ad with no `ids` logs a warning. | release |

A release build that is not on PRODUCTION logs `NON_PRODUCTION_IN_RELEASE_BUILD`.

## 7. Import map

| Package | Types and functions |
|---|---|
| `io.github.thesobiaakbar.adroit` | `Adroit`, `AdsClient`, `AdSlots`, `AdsState`, `AdError`, `AdAvailability`, `FullScreenResult`, `RewardResult`, `Reward`, `BannerSize`, `CollapsibleBannerPosition`, `BannerOptions`, `BannerState`, `NativeStyle`, `NativeAdLayout`, `NativeAdRenderer`, `NativeAdResult`, `AdCloseButton`, `AdFormat`, placements (`BannerPlacement`, `InterstitialPlacement`, `RewardedPlacement`, `RewardedInterstitialPlacement`, `AppOpenPlacement`, `NativePlacement`), `RewardedShowOptions`, `ConsentState`, `UnavailableReason` |
| `io.github.thesobiaakbar.adroit.config` | `adMobConfig`, `AdMobConfig`, `AdEnvironment`, `AdDefinition`, `AdDefinitions`, `AdProfile`, `AdSettings`, `DebugGeography`, `PreloadStrategy`, the extension functions `interstitialPlacement()`, `rewardedPlacement()`, `rewardedInterstitialPlacement()`, `appOpenPlacement()`, `bannerPlacement()`, `nativePlacement()` |
| `io.github.thesobiaakbar.adroit.config.remote` | `AdConfigProvider`, `AdOverrides`, `PlacementOverride`, `StaticConfigProvider` |
| `io.github.thesobiaakbar.adroit.events` | `AdEvent` (incl. `AdEvent.CloseClicked`), `AdEventListener`, `AdRevenue` |
| `io.github.thesobiaakbar.adroit.logging` | `LogLevel` |
| `io.github.thesobiaakbar.adroit.splash` | `SplashFlow`, `SplashState` |
| `io.github.thesobiaakbar.adroit.compose` | `AdSlot`, `AdBanner`, `AdNative`, `rememberNativeAd`, `rememberAdAvailability`, `rememberFullScreenAdLauncher`, `FullScreenAdLauncher` |
| `io.github.thesobiaakbar.adroit.views` | `AdSlotView`, `AdBannerView`, `AdNativeView`, `AdShimmerView`, `attachAd`, `attachBanner`, `attachNativeAd`, `NativeAdHolder`, `NativeTemplates` |
| `io.github.thesobiaakbar.adroit.firebase` | `FirebaseRemoteConfigProvider`, `FirebaseAnalyticsEventListener`, `FirebaseAnalyticsOptions`, `RemoteValue`, `RemoteConfigSession` |
| `io.github.thesobiaakbar.adroit.update` | `AdroitUpdate`, `UpdateType` |
| `io.github.thesobiaakbar.adroit.testing` | `FakeAdsClient`, `AdroitTestRule`, `FakeCall`, `FakeBannerAdHandle`, `FakeNativeAdHandle` |
