# PROMPT 19: Sample mixed app and mediation exclusion demo (`sample-mixed`)

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §6, §7, and `01_SDK_RESEARCH.md` §1.1 (F6, F7), §1.5 (the mediation testing notes).

## 1. Objective
Show that XML and Compose integrations coexist in one app (the typical legacy-migration state), and document with a working build how a host adds an AdMob Mediation adapter with the Next-Gen SDK, including the required global exclusion of the legacy SDK.

## 2. Context
- Next-Gen migration guide: mediation adapters still depend on `play-services-ads`. The host must add `configurations.configureEach { exclude(group = "com.google.android.gms", module = "play-services-ads"); exclude(group = "com.google.android.gms", module = "play-services-ads-lite") }`.
- Demo ad units only serve Google ads. Mediation testing needs real units on test devices.

## 3. Files to create / modify (`sample-mixed/`)
- `MixedApplication.kt`, `ads/Placements.kt`, `ads/MixedAdsConfig.kt`
- `LegacyXmlActivity.kt`: an XML layout with an `AdBannerView` + a `ComposeView` hosting `AdNative(...)` + a button showing an interstitial
- `ComposeHostActivity.kt`: a Compose screen embedding an `AndroidView` of a classic XML layout that contains an `AdNativeView`
- `build.gradle.kts`: a **Gradle property flag `-PwithMediation=true`** that adds **one** mediation adapter (choose `com.google.ads.mediation:applovin` or `…:meta`; check the latest adapter version on Google's Maven repo) **and** the global exclusion block. With the flag off, neither is present.
- `sample-mixed/MEDIATION_DEMO.md`: how to build with the flag, the duplicate-class error you get **without** the exclusion (reproduce it once and paste the key lines), and how to verify adapters with Ad Inspector.

## 4. Files to modify
- `build-logic` `checkModuleDependencies`: allow `play-services-ads` to appear **only** as an excluded dependency. The check must still fail if it resolves on any runtime classpath, including sample-mixed with `-PwithMediation=true`.

## 5. Implementation requirements
1. No mediation code in the libraries. The adapter is a host dependency only.
2. The mediation flag build must pass `checkModuleDependencies` (proving the exclusion works).
3. The DEVELOPMENT config with mediation still uses demo IDs (Google-only). Explain in `MEDIATION_DEMO.md` that real mediation testing needs STAGING + real IDs + test devices + each network's test mode.
4. The XML ↔ Compose interop has no double initialization, and both surfaces observe the same `CompanyAdMob` state.

## 6. Public API requirements
None.

## 7. Constraints
Don't commit any real ad unit IDs or network credentials. The adapter dependency is behind a flag and off by default.

## 8. Dependencies
One mediation adapter, behind the flag (sample only).

## 9. Tests
- `./gradlew :sample-mixed:assembleDebug` and `./gradlew :sample-mixed:assembleDebug -PwithMediation=true` both succeed.
- `./gradlew :sample-mixed:dependencies -PwithMediation=true --configuration debugRuntimeClasspath | grep play-services-ads` → only "(excluded)" lines, or nothing.
- An androidTest `InteropSmokeTest`: open both Activities, rotate, go back. No crash.

## 10. Validation commands
The commands in §9, plus `./gradlew checkModuleDependencies -PwithMediation=true`.

## 11. Acceptance criteria
- [ ] XML-hosted Compose and Compose-hosted XML both work with ads.
- [ ] The mediation build works with the exclusion, and the failure without it is documented.
- [ ] The dependency guard holds with and without the flag.

## 12. Completion report
Per 00_WORKING_RULES §D, including the adapter coordinates and version chosen and the exact duplicate-class error text observed.
