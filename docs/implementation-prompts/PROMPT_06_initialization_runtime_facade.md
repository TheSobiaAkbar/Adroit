# PROMPT 06: Initialization coordinator, AdsRuntime, facade, and lifecycle tracking

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §9 (composition root), §11 (all), §20.3, §30, §33, and `03_PUBLIC_API.md` §3.1.

## 1. Objective
Make `CompanyAdMob.initialize(application, config)` and `CompanyAdMob.start(activity)` work end to end: validation, lifecycle tracking, consent, background SDK initialization, the `AdsState` machine, `awaitReady`, `setAdFree`, and the readiness gate the ad engines will use.

## 2. Context
- Phase A (`initialize`) is synchronous, cheap, has no network, and makes no Google calls. Phase B (`start`) runs consent → SDK init → `Ready`.
- Nothing may load before `Ready` (§11 rules).
- Prompts 02–05 provide the domain model, config, ports, adapter, and consent.

## 3. Files to create (`admob-core/src/main/kotlin/com/company/admob/internal/`)
- `runtime/AdsRuntime.kt`: the hand-written composition root. `internal class AdsRuntime(app: Application, config: AdMobConfig, deps: RuntimeDeps)`. `RuntimeDeps` holds the port instances; `RuntimeDeps.production(app)` builds the real ones, and tests pass fakes.
- `runtime/AdsClientImpl.kt`: implements `AdsClient`. Sub-APIs that don't exist yet (interstitial and so on) are wired to temporary `NotYetAvailable*Api` objects returning `NotShown(Internal("not implemented"))`. **Mark each with `TODO(PROMPT-NN)`.**
- `init/InitCoordinator.kt`: owns the `AdsState` `MutableStateFlow`. Has `initSdkOnce()` with an `AtomicBoolean` guard, retry-on-`start` after failure (≥ 30 s apart, using `Clock`), and applies RDP (`RdpWriter`) and the request configuration **before** `MobileAds.initialize`.
- `init/Readiness.kt`: `internal interface Readiness { val state: StateFlow<AdsState>; fun canLoad(): Boolean; suspend fun awaitReady() }`. `canLoad()` = `state == Ready`. Engines depend on this interface only.
- `lifecycle/LifecycleActivityTracker.kt`: `Application.ActivityLifecycleCallbacks`. Tracks `resumedActivity` (set on resume, **cleared on pause and destroy**, so no WeakReference is needed; explain this in KDoc), `startedActivityCount`, and an `isValidForShow(activity)` helper (not finishing, not destroyed, and `lifecycle ≥ RESUMED` if it's a `LifecycleOwner`).
- `lifecycle/ProcessLifecycleForegroundSource.kt`: `ProcessLifecycleOwner.get().lifecycle` observer → `SharedFlow<ForegroundEvent>` with `isColdStart` (the first ON_START of the process). Register on main.
- `storage/SharedPrefsKeyValueStore.kt`: file `company_admob_state`, which stores only `launch_count` (incremented once per process in `initialize`).
- `AdFreeController.kt`: `setAdFree(true)` → state `Unavailable(AD_FREE_USER)`, and notifies registered `AdFreeListener`s (engines register in later phases to destroy caches). `setAdFree(false)` → re-evaluates to `Ready` if the SDK is initialized and `canRequestAds`, otherwise to the appropriate state. **Not persisted by the library**; the host re-applies it on every launch (document why: the host owns entitlement truth).

## 4. Files to modify
- `CompanyAdMob.kt`: implement `initialize` (replacing the Prompt 02 TODO) and delegate to `AdsClientImpl`.
- `api/AdsClient.kt`: KDoc only, if needed.

## 5. Implementation requirements
1. `initialize`:
   - `synchronized` once-only. A second call with an **equal** config does nothing. A different config logs an error, keeps the first, and records a diagnostics warning.
   - It must be called with an `Application`, and must be callable off the main thread; internally it posts main-thread work.
   - Validate with `ConfigValidator(isDebuggable = appInfo.flags and FLAG_DEBUGGABLE != 0)`. On errors: `failFast` → throw `IllegalStateException` listing every error; otherwise state `Failed(InvalidConfiguration)`. Warnings → logs + `AdEvent.ConfigurationWarning`.
   - Register the tracker and foreground source. Increment the launch count. State → `AwaitingStart`.
2. `start(activity, onComplete)`:
   - From `NotInitialized`: log an error and call `onComplete(NotInitialized)`.
   - From `AwaitingStart`, `Unavailable(CONSENT_NOT_OBTAINED)`, or `Failed(SdkInitFailed)` (after the retry interval): run `ConsentCoordinator.gather`.
   - From `GatheringConsent`/`InitializingSdk`: attach `onComplete` to the in-flight run.
   - From `Ready`: call `onComplete(Ready)` immediately (posted to main).
   - Other `Unavailable` reasons: call `onComplete(current)`.
   - `onComplete` fires exactly once (`OnceCallback`) when the state settles (Ready/Unavailable/Failed).
3. SDK init: `AdsSdk.initialize(appContext, appId, SdkRequestConfig, disableCrashReporting)` is called once. On success → `Ready` unless ad-free or remote-disabled; on failure → `Failed(SdkInitFailed)`. Ad-free during init: finish init, then state `Unavailable(AD_FREE_USER)`.
4. `awaitReady(timeout)` suspends until `Ready` or a terminal state, or the timeout. It returns a Boolean and never throws on timeout.
5. `CompanyAdMob.client` returns the live `AdsClient` (for DI).
6. Consent revoked (from `PrivacyApi`) → `Unavailable(CONSENT_NOT_OBTAINED)`, and notifies the `AdFreeListener`-style "ads blocked" listeners (rename this to `AdsBlockedListener` to cover ad-free, consent, and remote).
7. A test-only reset hook: `internal fun CompanyAdMob.resetForTests()`, annotated `@VisibleForTesting`, used by later Robolectric tests. It must not be public.
8. Emit `AdEvent.StateChanged` on every transition. Log transitions at INFO.

## 6. Public API requirements
`CompanyAdMob.initialize`, `client`, `state`, `isReady`, `start`, `awaitReady`, `setAdFree`, and `privacy`, all as in 03 §3.1. The API dump must reflect only intended changes.

## 7. Constraints
- No ad loading in this phase.
- `initialize` must not touch `MobileAds` or UMP (verified with the fakes: zero calls).

## 8. Dependencies
None new (`lifecycle-process` was added in Prompt 01).

## 9. Tests
- `InitializeTest`: idempotent, a different config is ignored with an error, invalid config with failFast true → throws and with failFast false → `Failed`, no SDK or consent calls, launch count increments once per process, and concurrent `initialize` from 8 threads → a single setup.
- `StartFlowTest` (fakes): happy path to Ready. A consent-not-obtained path. SDK init failure → Failed → retry after 30 s of virtual time → Ready. A double `start` while in flight → a single SDK init and both callbacks fire. Rotation (start with a new Activity mid-gather). `start` before `initialize`.
- `ReadinessTest`: `canLoad()` is false in every state except Ready, and `awaitReady` handles timeouts.
- `AdFreeTest`: toggling before init, during init, and after Ready. The blocked listeners are notified.
- `ActivityTrackerTest` (Robolectric `ActivityScenario`): resumed reference cleared on pause and destroy, `isValidForShow` for finishing and destroyed Activities, and no leak (`WeakReference` check after scenario close + GC).
- `ForegroundSourceTest` (Robolectric + `TestLifecycleOwner`, or simulating `ProcessLifecycleOwner`): cold-start flag only on the first start.

## 10. Validation commands
```
./gradlew :admob-core:testDebugUnitTest :admob-core:lint apiDump build
./gradlew :sample-xml:installDebug   # optional manual check below
```
Manual (if an emulator is available): in `sample-xml`, call `initialize` in a new `SampleApplication` (development config, one interstitial placement) and `start(this)` in `MainActivity`. Confirm with logcat that the state reaches `Ready` and that a UMP debug form appears when `debugGeography = EEA`.

## 11. Acceptance criteria
- [ ] Every test in §9 passes.
- [ ] The state machine matches the diagram in architecture §11.1, and no other transitions exist (add a transition-table test).
- [ ] `MobileAds.initialize` runs off main (the fake records the calling thread in tests; the adapter guarantees it in production).
- [ ] No Activity leak in the tracker tests.
- [ ] The sample reaches `Ready` on an emulator, or it's reported as not verified because no emulator was available.

## 12. Completion report
Per 00_WORKING_RULES §D, plus the state transition table as implemented.
