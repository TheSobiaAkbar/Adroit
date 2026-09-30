# Part 1: Google Mobile Ads / UMP research

**Research date:** 2026-09-25
**Method:** Every fact below comes from official Google developer documentation or Google's Maven repository, fetched on the research date. Nothing here is from memory. Google changes these SDKs often, so re-check this page before each GMA upgrade (see `RELEASE.md` → "Google SDK upgrade checklist").

> **Policy statement.** Adroit follows Google's documented requirements as of 2026-09-25. The host application is still responsible for its own AdMob account settings, Privacy & messaging configuration, Play Console data-safety disclosures, and how the product uses ads. This library does not guarantee AdMob or Google Play policy compliance.

---

## 1.1 Headline findings

| # | Finding | Source |
|---|---|---|
| F1 | The **legacy Google Mobile Ads SDK** (`com.google.android.gms:play-services-ads`, latest **25.5.0**, 2026-09-17) is marked in the docs as: *"Google Mobile Ads SDK is in maintenance mode. For the latest updates and features, migrate and set up GMA Next-Gen SDK."* | [Legacy quick start](https://developers.google.com/admob/android/quick-start) |
| F2 | The **GMA Next-Gen SDK** (`com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk`) reached **general availability with 1.0.0 on 2026-04-14**. The latest documented version is **1.4.0** (2026-08-20). Google's Maven repo also has **1.5.0**, published 2026-09-24 but not yet in the release notes. | [Next-Gen release notes](https://developers.google.com/admob/android/next-gen/rel-notes), [Next-Gen deprecation](https://developers.google.com/admob/android/next-gen/deprecation), `dl.google.com/android/maven2` metadata |
| F3 | Legacy **v24.x and v25.x are both scheduled for deprecation on 2027-06-30 and sunset on 2028-06-30**. After sunset, *"ad traffic from that version is at risk of receiving automatic no fill."* | [Legacy deprecation](https://developers.google.com/admob/android/deprecation) |
| F4 | Next-Gen **v1.x**: supported. Deprecation is planned for Q1 2028 and sunset for Q2 2029. Google plans one major version each Q1. Majors N and N‑1 are supported, N‑2 is deprecated, and N‑3 is sunset about 2 months after N ships. | [Next-Gen deprecation](https://developers.google.com/admob/android/next-gen/deprecation) |
| F5 | Next-Gen supports **every format** this library needs: banner (anchored/inline adaptive, fixed, collapsible), interstitial, rewarded, rewarded interstitial, app open, and native. It also has picture-in-picture (open beta) and swipeable interstitial (private beta). | [Next-Gen quick start](https://developers.google.com/admob/android/next-gen/quick-start) |
| F6 | **AdMob Mediation works with Next-Gen.** Adapter guides exist for AppLovin, BidMachine, Bigo, Chartboost, DT Exchange, i-mobile, InMobi, ironSource, Liftoff, LINE, maio, Meta, Mintegral, Moloco, myTarget, Pangle, PubMatic, Unity, Vpon, and Zucks. Adapters still depend on the legacy artifact, so the **host app must globally exclude `play-services-ads` and `play-services-ads-lite`**. Next-Gen includes the classes those adapters need. | [Next-Gen migration](https://developers.google.com/admob/android/next-gen/migration), Next-Gen mediation nav |
| F7 | Third-party *mediation platforms* (MAX, LevelPlay, and others) that treat AdMob as a demand source each have their own Next-Gen compatibility status. Google's list: AdMost, AppLovin MAX, Chartboost Mediation, DT FairBid, Gravite, Nimbus, TopOn, TradPlus VisiM, and Unity LevelPlay are compatible. | [Next-Gen migration](https://developers.google.com/admob/android/next-gen/migration) |
| F8 | **UMP SDK latest is 4.0.0** (2025-10-31). It added `setConsentSyncId()` and raised minSdk to 23. Next-Gen 1.5.0 pulls in UMP 4.0.0 transitively. | [UMP release notes](https://developers.google.com/admob/android/privacy/release-notes), ads-mobile-sdk 1.5.0 POM |

## 1.2 Version and platform requirements

| Item | Legacy GMA 25.5.0 | Next-Gen 1.4.0 / 1.5.0 | Source |
|---|---|---|---|
| minSdk | 24 | 24 | quick-start pages |
| compileSdk | 35+ (24.0.0 required 34+) | 35+ per the quick start (the migration page says 34) | quick-start / migration |
| Kotlin | min 2.1.0 since 24.1.0 | min 1.9 | release notes / quick start |
| Java | n/a | Java 11 supported since 0.24.0-beta03 | Next-Gen release notes |
| App ID | Manifest `<meta-data com.google.android.gms.ads.APPLICATION_ID>` | **Programmatic `InitializationConfig.Builder(appId)`.** The UMP SDK **still requires the manifest meta-data**, so the ID lives in two places. | Next-Gen privacy / migration |
| Initialization | Optional but recommended, on a background thread | **Mandatory before any ad call** (otherwise `UninitializedPropertyAccessException`). It **must run on a background thread**, and calling it on the main thread may cause an ANR. The completion listener fires when adapters finish initializing, or after 30 s. | Next-Gen quick start |
| Callback thread | Main thread | **Load and event callbacks run on a background thread**, so UI work must be dispatched to main explicitly | [Handle callbacks](https://developers.google.com/admob/android/next-gen/migration/handle-callbacks) |
| Transitive deps (1.5.0 POM) | n/a | androidx core/activity/browser/webkit/work-runtime/datastore, play-services-ads-identifier/appset/basement/cronet/tasks, UMP 4.0.0, gson, tink, guava-android, okhttp 4.11, cronet, kotlin-stdlib 1.9.20, **kotlinx-coroutines-android 1.9.0** | Maven POM |

**This repository right now:** AGP 9.1.1 (latest 9.4.1), Gradle 9.3.1, compileSdk 36.1, minSdk 24, targetSdk 36, Java 11, and **legacy `play-services-ads` 25.5.0** in `:app`. The AGP 9 built-in Kotlin support is in use: `:app` compiles Kotlin with no `kotlin-android` plugin applied. The latest stable Kotlin is 2.4.20, and the latest Compose BOM is 2026.09.00.

## 1.3 Next-Gen API surface (verified names)

| Area | API (package `com.google.android.libraries.ads.mobile.sdk…`) |
|---|---|
| Init | `MobileAds.initialize(context, InitializationConfig.Builder(appId).setRequestConfiguration(rc).build()) { status -> }`. `InitializationCompleteCallback.onInitializationFailed(AdError)` was added in 1.4.0. `InitializationConfig.Builder.disableSdkCrashReporting()` was added in 1.3.0, and `disableMediationAdapterInitialization()` also exists. |
| Request config | `RequestConfiguration.Builder().setAgeRestrictedTreatment(AgeRestrictedTreatment.{CHILD,TEEN,UNSPECIFIED}).setMaxAdContentRating(MAX_AD_CONTENT_RATING_{G,PG,T,MA}).setTestDeviceIds(list).setPublisherPrivacyPersonalizationState(...)`. **`MobileAds.setRequestConfiguration()` replaces the whole configuration**, so always build it complete. |
| Deprecated (Next-Gen 1.1.0 and legacy 25.3.0) | `setTagForChildDirectedTreatment()` and `setTagForUnderAgeOfConsent()`. Use `setAgeRestrictedTreatment()` instead. If both are set, Google applies the most conservative treatment. |
| Requests | `AdRequest.Builder(adUnitId)`, `.setGoogleExtrasBundle(bundle)`, `.putAdSourceExtrasBundle(AdapterClass::class.java, bundle)`, `BannerAdRequest.Builder(adUnitId, adSize)`, `NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE))` |
| Full-screen load | `InterstitialAd.load(request, AdLoadCallback<InterstitialAd>)`, and the same shape for `RewardedAd`, `RewardedInterstitialAd`, and `AppOpenAd`. The callback gets `onAdLoaded(ad)` and `onAdFailedToLoad(LoadAdError)`. |
| Full-screen show | `ad.show(activity)`. Rewarded: `ad.show(activity) { rewardItem -> amount/type }` (`OnUserEarnedRewardListener`). |
| Full-screen events | `ad.adEventCallback = <Format>AdEventCallback { onAdShowedFullScreenContent, onAdDismissedFullScreenContent, onAdFailedToShowFullScreenContent(FullScreenContentError), onAdImpression, onAdClicked, onAdPaid(AdValue) }` |
| Preloading (**beta**) | `InterstitialAdPreloader`, `RewardedAdPreloader`, `RewardedInterstitialAdPreloader`, `AppOpenAdPreloader`, and `BannerAdPreloader`, each with `.start(preloadId, PreloadConfiguration(request, bufferSize?), PreloadCallback?)`, `.pollAd(id)`, `.isAdAvailable(id)`, `.getNumAdsAvailable(id)`, `.peekAdResponseInfo(id)`, `.destroy(id)`, and `.destroyAll()`. There is an app-wide cap of 6 preloaded ads. **The page is still labelled "Ad preloading (beta)".** |
| Banner | `AdView(activity)` → `adView.loadAd(BannerAdRequest, AdLoadCallback<BannerAd>)`. Size: `AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)`. `BannerAdEventCallback`, `BannerAdRefreshCallback { onAdRefreshed, onAdFailedToRefresh }`. Release with remove-from-parent → `adView.destroy()`. `BannerAd.load()`/`getView()` are **deprecated** (0.22.0). Automatic refresh only happens while the banner is visible. Since 1.3.0, `AdView.loadAd()` pauses automatic retries when refresh is off for the unit. |
| Banner sizes | **Deprecated:** `getCurrentOrientationAnchoredAdaptiveBannerAdSize`, `getPortraitAnchoredAdaptiveBannerAdSize`, `getLandscapeAnchoredAdaptiveBannerAdSize`. **Current:** `getLargeAnchoredAdaptiveBannerAdSize`, `getLargePortrait…`, `getLargeLandscape…`, plus inline adaptive (`getInlineAdaptiveBannerAdSize(width, maxHeight)`, `getCurrentOrientationInlineAdaptiveBannerAdSize`). |
| Native | `NativeAdLoader.load(NativeAdRequest, NativeAdLoaderCallback { onNativeAdLoaded, onAdFailedToLoad, onAdLoadingCompleted })`, with optional `numberOfAds` ≤ 5. **Multi-ad load does not work with mediation.** `NativeAdView.registerNativeAd(nativeAd, mediaView)`. Asset view properties: `headlineView`, `bodyView`, `callToActionView`, `iconView`, `advertiserView`, `priceView`, `starRatingView`, `storeView`. `MediaView.imageScaleType`, `nativeAd.destroy()`. |
| Response info | `ad.getResponseInfo()` / `loadAdError.responseInfo`, `responseId`, `loadedAdSourceResponseInfo {name,id,instanceName,instanceId,adapterClassName}`, `responseExtras` (mediation group / A-B test). `Ad.getAdUnitId()` was added in 1.3.0. |
| Load errors | `LoadAdError.ErrorCode`: `APP_ID_MISSING, CANCELLED, INTERNAL_ERROR, INVALID_AD_RESPONSE, INVALID_REQUEST, NETWORK_ERROR, NOT_FOUND, NO_FILL, REQUEST_ID_MISMATCH, TIMEOUT, AD_RESPONSE_ALREADY_USED` |
| Show errors | `FullScreenContentError.ErrorCode`: `AD_REUSED, APP_NOT_FOREGROUND, H5_SHOW_AD_NOT_LOADED, INTERNAL_ERROR, MEDIATION_SHOW_ERROR` |
| Debug | `MobileAds.openAdInspector(listener)`. Check the exact signature: the reference lists `openAdInspector(listener)`, while the guide shows `(context){}`. There is also `MobileAds.openDebugMenu(activity, adUnitId)`. Since 1.3.0 a manifest flag can disable Ad Inspector, which is always disabled in production anyway. |
| Global | `MobileAds.setUserControlledAppVolume(0f..1f)`, `MobileAds.setUserMutedApp(bool)`, SharedPreferences `gad_has_consent_for_cookies=0` (limited ads) |

## 1.4 Ad lifetime and caching guidance (official)

| Format | Guidance | Source |
|---|---|---|
| App open | *"App open ads expire four hours after loading. Don't show an app open ad that has been loaded for more than four hours."* Don't show one on the very first app start. Show the first app-open ad only after the user has opened the app several times. On cold start, show only from a loading screen, and if the content becomes ready before the ad loads, don't show the ad. | Next-Gen app open / single-load |
| Interstitial / rewarded / rewarded interstitial | Preloaded ads *"expire (typically after one hour)"*. The preloader refreshes them before they expire. | ad-preloading pages |
| Native | *"When precaching ads, clear your cache and reload after one hour."* Cache only what's immediately visible, destroy ads you no longer use, and multi-ad loading doesn't work with mediation. | Next-Gen native |
| Banner | Refresh is controlled in the AdMob UI and only happens while the banner is visible. If refresh is off, issue a new request yourself. | Next-Gen banner |
| Rewarded interstitial | Requires **an intro screen that announces the reward and lets the user opt out**. | Next-Gen quick start |
| Video in banner/native | Hardware acceleration must be enabled for the Activity. | banner/native pages |

## 1.5 Official test IDs (verbatim from Google's Next-Gen test-ads page)

| Format | Demo ad unit ID |
|---|---|
| App ID (sample) | `ca-app-pub-3940256099942544~3347511713` |
| App open | `ca-app-pub-3940256099942544/9257395921` |
| Anchored adaptive banner | `ca-app-pub-3940256099942544/9214589741` |
| Inline adaptive banner | `ca-app-pub-3940256099942544/9214589741` |
| Fixed-size banner | `ca-app-pub-3940256099942544/6300978111` |
| Interstitial | `ca-app-pub-3940256099942544/1033173712` |
| Rewarded | `ca-app-pub-3940256099942544/5224354917` |
| Rewarded interstitial | `ca-app-pub-3940256099942544/5354046379` |
| Native | `ca-app-pub-3940256099942544/2247696110` |
| Native video | `ca-app-pub-3940256099942544/1044960115` |

Additional test facts:
- Emulators are automatically test devices for both GMA and UMP.
- Physical devices can be registered in the AdMob UI or with `RequestConfiguration.Builder.setTestDeviceIds()`. Google says: *"Be sure to remove the code that sets these test device IDs before you release your app."*
- **Mediated ads don't show a "Test Ad" label, and demo ad units only serve Google ads.** To test mediation, use your real units on registered test devices, and enable each network's own test mode.

## 1.6 Privacy and consent (UMP 4.0.0)

Official flow (Next-Gen privacy page):

1. `UserMessagingPlatform.getConsentInformation(context)`
2. `consentInformation.requestConsentInfoUpdate(activity, ConsentRequestParameters, onSuccess, onFailure)`. Call it **on every app launch**.
3. `UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError -> }`. The callback fires immediately if no form is required.
4. `consentInformation.canRequestAds()`. Check it in **two places**: right after `requestConsentInfoUpdate()` (consent may already exist from a previous session) and after the form completes. *"Ensure your logic prevents redundant ad requests"* (use an atomic flag).
5. On a consent error, **still check `canRequestAds()`**, because UMP falls back to the previous session's consent.
6. `canRequestAds()` **always returns false until `requestConsentInfoUpdate()` has been called**. Since UMP 3.0.0 it returns true when the app has no privacy messages configured.
7. Don't cache consent status yourself. Google warns that app-side caches *"might return outdated user consent data"*.
8. Privacy options entry point: `consentInformation.privacyOptionsRequirementStatus == REQUIRED` → show a visible, interactive UI element → `UserMessagingPlatform.showPrivacyOptionsForm(activity, listener)`.
9. **Initialize the Mobile Ads SDK only after consent handling.** Next-Gen: *"Ads may be preloaded by GMA Next-Gen SDK or mediation partner SDKs upon initialization… ensure you do so before initializing."*
10. Testing: `ConsentDebugSettings.Builder(ctx).setDebugGeography(DEBUG_GEOGRAPHY_EEA | …REGULATED_US_STATE | …OTHER).addTestDeviceHashedId(id)`. `DEBUG_GEOGRAPHY_NOT_EEA` is deprecated; use `DEBUG_GEOGRAPHY_OTHER`. `consentInformation.reset()` is **for testing only**.
11. UMP 4.0.0 adds `ConsentRequestParameters.Builder.setConsentSyncId()` for syncing consent across apps.

Related:
- **Google Consent Mode:** UMP 3.2.0+ calls Firebase consent APIs automatically when consent mode is turned on in the AdMob UI. Initialize Firebase before starting UMP. The library doesn't need Firebase code for this. ([consent mode](https://developers.google.com/admob/android/next-gen/privacy/consent-mode))
- **US states:** there are two options. RDP is SharedPreferences `gad_rdp = 1`, and GPP is written by UMP when US-states messaging is configured. Whether to use RDP is the publisher's decision. ([US states](https://developers.google.com/admob/android/next-gen/privacy/us-states))
- **Children/teens:** use `setAgeRestrictedTreatment(CHILD|TEEN|UNSPECIFIED)`. Apps in Designed for Families (primarily child-directed) and Family Link users are treated as child-directed automatically. Mediation adapters forward age treatment starting at specific adapter versions (see the targeting page table).
- **Max ad content rating:** `G / PG / T / MA`.
- **Play data disclosure:** The host app has to declare the data the SDK collects in its Play Console data-safety form. See [Next-Gen Play data disclosure](https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure). Apps that must not collect the advertising ID, such as child-directed apps, also need to review the `AD_ID` permission merged in through `play-services-ads-identifier`.

## 1.7 Recent changes that affect this design

- **Legacy 25.0.0:** removed several deprecated APIs and `NativeAdViewHolder`, moved to UMP 4.0.0, and deprecated the old anchored adaptive size APIs in favour of the "large" variants.
- **Legacy 25.3.0 and Next-Gen 1.1.0:** added `setAgeRestrictedTreatment`, deprecating TFCD and TFUA.
- **Legacy 24.4.0:** introduced a new preloading API that replaced the older one.
- **Next-Gen 0.22.0:** introduced the `AdView` banner API and deprecated `BannerAd.load/getView`.
- **Next-Gen 0.24.0:** added large anchored adaptive banners and Java 11 support. It also added the `force_use_cronet` extra, recommended for projects that use **OkHttp 5**, because the SDK depends on OkHttp 4.
- **Next-Gen 1.0.0:** removed `AdPreloader.numAdsAvailable()`; use `getNumAdsAvailable()` instead.
- **Next-Gen 1.3.0:** added `Ad.getAdUnitId()`, `disableSdkCrashReporting()`, and the manifest flag that disables Ad Inspector.
- **Next-Gen 1.4.0:** added the `onInitializationFailed(AdError)` callback.

## 1.8 Decision: which SDK

**Decision: build Adroit on the GMA Next-Gen SDK (`ads-mobile-sdk` 1.x) only. Don't ship a legacy adapter in v1.**

Why:
1. **Timeline (F1, F3, F4).** Legacy is in maintenance mode. A library built on it today would need a forced migration before 2027-06-30, and every app would have to be on the new version before 2028-06-30. Next-Gen v1 is supported into 2028 and has a predictable yearly major-version cadence.
2. **It's GA and complete (F2, F5, F6).** Next-Gen is GA, covers every required format, and supports AdMob Mediation.
3. **The adapter layer contains the risk.** All Google types stay behind `internal` adapter classes. If a specific app ever needs the legacy SDK, a `LegacyGoogleAdsSdk` adapter can be added without touching the public API.
4. **The migration is the expensive part.** Doing it once, inside the library, before any app adopts it is far cheaper than migrating N apps later. That is the purpose of this library.

Accepted risks, which each need an explicit mitigation:

| Risk | Mitigation |
|---|---|
| A host app, or another SDK in it, already pulls in `play-services-ads`, causing duplicate classes | `MEDIATION.md` and `TROUBLESHOOTING.md` document the required global exclusion. |
| A mediation platform the app uses isn't Next-Gen compatible (F7) | Check before adopting. The adapter seam allows a legacy adapter later. Record this as an open question for the business. |
| Callbacks arrive on background threads | The adapter marshals **every** callback to the main thread before it reaches engine code (see Architecture §Concurrency). |
| The SDK's own preloader is still beta | The library uses the **single-load APIs with its own cache**. The Google preloader is noted as a future optimisation behind the same internal interface. |
| App ID lives in two places | The library reads the manifest meta-data, which UMP requires anyway, and uses it as the single source of truth. If `AdMobConfig` also sets it, the two must match. |
| Next-Gen 1.5.0 isn't in the release notes yet | Pin **1.4.0** (documented) in Prompt 01. Upgrade to 1.5.0+ through the upgrade checklist once Google publishes its notes. |

## 1.9 Source index

- Legacy quick start (maintenance-mode notice): https://developers.google.com/admob/android/quick-start
- Legacy release notes: https://developers.google.com/admob/android/rel-notes
- Legacy deprecation schedule: https://developers.google.com/admob/android/deprecation
- Next-Gen quick start: https://developers.google.com/admob/android/next-gen/quick-start
- Next-Gen release notes: https://developers.google.com/admob/android/next-gen/rel-notes
- Next-Gen deprecation: https://developers.google.com/admob/android/next-gen/deprecation
- Next-Gen migration: https://developers.google.com/admob/android/next-gen/migration (and sub-pages `handle-callbacks`, `migrate-ad-requests`, `migrate-*`)
- Next-Gen formats: `/next-gen/banner`, `/next-gen/interstitial`, `/next-gen/interstitial/single-load`, `/next-gen/rewarded`, `/next-gen/rewarded/single-load`, `/next-gen/rewarded-interstitial`, `/next-gen/app-open`, `/next-gen/app-open/single-load`, `/next-gen/native`, `/next-gen/native/advanced`, `/next-gen/native/options`
- Next-Gen privacy: `/next-gen/privacy`, `/next-gen/privacy/us-states`, `/next-gen/privacy/consent-mode`, `/next-gen/privacy/gdpr`, `/next-gen/privacy/play-data-disclosure`
- Next-Gen targeting: `/next-gen/targeting`; test ads: `/next-gen/test-ads`; Ad Inspector: `/next-gen/ad-inspector/*`
- Next-Gen revenue: `/next-gen/impression-level-ad-revenue`; response info: `/next-gen/response-info`; load errors: `/next-gen/ad-load-errors`
- Next-Gen API reference: `/next-gen/reference/kotlin/com/google/android/libraries/ads/mobile/sdk/...`
- UMP release notes: https://developers.google.com/admob/android/privacy/release-notes
- Maven metadata: `https://dl.google.com/android/maven2/com/google/android/libraries/ads/mobile/sdk/ads-mobile-sdk/maven-metadata.xml`

(All URLs are under `https://developers.google.com/admob/android` unless given in full.)
