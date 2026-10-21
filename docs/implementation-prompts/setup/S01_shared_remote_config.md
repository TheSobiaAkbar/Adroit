# S01 — adroit-remote-config: one Remote Config fetch for ads, billing and update

Read `docs/implementation-prompts/setup/S00_WORKING_RULES.md` first and follow it.

## Objective
Replace the two copies of the "fetch Remote Config once" logic with one module that every Firebase consumer uses:
- `adroit-firebase/.../firebase/RemoteConfigSession.kt`
- `adroit-billing-firebase/.../billing/firebase/FirebaseBillingConfig.kt`: its `RemoteConfigFetcher`,
  `RemoteConfigSource` and `isOnline`

Apps no longer call `RemoteConfigSession.start(this)`. The first consumer starts the shared session.

## Context
- **Ads side:**
  - `RemoteConfigSession` fetches once per process. It retries on `onActivityCreated` and when the network comes
    back, and exposes `isFetched: StateFlow<Boolean>`.
  - It depends on `adroit-core`'s `NetworkMonitor` and logging, and notifies `internal/ActivationSignal`, which
    `FirebaseRemoteConfigProvider` listens to.
  - Today apps must call `RemoteConfigSession.start(app)`; see the samples' `SampleApplication.kt`.
- **Billing side:**
  - `RemoteConfigFetcher` is a core-free copy of the same behaviour. It has its own `ConnectivityManager` online
    check and `registerDefaultNetworkCallback`.
  - It gets the Application from `FirebaseApp.getInstance().applicationContext`.
- Billing must never pull in `adroit-core` or the Mobile Ads SDK. That's why the shared piece can't live in
  `adroit-firebase`.

## Files
1. **New module `adroit-remote-config`.**
   - Add it to `settings.gradle.kts`, and use the same convention plugin as `adroit-billing-firebase`.
   - Dependencies: Firebase BOM, `firebase-config`, coroutines-core. `ACCESS_NETWORK_STATE` goes in its manifest.
   - **No `adroit-*` dependency.**
   - `RemoteConfigSession` moves here, in package `io.github.thesobiaakbar.adroit.firebase` (unchanged). It is built
     on billing's core-free fetcher logic.
   - **Public API kept:** `start(application, remoteConfig = getInstance())`, `current()`, `isFetched`,
     `fetchIfNeeded()`.
   - **Public API added:**
     - `startDefault(): RemoteConfigSession?`: starts from `FirebaseApp.getInstance().applicationContext`. It
       returns null and logs once (android `Log`, tag `Adroit`) when Firebase isn't initialized, and never throws.
     - `activations: Flow<Unit>`: emits on collection if already activated, then on each successful activation.
     - `string(key): String?`: null when only Firebase's static default sets it.
   - **Internal:** `RemoteConfigSource`, `FirebaseRemoteConfigSource` and the online check, all moved from billing.
     Tests can construct the session with a fake through `internal` + `@VisibleForTesting`, or a test fixture.
2. **`adroit-firebase`:**
   - Add `api(project(":adroit-remote-config"))`.
   - Delete its `RemoteConfigSession.kt`, its internal `RemoteConfigSource`/`FirebaseRemoteConfigSource`, and
     `ActivationSignal` if nothing else uses it.
   - `FirebaseRemoteConfigProvider` calls `RemoteConfigSession.startDefault()` on first use (if not started) and
     refreshes on `activations`.
   - `RemoteValue` reads through the session.
3. **`adroit-billing-firebase`:**
   - Add `api(project(":adroit-remote-config"))`.
   - `FirebaseBillingConfig.source()` wraps `RemoteConfigSession.start(app, remoteConfig)`: `raw` maps to `string`,
     `updates` maps to `activations`.
   - Delete `RemoteConfigFetcher`, `RemoteConfigSource`, `FirebaseRemoteConfigSource`, `orNull` and `isOnline`
     there.
   - Keep the `BillingConfigSource.none()` fallback when Firebase isn't initialized.
4. **Dependency guard** (`checkModuleDependencies`): allow `adroit-remote-config` for both Firebase modules. It must
   still fail if billing resolves any ads module or the Mobile Ads SDK.
5. **Tests:**
   - Move the session and fetcher tests into `adroit-remote-config` and keep every case (fetch once, retry triggers,
     offline skip, one attempt at a time).
   - Add: `startDefault` without Firebase returns null; `activations` replays after activation.
6. **Samples:** remove `RemoteConfigSession.start(...)` from both `SampleApplication.kt` files. Nothing else in the
   samples changes in S01.
7. **Docs:**
   - Update the module table in `docs/architecture/02_ARCHITECTURE.md` §7 and
     `docs/architecture/06_BILLING_ARCHITECTURE.md` §2.
   - Add one line to `CHANGELOG.md`. Guides come in S08.

## Edge cases
- `start(app, customInstance)` called before a consumer's `startDefault()`: the custom instance wins (first call
  wins, as today).
- An app with billing only: `FirebaseBillingConfig.source()` starts the session; no ads class loads.
- An activation before the provider or billing starts collecting isn't lost (`activations` replays).

## Verify
```
./gradlew :adroit-remote-config:build :adroit-firebase:build :adroit-billing-firebase:build
./gradlew testDebugUnitTest apiDump apiCheck checkModuleDependencies
```
Device (Pixel 7a, sample-xml): logcat shows one "Remote Config fetched" per launch, not two.

## Acceptance criteria
- [ ] Exactly one fetch loop exists in the repo. A grep for `fetchAndActivate` in main sources finds one call site.
- [ ] `adroit-billing-firebase` resolves no ads module and no Mobile Ads SDK (the guard passes).
- [ ] `adroit-remote-config` depends on no `adroit-*` module.
- [ ] `RemoteConfigSession`'s existing public API is binary-compatible (only additions in `.api`).
- [ ] Neither sample calls `RemoteConfigSession.start`, and both still get remote values.
- [ ] Every moved test case still exists and passes.

## Report
S00 §E.
