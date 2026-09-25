# PROMPT 24 (OPTIONAL): `admob-firebase` integration module

Only run this if the company uses Firebase. Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §7, §21, §22.

## 1. Objective
Provide ready-made `FirebaseRemoteConfigProvider : AdConfigProvider` and `FirebaseAnalyticsEventListener : AdEventListener` in a separate, optional module. `admob-core` gains no Firebase dependency.

## 2. Context
- Firebase is initialized by the host (`google-services` plugin). The module must not apply it.
- Consent Mode: UMP 3.2.0+ calls Firebase consent APIs when it's turned on in the AdMob UI. This module adds **no** consent logic.

## 3. Files to create
- `admob-firebase/build.gradle.kts` (the library + publishing conventions; `api(project(":admob-core"))`; `implementation(platform(libs.firebase.bom))`, `implementation(libs.firebase.config)`, `implementation(libs.firebase.analytics)`. Verify the current BoM and artifact names; the KTX artifacts were merged into the main ones.)
- `admob-firebase/src/main/kotlin/com/company/admob/firebase/FirebaseRemoteConfigProvider.kt`: maps the documented keys to `AdOverrides`:
  - `company_admob_enabled` (bool)
  - `company_admob_disabled_formats` (comma list of `AdFormat` names)
  - `company_admob_min_fullscreen_interval_seconds` (long)
  - `company_admob_placements_json` (a JSON object per placement key: `enabled`, `minIntervalSeconds`, `maxPerSession`, `preload`, `adUnitId`), parsed with `org.json` (on the platform, so no new dependency)

  It emits on `addOnConfigUpdateListener` + `activate`. It doesn't call `fetch` itself unless `autoFetch = true` (the host usually owns fetch timing). Invalid JSON → log and ignore that key.
- `FirebaseAnalyticsEventListener.kt`: maps `AdEvent.Paid` → Firebase `ad_impression` with the standard params (`ad_platform = "admob"`, `ad_source`, `ad_format`, `ad_unit_name`, `currency`, `value` = micros / 1e6), plus optionally `Impression`/`Clicked`/`RewardEarned` → custom events with an `ad_` prefix, controlled by an options set. Verify the current Firebase `ad_impression` parameter names against the Firebase docs.
- Tests with fakes: wrap `FirebaseRemoteConfig` and `FirebaseAnalytics` behind two tiny internal interfaces so the mapping is JVM-testable.
- `docs/REMOTE_CONFIG.md` and `docs/ANALYTICS.md`: add the Firebase sections.

## 4. Files to modify
- `settings.gradle.kts` (include the module), `gradle/libs.versions.toml` (Firebase BoM), and the BCV configuration.
- `checkModuleDependencies`: allow Firebase **only** in `admob-firebase`.

## 5. Implementation requirements
1. This module exists specifically for Firebase, so Firebase types **may** appear in its public constructors. That's the one exception to the "no vendor types" rule, and it applies only to this module. Provide a primary constructor taking `FirebaseRemoteConfig` / `FirebaseAnalytics` (so it supports multi-instance hosts and testing), plus a convenience constructor with default instances. Keep the dump small.
2. Thread-safety: Firebase listeners may call back on background threads. Post to the `Flow` with thread-safe emission (`MutableStateFlow`).

## 6. Public API requirements
The two classes plus an `options` data class. The `.api` dump is reviewed.

## 7. Constraints
Core, views, and compose must stay Firebase-free (the guard passes).

## 8. Dependencies
The Firebase BoM, `firebase-config`, and `firebase-analytics` (this module only).

## 9. Tests
The JSON mapping (valid, invalid, partial), the key mapping, the `ad_impression` param mapping, and listener isolation.

## 10. Validation commands
```
./gradlew :admob-firebase:testDebugUnitTest :admob-firebase:lint :admob-firebase:apiDump checkModuleDependencies verifyPublication
```

## 11. Acceptance criteria
- [ ] The module builds, is tested, and publishes.
- [ ] The core dependency graph is unchanged (the guard passes).
- [ ] The docs are updated.

## 12. Completion report
Per 00_WORKING_RULES §D.
