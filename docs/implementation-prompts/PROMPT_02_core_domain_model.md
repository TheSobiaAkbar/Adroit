# PROMPT 02: Core domain model and public API skeleton

Read `docs/implementation-prompts/00_WORKING_RULES.md` first and follow it. Then read `docs/architecture/03_PUBLIC_API.md` (all of it) and `02_ARCHITECTURE.md` §9, §11.1, §12, §22, §24, §31, §33.

## 1. Objective
Create the stable, Google-free domain types and public API interfaces in `admob-core`, plus the internal infrastructure every later phase depends on: logger, clock, main-thread dispatching, and the event bus. The facade exists but returns "not initialized" behaviour until Prompt 06.

## 2. Context
- Prompt 01 is done: the modules compile, explicit API mode is on, and BCV is wired.
- The public API must never expose `com.google.*` types (the architecture's API-stability requirement).
- Engine state is main-thread confined (ADR-006). Host callbacks are delivered on main.

## 3. Files to create (all under `admob-core/src/main/kotlin/com/company/admob/`)
- `AdFormat.kt`, `AdPlacement.kt` (sealed interface + the 6 final placement classes, key validation `[a-z0-9_]{1,40}`, `equals`/`hashCode`/`toString`)
- `AdsState.kt` (`AdsState`, `UnavailableReason`), `AdAvailability.kt`
- `AdError.kt` (the sealed hierarchy in 03 §3.2 + `SdkErrorDetails`; each subtype gets a stable `code: String` like `"no_fill"` for analytics)
- `results/FullScreenResult.kt`, `results/RewardResult.kt` (`Reward`), `results/NativeAdResult.kt` (the interface only; `NativeAdHandle` declared in `native/NativeAdHandle.kt`)
- `events/AdEvent.kt` (all subtypes), `events/AdRevenue.kt`, `events/AdSourceInfo.kt`, `events/AdEventListener.kt`
- `logging/LogLevel.kt`, `logging/AdLogger.kt` (public fun interface), `logging/AndroidAdLogger.kt` (public class, the only place `android.util.Log` is used)
- `annotations/ExperimentalCompanyAdMobApi.kt`, `annotations/GoogleSdkEscapeHatch.kt`
- `api/AdsClient.kt`, `api/PrivacyApi.kt` (+ `ConsentState`, `ConsentStatus`), `api/FullScreenAdApi.kt`, `api/InterstitialApi.kt`, `api/RewardedApi.kt` (+ `RewardedShowOptions`), `api/RewardedInterstitialApi.kt`, `api/AppOpenApi.kt`, `api/BannerApi.kt` (+ `BannerOptions`, `BannerSize`, `BannerAdHandle`, `BannerState`), `api/NativeApi.kt` (+ `NativeAdLayout`, `NativeAdRenderer`, `NativeAdAssets`), `api/DiagnosticsApi.kt` (+ `DiagnosticsReport`, `PlacementDiagnostics`, `TestModeInfo`)
- `CompanyAdMob.kt`: the facade object implementing `AdsClient` by delegating to `internal var delegate: AdsClient = UninitializedAdsClient`
- `internal/UninitializedAdsClient.kt`: every show returns `NotShown(AdError.NotInitialized)` / `RewardResult.Failed(NotInitialized)` on main, state is `NotInitialized`, and every other call is a logged no-op
- `internal/logging/AdLog.kt`: an internal wrapper around `AdLogger` + level, with `inline` lazy-message functions `v/d/i/w/e(tag) { msg }`
- `internal/time/Clock.kt`: `internal interface Clock { fun elapsedRealtimeMillis(): Long; fun currentTimeMillis(): Long }` + `SystemClockImpl`
- `internal/threading/MainThread.kt`: `isMainThread()`, `postToMain(block)`, `internal val MainDispatcher = Dispatchers.Main.immediate`, and `internal fun libraryScope(log): CoroutineScope` = `SupervisorJob + Main.immediate + CoroutineExceptionHandler` that logs and never crashes
- `internal/events/EventBus.kt`: `MutableSharedFlow<AdEvent>(replay = 0, extraBufferCapacity = 64, onBufferOverflow = DROP_OLDEST)`, listener fan-out on main with try/catch per listener, and a ring buffer of the last 100 events for diagnostics
- `internal/callbacks/OnceCallback.kt`: wraps a `(T) -> Unit`, uses an `AtomicBoolean`, and invokes at most once on main (used by every show path later)

## 4. Files to modify
- `admob-core/api/admob-core.api` (regenerated with `apiDump`)

## 5. Implementation requirements
1. Use `data class` only where value semantics are intended and future field additions are acceptable. **For public result and event types, prefer `class` with explicit `equals`/`hashCode`/`toString`, or `data class` plus BCV awareness.** Decide per type, keeping in mind that adding a field to a public `data class` breaks binary compatibility (the `copy`/`componentN` functions). Record the decision in `docs/architecture/02_ARCHITECTURE.md` §32 as ADR-011. Recommended: `data class` for small, frozen value types (`Reward`, `AdRevenue`, `SdkErrorDetails`), and regular classes with a `toString` for events and `DiagnosticsReport`.
2. `AdPlacement` constructors throw `IllegalArgumentException` for invalid keys. This is a programmer error at declaration time and is documented.
3. Every public type gets KDoc stating its thread contract and stability.
4. `CompanyAdMob.initialize` isn't implemented yet: it throws `NotImplementedError("Prompt 06")`, and that's acceptable only in this phase. Mark it with a `// TODO(PROMPT-06)`.
5. `FullScreenResult.wasShown` is true only for `Dismissed`.
6. `AdEvent` subtypes carry `placementKey`, `format`, and `timestampMillis`. Provide `AdEvent.toLogString()` (internal) with redaction of ad unit IDs.
7. `OnceCallback` must be safe when `invoke` is called concurrently from two threads (test it).

## 6. Public API requirements
Exactly the declarations in 03_PUBLIC_API §3.1–3.5, §3.7, §3.8, excluding config (Prompt 03), Views (13), Compose (14), and testing (16). The `AdsClient` members are interfaces only.

## 7. Constraints
- No Google SDK imports in this phase (`grep -r "com.google.android" admob-core/src/main` must be empty).
- No config types yet (Prompt 03). Where an interface needs config, leave it out and let Prompt 03 add it.

## 8. Dependencies
None new.

## 9. Tests (`admob-core/src/test/kotlin/com/company/admob/…`)
- `AdPlacementTest`: key validation, equality across the same key and different formats, `toString`.
- `AdErrorTest`: every subtype has a unique, stable `code`, and `sdkDetails` defaults to null.
- `FullScreenResultTest`: `wasShown`.
- `OnceCallbackTest`: a single invocation under 2 threads × 10,000 races (use `CountDownLatch`), and invocation on main (Robolectric `shadowOf(getMainLooper()).idle()`).
- `EventBusTest`: listener exceptions are isolated, the ring buffer is capped at 100, a slow collector doesn't block emit (Turbine), and DROP_OLDEST behaviour.
- `AdLogTest`: messages below the level are never evaluated (lambda counter).
- `UninitializedAdsClientTest`: shows return NotInitialized once, on main.
- `NoGoogleTypesInPublicApiTest`: reads `admob-core/api/admob-core.api` and fails if it contains `com/google/android/libraries/ads` or `com/google/android/ump`.

## 10. Validation commands
```
./gradlew :admob-core:testDebugUnitTest
./gradlew :admob-core:lint
./gradlew apiDump && git diff --stat -- '*.api'
./gradlew build
```

## 11. Acceptance criteria
- [ ] Every type listed in §3 exists with KDoc, and the build is green with warnings as errors.
- [ ] The `.api` dump contains the full public surface, and no Google types.
- [ ] All the tests listed pass.
- [ ] ADR-011 (data class policy) is recorded.
- [ ] No `android.util.Log` outside `AndroidAdLogger` (grep) and no `println` (grep).

## 12. Completion report
Per 00_WORKING_RULES §D. Include the complete `.api` diff.
