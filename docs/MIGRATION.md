# Migration

## From hand-written legacy AdMob code

Apps that use the legacy Google Mobile Ads SDK (`com.google.android.gms:play-services-ads`) directly move to
CompanyAdMob in four steps.

### 1. Replace the dependency

Remove `com.google.android.gms:play-services-ads` (and `-lite`) from your build and add CompanyAdMob
([Getting started](GETTING_STARTED.md#2-add-the-dependencies)). CompanyAdMob uses the Next-Gen SDK, which can't be on
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

| Legacy code | CompanyAdMob |
|---|---|
| `MobileAds.initialize(context)` | `CompanyAdMob.initialize(application, config)` in `Application.onCreate`, then `CompanyAdMob.start(activity)` |
| UMP `ConsentInformation.requestConsentInfoUpdate` + `loadAndShowConsentFormIfRequired` | Done by `start(activity)` |
| UMP `showPrivacyOptionsForm` / `getPrivacyOptionsRequirementStatus` | `privacy.showPrivacyOptions(activity)` / `privacy.isPrivacyOptionsRequired` |
| `RequestConfiguration.Builder().setTestDeviceIds(…)` | `testing { testDeviceIds += … }` |
| `setTagForChildDirectedTreatment` / `setTagForUnderAgeOfConsent` | `privacy { ageTreatment = … }` |
| `setMaxAdContentRating(…)` | `privacy { maxAdContentRating = … }` |
| `AdView` in XML + `adView.loadAd(AdRequest.Builder().build())` | `AdBannerView` with `company_admob_placement` ([XML](XML.md)) |
| `adView.pause()` / `resume()` / `destroy()` in lifecycle callbacks | Automatic: banners follow their lifecycle owner |
| `InterstitialAd.load(…)` + keeping the ad + `show(activity)` + `FullScreenContentCallback` | `CompanyAdMob.showInterstitial(activity, placement) { result -> }`: loading, caching, reloading and callbacks included |
| `RewardedAd.load` + `show(activity) { rewardItem -> }` | `rewarded.show(activity, placement) { RewardResult }` with a `rewardId` |
| `RewardedInterstitialAd` | `rewardedInterstitial.show` after your intro screen |
| `AppOpenAd` + an `AppOpenManager` with `ProcessLifecycleOwner` | Configure an `appOpen` placement; `showOnColdStart` from the loading screen |
| `AdLoader.Builder(…).forNativeAd { }` + `NativeAdView` binding + `nativeAd.destroy()` | `AdNative` / `AdNativeView` / `attachNativeAd` with templates or a `NativeAdLayout` |
| `OnPaidEventListener` | `AdEvent.Paid` ([Analytics](ANALYTICS.md)) |
| `ResponseInfo.getMediationAdapterClassName()` | `AdSourceInfo` in `Loaded` and `Paid` events |
| `MobileAds.openAdInspector(context, listener)` | `diagnostics.openAdInspector(activity)` |

Things you can delete rather than port: retry loops, "is an ad loading" flags, cache timestamps, frequency counters,
`WeakReference<Activity>` holders and "is a full-screen ad showing" booleans.

## Between CompanyAdMob versions

Each release lists breaking changes in the [changelog](../CHANGELOG.md), and a section appears here when a release needs
code changes.

### 0.1.0

The first release: nothing to migrate.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
