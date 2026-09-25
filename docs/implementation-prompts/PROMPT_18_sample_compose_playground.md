# PROMPT 18: Sample Compose "AdMob Playground" (`sample-compose`)

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §10, §18, §22, §30, and `03_PUBLIC_API.md` (all).

## 1. Objective
Build the internal developer playground used to validate every library release. It's a Compose app with live state for initialization, consent, each ad format, diagnostics, configuration, and the event stream.

## 2. Context
- The playground must work only with **demo ad units or registered test devices**.
- It demonstrates DI-style usage: `AdsClient` injected through a tiny manual container (`AppContainer`). **Don't add Hilt**; document how Hilt would bind `AdsClient` to `CompanyAdMob.client` in a code comment and in `docs/GETTING_STARTED.md` (Prompt 23).

## 3. Files to create (`sample-compose/src/main/kotlin/com/company/admob/sample/compose/`)
- `PlaygroundApplication.kt` + `AppContainer.kt` (`val ads: AdsClient = CompanyAdMob.client`)
- `ads/Placements.kt`, `ads/PlaygroundAdsConfig.kt`. A debug-only "config profile" switch (DEVELOPMENT / STAGING-with-test-device) applies **on the next app start** (stored in SharedPreferences, and the app restarts itself). Explain in the UI that config is fixed per process.
- `MainActivity.kt`: `ComponentActivity` + `setContent { PlaygroundApp() }`. It calls `ads.start(this)` in `onCreate`.
- `ui/PlaygroundApp.kt`: Navigation. **Use `androidx.navigation:navigation-compose`** (sample only) with these 12 destinations:
  1. **Dashboard**: `AdsState`, `ConsentState`, the environment, SDK version, a readiness chip per placement (`rememberAdAvailability`), and quick actions
  2. **Initialization**: a state timeline (from `events` `StateChanged`), plus `start()`/`awaitReady()` buttons
  3. **Consent**: consent status, `canRequestAds`, the privacy options button (shown when required), `resetConsentForTesting()` (debug), and the debug geography currently configured
  4. **Banner**: `AdBanner` in anchored adaptive, inline adaptive (max 250 dp), and medium rectangle, with state labels, plus a toggle that removes/adds it to prove disposal
  5. **Interstitial**: availability, preload, show (callback), show (suspend), clear, the last result, and cooldown feedback (`FrequencyCapped.retryAfter`)
  6. **Rewarded**: coins counter (idempotent by `rewardId`), show, and the unclaimed results collector in a `PlaygroundViewModel`
  7. **Rewarded interstitial**: the intro screen (countdown + opt-out) → show
  8. **App open**: enable/disable, `skipNextForeground`, a `suppressWhile` demo, a launch counter display, and instructions to background and foreground the app
  9. **Native**: `AdNative` in small/medium/large, a `LazyColumn` feed with a native every 6 items, and a custom renderer example
  10. **Diagnostics**: `diagnostics.snapshot().toString()` in a scrollable monospace text + copy to clipboard, and an "Open Ad Inspector" button
  11. **Configuration**: a read-only view of the effective placements (key, format, redacted unit, strategy, frequency) and the active remote overrides, plus a **local fake remote-config editor** (an `AdConfigProvider` backed by a `MutableStateFlow<AdOverrides>` in `AppContainer`) to toggle formats and placements live
  12. **Events**: a live list of `AdEvent`s (last 200) with filter chips by format and type
- `ui/theme/`: Material3 theme (sample only), light and dark.

## 4. Files to modify
- `sample-compose/build.gradle.kts`: compose, material3, navigation-compose, lifecycle-viewmodel-compose, activity-compose (sample), `project(":admob-compose")`, and debug LeakCanary.

## 5. Implementation requirements
1. Every screen observes library state through `collectAsStateWithLifecycle`. Nothing polls.
2. Rotation on every screen: no crash, no native reload (visible through a load counter from `events` on the Native screen), and no duplicate banner.
3. Navigation away from a Banner/Native screen → `Destroyed` events visible on the Events screen (proving disposal).
4. Airplane mode → every screen shows failures gracefully.
5. Design is plain Material3 and legible. It's an internal tool; don't invest beyond clarity.

## 6. Public API requirements
None (sample). Report any API friction found.

## 7. Constraints
No Hilt, no Firebase, no real IDs.

## 8. Dependencies
Sample-only Compose, Material3, Navigation, and LeakCanary (debug). Verify the versions.

## 9. Tests (`sample-compose/src/androidTest`)
- `PlaygroundNavigationTest`: visit all 12 destinations and rotate on each. No crash, and LeakCanary `DetectLeaksAfterTestSuccess` reports no leaks.
- `NativeNoReloadTest`: on the Native screen, rotate ×3 → the load counter doesn't increase.
- Mark them `@RequiresNetwork` where they need ads.

## 10. Validation commands
```
./gradlew :sample-compose:assembleDebug :sample-compose:assembleRelease :sample-compose:lint
./gradlew :sample-compose:connectedDebugAndroidTest   # if an emulator is available
```

## 11. Acceptance criteria
- [ ] All 12 screens exist and show live state.
- [ ] The rotation, navigation-disposal, and offline behaviours were verified (tests or a manual QA table).
- [ ] The minified release build assembles and launches.

## 12. Completion report
Per 00_WORKING_RULES §D, plus a manual QA table and a list of API friction points.
