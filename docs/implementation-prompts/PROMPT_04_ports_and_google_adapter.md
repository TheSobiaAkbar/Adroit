# PROMPT 04: Internal ports and the Google Next-Gen adapter

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §6, §9, §12, §22, §24, §33 and `01_SDK_RESEARCH.md` §1.2–1.4, §1.6.

## 1. Objective
Define the internal port interfaces the engines depend on, give them fake implementations for tests, and implement the production adapter over the **GMA Next-Gen SDK** and **UMP**. This is the **only** phase where `com.google.android.libraries.ads.mobile.sdk.*` and `com.google.android.ump.*` imports are introduced, and they stay confined to `com.company.admob.internal.google`.

## 2. Context
- Next-Gen facts (verified 2026-09-25; **re-verify against the reference docs**):
  - `MobileAds.initialize(context, InitializationConfig.Builder(appId).setRequestConfiguration(rc).build(), InitializationCompleteCallback)` **must run on a background thread**, and it's mandatory before any ad call.
  - **Load and event callbacks arrive on background threads.**
  - Loads: `InterstitialAd.load(AdRequest, AdLoadCallback<InterstitialAd>)`, the same shape for `RewardedAd`, `RewardedInterstitialAd`, and `AppOpenAd`. `NativeAdLoader.load(NativeAdRequest, NativeAdLoaderCallback)`. Banner: `AdView(activity).loadAd(BannerAdRequest, AdLoadCallback<BannerAd>)`.
  - Events: `adEventCallback` with `onAdShowedFullScreenContent`, `onAdDismissedFullScreenContent`, `onAdFailedToShowFullScreenContent(FullScreenContentError)`, `onAdImpression`, `onAdClicked`, `onAdPaid(AdValue)`. Rewarded: `show(activity, OnUserEarnedRewardListener)`.
  - Errors: `LoadAdError.ErrorCode` {APP_ID_MISSING, CANCELLED, INTERNAL_ERROR, INVALID_AD_RESPONSE, INVALID_REQUEST, NETWORK_ERROR, NOT_FOUND, NO_FILL, REQUEST_ID_MISMATCH, TIMEOUT, AD_RESPONSE_ALREADY_USED}. `FullScreenContentError.ErrorCode` {AD_REUSED, APP_NOT_FOREGROUND, H5_SHOW_AD_NOT_LOADED, INTERNAL_ERROR, MEDIATION_SHOW_ERROR}.
  - `RequestConfiguration.Builder().setAgeRestrictedTreatment(...)`, `.setMaxAdContentRating(...)`, `.setTestDeviceIds(...)`. **Don't use the deprecated `setTagForChildDirectedTreatment`/`setTagForUnderAgeOfConsent`.** `MobileAds.setRequestConfiguration` replaces the whole config.
- UMP 4.0.0: `UserMessagingPlatform.getConsentInformation(ctx)`, `requestConsentInfoUpdate(activity, params, onSuccess, onFailure)`, `UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, listener)`, `canRequestAds()`, `privacyOptionsRequirementStatus`, `UserMessagingPlatform.showPrivacyOptionsForm(activity, listener)`, `reset()`, `ConsentDebugSettings`, and `ConsentRequestParameters.Builder().setConsentSyncId()`.

## 3. Files to create
Ports (`admob-core/src/main/kotlin/com/company/admob/internal/port/`):
- `AdsSdk.kt`:
  ```kotlin
  internal interface AdsSdk {
    val sdkVersion: String?
    fun initialize(appContext: Context, appId: String, request: SdkRequestConfig, disableCrashReporting: Boolean,
                   onComplete: (SdkInitResult) -> Unit)                     // onComplete on MAIN
    fun updateRequestConfig(request: SdkRequestConfig)
    fun loadFullScreen(format: AdFormat, adUnitId: String, extras: RequestExtras,
                       onResult: (SdkLoadResult<LoadedFullScreenAd>) -> Unit)  // MAIN
    fun loadNative(adUnitId: String, extras: RequestExtras, onResult: (SdkLoadResult<LoadedNativeAd>) -> Unit)
    fun createBannerView(activity: Activity): SdkBannerView
    fun openAdInspector(activity: Activity, onClosed: (AdError?) -> Unit)
  }
  internal data class SdkRequestConfig(val testDeviceIds: List<String>, val ageTreatment: AgeTreatment,
                                       val maxAdContentRating: MaxAdContentRating?)
  internal data class RequestExtras(val placement: AdPlacement, val customizer: GoogleRequestCustomizer?)
  internal sealed interface SdkLoadResult<out T> { data class Success<T>(val ad: T, val source: AdSourceInfo?): SdkLoadResult<T>
                                                    data class Failure(val error: AdError, val retryable: Boolean): SdkLoadResult<Nothing> }
  internal interface LoadedFullScreenAd {
    val format: AdFormat; val source: AdSourceInfo?
    fun setServerSideVerification(customData: String?)          // rewarded formats only; no-op otherwise
    fun show(activity: Activity, listener: FullScreenAdListener) // MAIN; listener callbacks delivered on MAIN
    fun release()                                               // drop references
  }
  internal interface FullScreenAdListener { fun onShown(); fun onImpression(); fun onClicked(); fun onPaid(r: AdRevenue)
    fun onRewardEarned(reward: Reward); fun onDismissed(); fun onFailedToShow(error: AdError) }
  internal interface LoadedNativeAd { val assets: NativeAdAssets; val source: AdSourceInfo?
    fun setListener(l: NativeAdListener); fun destroy() ; fun bindInternal(): Any /* the Google NativeAd, used only by the internal binder */ }
  internal interface SdkBannerView { val view: View; fun load(adUnitId: String, size: BannerSize, widthDp: Int, extras: RequestExtras, listener: BannerListener)
    fun expectedHeightPx(size: BannerSize, widthDp: Int): Int; fun destroy() }
  ```
  (Adjust names as needed, but keep the shape: Google-free parameters, main-thread callbacks.)
- `ConsentSdk.kt`: `requestUpdate(activity, ConsentParams, onDone(result: ConsentUpdateResult))`, `loadAndShowFormIfRequired(activity, onDone(AdError?))`, `canRequestAds(): Boolean`, `consentStatus(): ConsentStatus`, `privacyOptionsRequired(): Boolean`, `showPrivacyOptionsForm(activity, onDone(AdError?))`, `reset()`. `ConsentParams(debugGeography, testDeviceHashedIds, tagUnderAgeOfConsent: Boolean, consentSyncId: String?)`.
- `ActivityTracker.kt`, `AppForegroundSource.kt`, `KeyValueStore.kt` (the interfaces only; implemented in Prompt 06)

Google adapter (`admob-core/src/main/kotlin/com/company/admob/internal/google/`):
- `NextGenAdsSdk.kt`: implements `AdsSdk`. Rules:
  - `initialize` runs `MobileAds.initialize` on `Dispatchers.IO` (`withContext` inside the library scope), wraps it in `runCatching`, handles the `onInitializationFailed` callback if present in the pinned SDK version, and posts the result to main.
  - Every load call is made from `Dispatchers.Default` (verify the reference for thread requirements; if loads must happen on main, follow the reference and report). Every callback is posted to main through `postToMain`.
  - Every ad object's `adEventCallback` is mapped to `FullScreenAdListener` with every call posted to main.
- `UmpConsentSdk.kt`: implements `ConsentSdk`. UMP callbacks are also posted to main (UMP usually calls back on main; post anyway, idempotently).
- `GoogleErrorMapper.kt`: `LoadAdError` → `AdError` + retryable flag (architecture §24 mapping table; unknown codes → `LoadFailed(retryable = true)`), `FullScreenContentError` → `AdError`, and UMP `FormError` → `AdError.Internal`/`ConsentRequired` with `SdkErrorDetails`. **Mapping must use `when` over the enum with an `else` branch** so a new SDK enum value never crashes.
- `GoogleResponseInfoMapper.kt`: `ResponseInfo` → `AdSourceInfo` (responseId, loadedAdSourceResponseInfo name/id/instanceName/instanceId/adapterClassName, responseExtras mediation_group_name / mediation_ab_test_name / mediation_ab_test_variant).
- `GoogleRevenueMapper.kt`: `AdValue` → `AdRevenue` (the precision enum mapping, with an else branch → UNKNOWN).
- `GoogleRequestFactory.kt`: builds `AdRequest`/`NativeAdRequest`/`BannerAdRequest` and applies the escape-hatch customizer inside `runCatching` (a failure logs an error and continues with the uncustomized request).
- `GoogleRequestConfigMapper.kt`: `SdkRequestConfig` → `RequestConfiguration` (AgeTreatment → `AgeRestrictedTreatment`, rating mapping).

Test fakes (`admob-core/src/test/kotlin/com/company/admob/fakes/`):
- `FakeAdsSdk.kt`: scriptable per ad unit (`enqueueLoad(adUnitId, result, delayMs)`), records calls, and `FakeLoadedFullScreenAd` exposes `simulateShown()`, `simulateReward()`, `simulateDismissed()`, `simulateFailedToShow()`, and so on.
- `FakeConsentSdk.kt`: scriptable update result, form result, `canRequestAds`, and privacy-options flag.
- `FakeClock.kt`, `FakeActivityTracker.kt`, `FakeAppForegroundSource.kt`, `InMemoryKeyValueStore.kt`

Put these fakes in the `testFixtures` source set if AGP 9 supports Android test fixtures for libraries (`android.testFixtures.enable = true`), so later modules (views/compose tests) can reuse them. Otherwise use `src/test` and report.

## 4. Files to modify
- `admob-core/consumer-rules.pro`: nothing needed, since the adapter uses no reflection. Keep it as a comment and explain why in the file.

## 5. Implementation requirements
1. **No Google import outside `internal/google/`.** Add a unit test that scans `admob-core/src/main/kotlin` and fails if `import com.google.android.libraries.ads` or `import com.google.android.ump` appears in any file outside `internal/google/`.
2. **Threading contract test**: with `FakeAdsSdk` there's nothing to test, so instead add a Robolectric test for `NextGenAdsSdk`'s posting helper. Invoke a listener wrapper from a background thread and assert the delegate runs on the main looper. Where Google classes can't be constructed in unit tests, test the wrapper classes that take plain lambdas.
3. `LoadedNativeAd.bindInternal()` returns the underlying Google `NativeAd` typed as `Any`. It's used only by the internal native binder in core (Prompt 12). Document this.
4. The escape-hatch customizer receives the Google builder as `Any`.
5. The error mapping table must match architecture §24 exactly, and be unit-tested through a mapping function that takes the enum *name* (String), so it's testable without constructing Google objects. Design `GoogleErrorMapper.mapLoadErrorCode(name: String, message: String, domain: String?, responseId: String?)` and have the Google overload delegate to it.

## 6. Public API requirements
None. Everything in this phase is `internal`. The API dump must be unchanged.

## 7. Constraints
- Don't call the adapter from anywhere yet (the engines come later). Unused internal code is fine for one phase.
- No legacy `com.google.android.gms.ads` imports. (Mediation adapters may reference legacy classes, but Next-Gen provides shims. Our code must use the Next-Gen packages only.)

## 8. Dependencies
None new (Next-Gen and UMP were added in Prompt 01).

## 9. Tests
- `GoogleErrorMapperTest`: every `LoadAdError.ErrorCode` name, and an unknown name → retryable LoadFailed. Every FullScreenContentError name.
- `GoogleRevenueMapperTest` / `GoogleResponseInfoMapperTest`: via intermediate plain-data functions.
- `GoogleImportBoundaryTest`: the scan described in §5.1.
- `MainThreadPostingTest` (Robolectric): described in §5.2.
- `FakeAdsSdkTest`: sanity of the scripting used by all later phases.

## 10. Validation commands
```
./gradlew :admob-core:testDebugUnitTest :admob-core:lint :admob-core:apiCheck build
grep -rn "import com.google.android.libraries.ads\|import com.google.android.ump" admob-core/src/main/kotlin | grep -v "/internal/google/"   # must be empty
```

## 11. Acceptance criteria
- [ ] The ports exist, and the fakes are reusable from other modules (testFixtures) or the reason they aren't is reported.
- [ ] The Next-Gen adapter compiles against the pinned `ads-mobile-sdk`, uses no deprecated API (`-Werror` catches deprecation warnings), and posts all callbacks to main.
- [ ] `MobileAds.initialize` is invoked off the main thread by construction.
- [ ] The import-boundary test passes.
- [ ] The public API dump is unchanged.

## 12. Completion report
Per 00_WORKING_RULES §D. List every Google API used, with its reference URL, and any place where the real API differed from this prompt.
