# PROMPT 16: `admob-testing` module (fakes for host apps)

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §7 (the testing row), §26, and `03_PUBLIC_API.md` §3.11.

## 1. Objective
Publish a small, stable `FakeAdsClient` so host apps can unit-test ViewModels, repositories, and UI logic that depend on `AdsClient` without Google, a device, or Robolectric.

## 2. Context
- Hosts that inject `AdsClient` (Hilt or manual) swap in `FakeAdsClient` in tests. Hosts that call the `CompanyAdMob` object directly can't swap it, which is documented as a reason to prefer injection. Provide an `@VisibleForTesting`-style public hook **only in admob-testing**: `CompanyAdMobTestRule` (a JUnit4 rule) that installs a fake delegate into `CompanyAdMob` and restores it after the test. This needs a restricted hook in core (`CompanyAdMob.installForTesting(client)`), marked `@RestrictTo(LIBRARY_GROUP)` or behind the ADR-012 opt-in. Justify the choice.

## 3. Files to create (`admob-testing/src/main/kotlin/com/company/admob/testing/`)
- `FakeAdsClient.kt`: implements the full `AdsClient`, including every sub-API (`FakeInterstitialApi`, `FakeRewardedApi`, and so on as nested or separate classes). Features:
  - `setState(AdsState)`
  - `enqueueInterstitialResult(placement, result)`, `enqueueRewardResult(placement, result)`, `enqueueRewardedInterstitialResult(...)`, `enqueueAppOpenResult(...)`. With an empty queue the default result is `NotShown(NotReady)` / `RewardResult.Failed(NotReady)`.
  - `setAvailability(placement, AdAvailability)`
  - `emit(AdEvent)` and `emitUnclaimedReward(...)`
  - `recordedCalls: List<FakeCall>` (a sealed class: `ShowInterstitial(placementKey)`, `ShowRewarded(placementKey)`, `Preload(keys)`, `Start`, `SetAdFree(value)`, `ShowPrivacyOptions`, …)
  - Callbacks run **synchronously** by default for easy testing, configurable to post on a supplied `Executor` or `CoroutineDispatcher`. **This differs from production, and the KDoc must say so.**
  - `banner.create` and `native.load`/`render`: return `FakeBannerAdHandle` (a plain `View` created with the provided Activity) and `FakeNativeAdHandle` (with configurable `NativeAdAssets`). Rendering inflates the given layout and fills the text, so hosts can screenshot-test templates.
- `CompanyAdMobTestRule.kt`: a JUnit4 `TestWatcher` that installs and restores the facade delegate.
- `FakeCall.kt`, `FakeBannerAdHandle.kt`, `FakeNativeAdHandle.kt`

## 4. Files to modify
- `admob-core`: the restricted test-install hook (see §2).
- `admob-testing/build.gradle.kts`: `api(project(":admob-core"))` and `compileOnly(junit)`. JUnit is `compileOnly` so the test framework isn't forced on consumers. Consumers already have it; document this.

## 5. Implementation requirements
1. `FakeAdsClient` must be kept in sync with `AdsClient`: a compile-time guarantee, since it implements the interface.
2. It must not depend on Google classes. `checkModuleDependencies` should also forbid Google Ads SDK classes from being referenced in the testing module's sources (a grep test).
3. It must be usable from pure JVM tests (no Android main looper needed) for the non-View APIs.

## 6. Public API requirements
03 §3.11 plus `CompanyAdMobTestRule`. Its own `.api` dump.

## 7. Constraints
Don't expose engine internals or `AdsRuntime`.

## 8. Dependencies
`compileOnly(junit)` only.

## 9. Tests (`admob-testing/src/test`)
- A sample host `LevelViewModel` (in the test sources) that shows an interstitial after level complete and grants coins for rewarded, tested using `FakeAdsClient`: queued results, recorded calls, and the unclaimed reward flow.
- `CompanyAdMobTestRuleTest`: the facade is swapped and restored.

## 10. Validation commands
```
./gradlew :admob-testing:testDebugUnitTest :admob-testing:lint :admob-testing:apiDump build
```

## 11. Acceptance criteria
- [ ] A host-style ViewModel test runs on the plain JVM using only `admob-testing`.
- [ ] No Google references in admob-testing (grep test).
- [ ] The API dump was reviewed.

## 12. Completion report
Per 00_WORKING_RULES §D.
