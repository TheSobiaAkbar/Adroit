# PROMPT 17: Sample XML app (`sample-xml`)

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §11, §15, §19, and `03_PUBLIC_API.md` §3.1–3.5, §3.9.

## 1. Objective
Turn `sample-xml` into a realistic View-based integration reference: Application init, a splash with consent and a cold-start app-open ad, Activities and Fragments with ViewBinding, every ad format, a privacy-options entry, and leak detection. It also serves as the instrumentation test target.

## 2. Context
- `sample-xml` is the renamed `:app` (AppCompat + XML). The app ID comes from a manifest placeholder, defaulting to Google's sample ID.
- Environment: DEVELOPMENT for debug, STAGING for a new `staging` build type, and PRODUCTION for release, **but** release in the sample must still use demo IDs. So release uses `AdEnvironment.STAGING` with `useDemoAdUnits = true`, and a **comment** explains how a real app would use PRODUCTION. Never put real IDs in the sample.

## 3. Files to create / modify (`sample-xml/src/main/`)
- `SampleApplication.kt`: `CompanyAdMob.initialize(this, SampleAdsConfig.create(BuildConfig.BUILD_TYPE))`
- `ads/Placements.kt`: `object Placements { HomeBanner, DetailsBanner, LevelCompleteInterstitial, ExtraLifeRewarded, BonusRewardedInterstitial, AppOpen, FeedNative, ArticleNative }`
- `ads/SampleAdsConfig.kt`: the DSL config. Placeholder real-ID strings are `"ca-app-pub-0000000000000000/0000000000"` with a comment, since they're replaced by demo IDs in DEVELOPMENT/STAGING.
- `SplashActivity.kt` (the launcher, excluded from app open): `CompanyAdMob.start(this) { … }`, then `CompanyAdMob.appOpen.showOnColdStart(this, 4.seconds) { goToMain() }`. It uses the AndroidX SplashScreen API only if it's already in the catalog (don't add it otherwise; use a simple layout).
- `MainActivity.kt`: a `BottomNavigationView` or `TabLayout` (Material is allowed in samples) hosting Fragments:
  - `HomeFragment`: an `AdBannerView` declared in XML (`app:company_admob_placement="home_banner"`), plus buttons for "Level complete" (interstitial, navigating to `DetailsActivity` in **both** result branches) and "Extra life" (rewarded, via a `CoinsViewModel` that also collects `unclaimedResults`)
  - `FeedFragment`: a RecyclerView of 30 fake items with a native ad every 8th position, using `NativeAdHolder`s owned by `FeedViewModel` (demonstrating the list pattern)
  - `SettingsFragment`: a privacy options button that is **visible only when** `privacy.isPrivacyOptionsRequired` is true, an "Ad-free" switch → `setAdFree`, and "Open Ad Inspector"
- `DetailsActivity.kt`: `attachBanner(this, Placements.DetailsBanner)` + an `AdNativeView` (template large) in XML
- `BonusIntroDialogFragment.kt`: the **required intro/opt-out screen** for rewarded interstitial, with a 5 s countdown and a "No thanks" button, then `showRewardedInterstitial`
- Layouts with ViewBinding (enable `viewBinding` in `sample-xml/build.gradle.kts`)
- `debug` dependencies: `leakcanary-android` (2.x)
- `sample-xml/src/androidTest/`: an instrumented smoke test (see §9)

## 4. Files to modify
- `sample-xml/build.gradle.kts`: viewBinding, a `staging` build type (`initWith(debug)`, `matchingFallbacks += "debug"`), Material/AppCompat/RecyclerView/ConstraintLayout dependencies (sample only), and `androidTestImplementation` for the test libraries + `leakcanary-android-instrumentation`.
- `AndroidManifest.xml`: register the Activities and the Application. Enable `android:hardwareAccelerated="true"` explicitly at the application level, with a comment linking the Google video-ads requirement.

## 5. Implementation requirements
1. Every screen continues to work normally when ads fail: turn on airplane mode → no crash and navigation continues. Verify it manually and report.
2. No Google SDK classes are referenced in sample code (the compile classpath already prevents it; keep it that way).
3. The CoinsViewModel grants coins keyed by `rewardId` (a `Set<String>` of granted IDs), demonstrating idempotency.
4. The settings privacy button's visibility is driven by the StateFlow via `repeatOnLifecycle`.
5. A `staging` build shows how test devices work: `testing { testDeviceIds += BuildConfig.TEST_DEVICE_IDS }`, read from `local.properties` (empty by default).

## 6. Public API requirements
None (sample only). If the sample reveals an awkward API, **report it** rather than changing the library silently.

## 7. Constraints
No real ad unit IDs or app IDs. No Firebase. No Hilt (sample-compose shows DI style; keep this sample plain).

## 8. Dependencies
Sample-only: AppCompat, Material, RecyclerView, ConstraintLayout, Fragment KTX, and LeakCanary (debug + androidTest instrumentation). Verify the versions.

## 9. Tests (`sample-xml/src/androidTest`, run on an emulator)
- `StartupSmokeTest`: launch the splash → reaches `MainActivity` within 10 s whether or not an ad shows (the ad is dismissed via `UiDevice.pressBack()` if one appears).
- `InterstitialFlowTest`: tap "Level complete" → either an ad shows (press back) or not → `DetailsActivity` is displayed.
- `FragmentLeakTest`: switch tabs ×10 and rotate ×3 with the `DetectLeaksAfterTestSuccess` rule (LeakCanary instrumentation) → no leaks.
- These tests need network for real test ads. Mark them with a custom `@RequiresNetwork` annotation, and make the CI nightly job run them.

## 10. Validation commands
```
./gradlew :sample-xml:assembleDebug :sample-xml:assembleStaging :sample-xml:assembleRelease :sample-xml:lint
./gradlew :sample-xml:connectedDebugAndroidTest   # if an emulator/device is available
```

## 11. Acceptance criteria
- [ ] All formats are demonstrated in View-based code, including a Fragment with `viewLifecycleOwner`, a RecyclerView native list, and the rewarded-interstitial intro screen.
- [ ] The minified release build assembles.
- [ ] The instrumented tests pass on an emulator (or are reported as not run, with the reason).
- [ ] LeakCanary reports no leaks during the manual walkthrough (or the leaks are reported with traces).
- [ ] Airplane-mode walkthrough: no crash.

## 12. Completion report
Per 00_WORKING_RULES §D, plus a manual QA table (screen × format × online/offline).
