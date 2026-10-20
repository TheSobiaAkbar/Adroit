# Migration

## From CompanyAdMob to Adroit

The library was renamed. Nothing else changed: replace the names below, then rebuild.

| Before | After |
|---|---|
| `com.company:admob-core` (and `-views`, `-compose`, `-testing`, `-firebase`) | `io.github.thesobiaakbar:adroit-core` (and `-views`, `-compose`, `-testing`, `-firebase`) |
| `import com.company.admob.…` | `import io.github.thesobiaakbar.adroit.…` |
| `CompanyAdMob.initialize(…)`, `CompanyAdMob.start(…)`, `CompanyAdMob.client` | `Adroit.initialize(…)`, `Adroit.start(…)`, `Adroit.client` |
| `CompanyAdMobTestRule`, `CompanyAdMobVersion` | `AdroitTestRule`, `AdroitVersion` |
| XML attributes `app:company_admob_placement`, `app:company_admob_template`, … | `app:adroit_placement`, `app:adroit_template`, … |
| Resource overrides `@color/company_admob_…`, `@dimen/company_admob_…`, `@style/…` | `@color/adroit_…`, `@dimen/adroit_…` |
| The repository `maven.pkg.github.com/TheSobiaAkbar/AdsPlugin` | `maven.pkg.github.com/TheSobiaAkbar/Adroit` (from 1.0.0-rc5: Maven Central, no credentials) |

A find-and-replace of `com.company.admob` → `io.github.thesobiaakbar.adroit`, `CompanyAdMob` → `Adroit` and
`company_admob_` → `adroit_` covers it. Firebase Remote Config parameters keep their names.

## From hand-written legacy AdMob code

Apps that use the legacy Google Mobile Ads SDK (`com.google.android.gms:play-services-ads`) directly move to
Adroit in four steps.

### 1. Replace the dependency

Remove `com.google.android.gms:play-services-ads` (and `-lite`) from your build and add Adroit
([Getting started](GETTING_STARTED.md#2-add-the-dependencies)). Adroit uses the Next-Gen SDK, which can't be on
the same classpath as the legacy one. If a dependency still brings the legacy SDK (a mediation adapter), exclude it
([Mediation](MEDIATION.md)).

### 2. Keep the app ID in the manifest

Keep your `com.google.android.gms.ads.APPLICATION_ID` meta-data: UMP and the Next-Gen SDK both need it. You don't pass
it anywhere else; the library reads it from the manifest (`appId` in the configuration is only an optional
cross-check).

### 3. Move ad unit IDs into the configuration

Every hard-coded ad unit ID becomes a placement in `adMobConfig { placements { … } }` in `Application.onCreate`
([Configuration](CONFIGURATION.md)). Remove your own test-ID switches: environments handle them.

### 4. Replace each call

| Legacy code | Adroit |
|---|---|
| `MobileAds.initialize(context)` | `Adroit.initialize(application, config)` in `Application.onCreate`, then `Adroit.start(activity)` |
| UMP `ConsentInformation.requestConsentInfoUpdate` + `loadAndShowConsentFormIfRequired` | Done by `start(activity)` |
| UMP `showPrivacyOptionsForm` / `getPrivacyOptionsRequirementStatus` | `privacy.showPrivacyOptions(activity)` / `privacy.isPrivacyOptionsRequired` |
| `RequestConfiguration.Builder().setTestDeviceIds(…)` | `testing { testDeviceIds += … }` |
| `setTagForChildDirectedTreatment` / `setTagForUnderAgeOfConsent` | `privacy { ageTreatment = … }` |
| `setMaxAdContentRating(…)` | `privacy { maxAdContentRating = … }` |
| `AdView` in XML + `adView.loadAd(AdRequest.Builder().build())` | `AdBannerView` with `adroit_placement` ([XML](XML.md)) |
| `adView.pause()` / `resume()` / `destroy()` in lifecycle callbacks | Automatic: banners follow their lifecycle owner |
| `InterstitialAd.load(…)` + keeping the ad + `show(activity)` + `FullScreenContentCallback` | `Adroit.showInterstitial(activity, placement) { result -> }`: loading, caching, reloading and callbacks included |
| `RewardedAd.load` + `show(activity) { rewardItem -> }` | `rewarded.show(activity, placement) { RewardResult }` with a `rewardId` |
| `RewardedInterstitialAd` | `rewardedInterstitial.show` after your intro screen |
| `AppOpenAd` + an `AppOpenManager` with `ProcessLifecycleOwner` | Configure an `appOpen` placement; `showOnColdStart` from the loading screen |
| `AdLoader.Builder(…).forNativeAd { }` + `NativeAdView` binding + `nativeAd.destroy()` | `AdNative` / `AdNativeView` / `attachNativeAd` with templates or a `NativeAdLayout` |
| `OnPaidEventListener` | `AdEvent.Paid` ([Analytics](ANALYTICS.md)) |
| `ResponseInfo.getMediationAdapterClassName()` | `AdSourceInfo` in `Loaded` and `Paid` events |
| `MobileAds.openAdInspector(context, listener)` | `diagnostics.openAdInspector(activity)` |

Things you can delete rather than port: retry loops, "is an ad loading" flags, cache timestamps, frequency counters,
`WeakReference<Activity>` holders and "is a full-screen ad showing" booleans.

## Between Adroit versions

Each release lists breaking changes in the [changelog](../CHANGELOG.md), and a section appears here when a release needs
code changes.

### 0.1.0

The first release: nothing to migrate.

## Billing

The billing modules (`adroit-billing`, `-views`, `-compose`, `-firebase` and `-testing`) follow the same API policy
as the ads modules (ADR-007):

- Their public API is recorded in `.api` dumps and checked on every build (`apiCheck`), so no change to it is
  accidental.
- Within 1.x, they stay **binary compatible**: an app compiled against 1.0 runs with any later 1.x without
  recompiling. Breaking changes wait for 2.0, and each one gets a section here.
- API marked `@ExperimentalAdroitBillingApi` is the exception: it may change in a minor version, and using it needs
  `@OptIn(ExperimentalAdroitBillingApi::class)`.
- New enum values (for example a new `BillingError` or `SkipReason`) can appear in minor versions where the type says
  so: keep an `else` branch when matching them.
- The Firebase JSON follows the same rule: new fields can appear, existing ones keep their meaning, and unknown fields
  are ignored, so an older app version reads a newer value safely.

Billing has no earlier version to migrate from. Apps that wrote their own Play Billing code move to it by following
[Adding billing to a Kotlin + XML app](BILLING_NEW_APP_XML.md) or [to a Compose app](BILLING_NEW_APP_COMPOSE.md),
and deleting their purchase handling, acknowledgement and "is premium" storage.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, Play Billing Library 9.1.0, 2026-10-20
