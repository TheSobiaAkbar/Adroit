# PROMPT 08: Full-screen engine and interstitial

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §12, §13 (all), §22, §24, §25, and `03_PUBLIC_API.md` §3.4 (`FullScreenAdApi`, `InterstitialApi`).

## 1. Objective
Implement one generic `FullScreenAdEngine` (cache + load + retry + expiry + show gates + events) and the public `InterstitialApi` on top of it. Rewarded, rewarded-interstitial, and app-open ads reuse this engine in later phases, so design the extension points now without over-generalising.

## 2. Context
- Ports: `AdsSdk.loadFullScreen`, `LoadedFullScreenAd.show`, `FullScreenAdListener`, `Readiness`, `ActivityTracker`, `Clock`, `RetryScheduler`, `FrequencyGate`, `FullScreenCoordinator`, `ShowSession`, `EventBus`, and `EffectiveConfig`.
- The cache is keyed by **(format, effective ad unit ID)**. Several placements may share a slot.
- Library TTL: 55 min for interstitial.

## 3. Files to create (`admob-core/src/main/kotlin/com/company/admob/internal/fullscreen/`)
- `FullScreenAdEngine.kt`: generic over the format. Responsibilities:
  - `preload(placement)`: if `!readiness.canLoad()`, record intent (`pendingPreloads`) and return. Otherwise, if the slot is Idle/Failed/Expired, load.
  - On `Ready` (collect `readiness.state`), load placements with `PreloadStrategy.AFTER_READY` plus pending intents. **Stagger** the initial loads by 250 ms each to avoid a burst at startup.
  - On load success: cache `Cached(ad, now, ttl)`, reset attempts, set availability Ready, and emit `Loaded(latency, source)`.
  - On load failure: availability Failed, emit `LoadFailed(error, attempt, willRetry)`, and schedule a retry via `RetryScheduler`.
  - Never more than one in-flight load per slot.
  - `show(activity, placement, sessionFactory)` runs the §13 gate list **in order** and returns `NotShown(error)` for the first failing gate. It triggers a load on `NotReady`/`Expired`.
  - Before `show`: set the listener, `FullScreenCoordinator.tryAcquire`, and remove the ad from the cache (single use).
  - Listener → events + session. `onShown` → `session.markShown()`, `Shown`. `onImpression` → `FrequencyGate.recordShown`, `Impression`. `onClicked` → `Clicked` + `coordinator.markClicked()`. `onPaid` → `Paid`. `onDismissed` → resolve `Dismissed(clicked)`, release, reload if strategy ≠ NONE. `onFailedToShow` → resolve `NotShown(ShowFailed)`, release, reload.
  - Expiry sweep: when `availability(placement)` is queried or a show is attempted, check expiry. Also schedule a single expiry job per cached ad that discards and reloads it at `expiresAt` (**only while the app is in the foreground**; in the background, mark it expired lazily).
  - Blocked listener (ad-free, consent revoked, remote disable): release all cached ads, cancel retries, and set availability to Disabled.
  - `clear(placement)`: drop the cache and cancel the retry for that slot.
  - `availability(placement): StateFlow<AdAvailability>`: one `MutableStateFlow` per slot, exposed per placement (a placement maps to its slot's flow, combined with the placement `enabled` flag).
- `FullScreenFormatStrategy.kt`: a small interface for per-format differences (`format`, `defaultTtl`, `defaultRetry`, `participatesInGlobalFrequency`, `resultOnDismiss(...)`). Keep it minimal; the rewarded extension comes in Prompt 09.
- `InterstitialApiImpl.kt`: implements `InterstitialApi` (callback + suspend `show`, `preload`, `availability`, `isReady`, `clear`). The suspend `show` uses `suspendCancellableCoroutine`. **Cancellation doesn't cancel an ad that's already showing**; the result is dropped and logged at debug. The interstitial has no reward, so dropping it is safe.

## 4. Files to modify
- `internal/runtime/AdsClientImpl.kt`: wire `interstitial` and `showInterstitial`, and `preload(vararg)` for interstitial placements. Remove the `TODO(PROMPT-08)` placeholder.

## 5. Implementation requirements
1. Gates (exact order, per architecture §13): readiness → placement configured and enabled for INTERSTITIAL (unknown placement → `InvalidConfiguration`) → Activity valid (`ActivityTracker.isValidForShow`) → coordinator free → frequency → cached and fresh → show.
2. `onResult` is always invoked on main, exactly once, **asynchronously** (posted), even for immediate gate failures. Document this: "the callback is never invoked synchronously inside `show`".
3. Never call `ad.show` off main, or with an Activity that fails validation.
4. The watchdog (`ShowSession`) resolves `NotShown(Timeout)` if neither `onShown` nor `onFailedToShow` arrives within `showTimeout`. It releases the coordinator and discards the ad.
5. Ad unit IDs in log lines at INFO and above are redacted in PRODUCTION (last 4 characters).
6. The engine holds **no Activity references** beyond the duration of the `show` call itself.
7. Loads use the `GoogleRequestCustomizer` from config, if any, via `RequestExtras`.

## 6. Public API requirements
`InterstitialApi` exactly as in 03 §3.4, plus `AdsClient.showInterstitial` and `preload`.

## 7. Constraints
Don't implement rewarded or app open yet, but don't design them out either (strategy seam). Keep `FullScreenAdEngine` under about 350 lines; split helpers out if it grows.

## 8. Dependencies
None new.

## 9. Tests (fakes + virtual time; Robolectric only where an Activity is needed)
- Load: preload before Ready → no SDK call → Ready → a single load. AFTER_READY auto-preload. A single in-flight load per slot. A shared ad unit across two placements → one load.
- Failure and retry: NoFill → retry at ≥ 30 s. Network → the 5/10/20 s sequence. Non-retryable → no retry. Attempts exhausted → Failed until an explicit `preload` resets.
- Show gates: one test per gate, each asserting the exact `AdError`.
- Show success: shown, impression, click, dismissed → `Dismissed(clicked = true)`, reload triggered, frequency recorded, coordinator released.
- Show failure: `onFailedToShow` → `NotShown(ShowFailed)`, reload.
- Duplicate show: a second `show` while showing → `AlreadyShowing`. The same cached ad is never shown twice (a fake counter).
- Expired: advance the clock 55 min → show → `Expired`, reload, no SDK show call.
- Watchdog: no callbacks → `Timeout` after 5 s, and the coordinator is released.
- Activity destroyed or finishing → `ActivityUnavailable`, and the ad stays cached.
- The callback is never synchronous (assert a flag set after `show` returns, before the callback).
- Ad-free toggled while cached → the cache is released and availability is Disabled. Toggled back → reload.
- Suspend `show`: returns the result. Cancelling the caller while showing → the ad continues and the coordinator is still released on dismiss.
- Events: the exact event sequence for a full success path (Turbine).

## 10. Validation commands
```
./gradlew :admob-core:testDebugUnitTest --tests "com.company.admob.internal.fullscreen.*" :admob-core:lint apiDump build
```
Manual (emulator): in `sample-xml`, add a button that calls `CompanyAdMob.showInterstitial(this, Placements.Interstitial) { … }` with DEVELOPMENT config, and confirm a Google **test** interstitial shows and the result is logged.

## 11. Acceptance criteria
- [ ] Every §9 test passes.
- [ ] Exactly-once result delivery is proven by tests, including the watchdog and late callbacks.
- [ ] No ad request is ever made while `readiness.canLoad()` is false (asserted in a dedicated test that fails if the fake sees a load in any non-Ready state).
- [ ] The manual test interstitial works, or it's reported as not verified.

## 12. Completion report
Per 00_WORKING_RULES §D, plus the event sequence captured from the success-path test.
