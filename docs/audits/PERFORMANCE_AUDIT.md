# Performance audit

PROMPT 26, run on 2026-09-27 and 28 against branch `feat/companyadmob-foundation` (base commit `dcc87db`), with
ads-mobile-sdk 1.4.0 and UMP 4.0.0. Scope: the startup cost, main-thread work, network requests, retries, memory,
recompositions and hot-path allocations caused by the library. Architecture references: `02_ARCHITECTURE.md` §11,
§12, §18, §25, §33.

Device: Pixel 7a, Android 17 (API 37), on USB power with Stay awake on. Every number says how it was taken; the
procedures at the end repeat them. Debuggable builds (debug and minifiedDebug) run without ahead-of-time
compilation, so their timings are several times the release ones; the release build is the one that counts.

## Result

| # | Measurement | Target | Result | Verdict |
|---|---|---|---|---|
| 1 | `initialize()` wall time on main (median of 10 cold starts) | < 2 ms | **Release 3.2 ms** (3.0 to 3.7). minifiedDebug 35.6 ms (was 86.7 ms before F4), debug 30.1 ms (was 49.9 ms). Configuration building, which reads and parses the ads file: release 1.9 ms. | **WARN** (W1, W2) |
| 2 | `start()` main-thread blocking | No main-thread disk or network I/O from the library; `MobileAds.initialize` off main | StrictMode, 10 debug cold starts: **0 violations from the library** (was 3 per start: F1, F2, F5). The one left per start is Chrome's, from the Google SDK's Custom Tabs warm-up. Method trace: `MobileAds.initialize`, the `gad_rdp` write and the launch count run on `DefaultDispatcher-worker-1`. | FAIL → **fixed** |
| 3 | Startup ad requests, first 10 s after Ready | Only `AFTER_READY` placements, staggered | Playground, 3 cold starts: **6 requests** each: app open and the shared demo native pool at Ready, interstitial, rewarded and rewarded interstitial at +250 ms, the pool's second native after the first loaded. None for banners without a view or for `ON_DEMAND`/`NONE` natives. Pinned by `StartupRequestsTest`. | PASS (observations O1, O2) |
| 4 | Retries offline for 5 minutes | Full-screen policy; none in the background | Virtual time, default policy: **1 request + 5 retries** per slot, then none for the next hour. Backgrounded: only the request made at Ready, the next one on foreground. Not run on the device: it needs airplane mode, a phone setting this audit doesn't change. | PASS (unit) |
| 5 | Memory, 1 banner + native pools + cached full-screen ads vs no ads | Noted | Release, median of 3, 30 to 40 s after launch on the home screen: **TOTAL PSS 262.6 MB vs 162.7 MB** (+100 MB); Java heap 20.1 vs 6.1 MB; native heap 46.8 vs 25.9 MB; code 59.6 vs 39.6 MB; graphics 74.2 vs 59.9 MB. The difference includes the cold-start app open ad, shown and dismissed, and is mostly the Google SDK's WebView-based rendering. Native pool expiry: `NativeLoaderTest` (fake clock); the 55-minute device observation wasn't run. | Noted |
| 6 | Compose recompositions | `AdBanner`/`AdNative` don't reload or re-render | 100 unrelated recompositions: **1** banner created, **1** native load, **1** render (`AdBannerTest`, `AdNativeTest`). Layout Inspector skip counts need Android Studio; the procedure and a "Recompose screen" button are ready. | PASS (unit) |
| 7 | Hot-path allocations | No log building below the level; cached `availability()` flows | `AdLogTest` (messages below the level are never built). New test: `availability()` returns the same `StateFlow` instance on every call, and one shared instance for unknown placements. | PASS |
| 8 | APK size | Noted | **+2,129,473 B** (about 2.03 MiB) release APK, almost all of it the Google SDKs (`R8_REPORT.md` §7). | Noted |

## Findings fixed

**F1: the launch count was read and written on the main thread during `initialize()`.**
- `AdsRuntime` read the count from SharedPreferences inside `initialize()`. The first read of a preferences file
  loads it from disk.
- *Fix:* the read and the increment run on `RuntimeDeps.ioDispatcher` (`Dispatchers.IO`), and `launchCount` is
  `@Volatile`. Only app open's first-launch rule uses the count, and only after `start()` reaches Ready. If it somehow
  isn't read yet, app open doesn't show (`NotReady`, "the launch count isn't read yet") rather than guess.
- *Tests:* `InitializeTest` "the launch count and the consent warm-up run on the IO dispatcher, not during
  initialize"; `AppOpenColdStartTest` "launch count not read yet - not shown, as not ready".

**F2: `gad_rdp` was committed to disk on the main thread in `start()`.**
- `RdpWriter` uses `commit()` on purpose (the SDK reads the value when it starts), but ran on the main thread.
- *Fix:* `AdsSdk.initialize` takes a `beforeInit` step that the adapter runs on its IO thread, right before
  `MobileAds.initialize`. The write still happens before the SDK starts.
- *Tests:* `StartFlowTest` "`gad_rdp` is written before the SDK initializes, and removed before it when turned off".

**F4: the first `Dispatchers.Main` use loaded its factory with `ServiceLoader` inside `initialize()`.**
- Found with StrictMode: `libraryScope` used `Dispatchers.Main.immediate`, and kotlinx-coroutines looks the main
  dispatcher up with `ServiceLoader`, which opens the APK as a jar on the main thread. StrictMode timed it at 60 to 80
  ms per cold start in minifiedDebug. R8's full optimization of the release build mostly removes the lookup, but an
  app built without it, and every debug build, paid it.
- *Fix:* the library's main dispatcher is the main `Handler`'s (`Handler.asCoroutineDispatcher("CompanyAdMob")
  .immediate`, from kotlinx-coroutines-android, already a dependency). Same thread and same `immediate` semantics.
- *Effect:* minifiedDebug `initialize()` 86.7 → 29.4 ms, and its whole cold start (`am start -W` TotalTime) 583 →
  353 ms. Release 3.40 → 3.16 ms.

**F5: UMP's first `getConsentInformation` read its SharedPreferences on the main thread in `start()`.**
- StrictMode: 2 disk reads of about 5 ms each per debug cold start, inside
  `UserMessagingPlatform.getConsentInformation`.
- *Fix:* new `ConsentSdk.warmUp()`, called on the IO dispatcher during `initialize()`, creates the
  `ConsentInformation` in advance. The UMP reference restricts the form methods to the main thread, not
  `getConsentInformation`. The holder is a synchronized `lazy`, so UMP is still created once, and a `start()` that
  comes first waits for it as before. The consent flow passed the device tests of the three samples afterwards.
- *Effect:* debug `initialize()` 45.9 → 30.1 ms (median; the StrictMode logging of the two violations cost time
  too), and debug TotalTime 680 → 495 ms. Release: no measurable change (A/B, 2 rounds of 10: 3.15 and 3.21 ms
  without, 3.17 and 3.31 ms with).

## Warnings

**W1: release `initialize()` takes 3.2 ms, over the 2 ms target.** *Owner:* the library maintainers.
- A temporary probe (removed) timed each step of a release `initialize()`: no step above 0.75 ms. The largest were
  the engines and API objects (about 0.6 ms together), the ports (0.45 ms, including the manifest app ID lookup, a
  binder call), starting the engines' readiness collectors (0.43 ms), the library scope (0.26 ms) and launching the IO
  job (0.3 to 0.7 ms, most of it creating the IO dispatcher's first thread, which `start()` would create anyway). It's
  class loading of about a hundred library classes, spread out, in a 112 ms app cold start (about 3%).
- *Follow-up option:* build the engines, the app open controller and the API objects lazily, on the first `start()`
  or API call, instead of in `initialize()`. That changes Phase A of architecture §11 and should be decided in PROMPT
  27 or FINAL, not slipped into an audit. Lower-end devices will multiply the 3.2 ms; the option is worth taking if
  the team's device range is wide.

**W2: building the configuration with an ads file takes 1.9 ms on the main thread (release).** *Owner:* the library
maintainers.
- `profiles(context, R.raw.ads_config)` reads the raw resource, and the file is parsed when the `AdMobConfig` is
  built, both in the host's `Application.onCreate`. Raw resources are read through `AssetManager`, which StrictMode
  doesn't report. Part of the 1.9 ms is loading the configuration classes, which an app without an ads file pays too.
- *Follow-up option:* same as W1. Parsing can't simply move off the main thread: validation errors must reach
  `initialize()` synchronously in development (failFast).

## Observations

**O1: debug builds may request more native ads than release builds.** With demo ad units, every placement of a
format uses Google's single demo ID, and, as designed (architecture §20, "a shared unit shares its cached ad"), they
share one cache. A native pool shared by several placements takes the largest pool size and the most eager preload
strategy: the playground makes 2 native requests at startup in debug, and would make 1 with real ad units.
`StartupRequestsTest` pins both shapes.

**O2: the 250 ms stagger is per format.** When the library becomes ready, each engine spaces its own placements 250
ms apart, but the interstitial, rewarded and rewarded interstitial engines each start their first load at +250 ms,
within 2 ms of one another on the device (after app open, which loads first when a cold-start show is configured).
All loads run off the main thread, and no cost was measured, so nothing changed; the engine's KDoc no longer claims
the engines are spaced from each other.

## Changes made during the audit

| Change | Files |
|---|---|
| Launch count off the main thread (F1) | `internal/runtime/AdsRuntime.kt`, `internal/runtime/RuntimeDeps.kt`, `internal/appopen/AppOpenController.kt` |
| `gad_rdp` written on the SDK's init thread (F2) | `internal/port/AdsSdk.kt`, `internal/google/NextGenAdsSdk.kt`, `internal/init/InitCoordinator.kt` |
| Main dispatcher from the main `Handler` (F4) | `internal/threading/MainThread.kt` |
| UMP warm-up on the IO dispatcher (F5) | `internal/port/ConsentSdk.kt`, `internal/google/UmpConsentSdk.kt`, `internal/runtime/AdsRuntime.kt` |
| StrictMode (thread: disk reads, disk writes, network; VM: leaked closables, Activity leaks; log penalty) in the debug builds of the three samples | `SampleApplication.kt`, `PlaygroundApplication.kt`, `MixedApplication.kt` |
| Configuration and `initialize()` timing, logged under the tag `CompanyAdMobPerf` by sample-xml | `SampleApplication.kt` |
| A "Recompose screen" button on the playground's Banner and Native screens (measurement 6) | `BannerScreen.kt`, `NativeScreen.kt` |
| New tests: startup requests (2), offline retries (2), cached `availability()`, the IO-dispatcher work in `initialize`, app open before the count is read | `StartupRequestsTest.kt`, `FullScreenLoadTest.kt`, `InitializeTest.kt`, `AppOpenColdStartTest.kt` |
| Compose recomposition tests raised from 10 to 100 recompositions | `AdBannerTest.kt`, `AdNativeTest.kt` |
| sample-xml device tests: keep rules for the diagnostics API the new `AdProfilesDeviceTest` reads (R8 removed it from the minified app); and the tests' `closeAd` now also taps the app open test ad's "Continue to app" button. That ad ignores back, so the first test of a run, the only one that gets a cold start, failed whenever a cold-start app open ad showed (3 runs of 15). The button is web content that UI Automator sees only about 5 s after the ad appears, so that first test waits 20 s for the main screen | `proguard-minified-debug-rules.pro`, `AdAwareNavigation.kt`, `AdProfilesDeviceTest.kt` |

## Procedures

`adb` is in the Android SDK's `platform-tools`. In Git Bash, set `MSYS_NO_PATHCONV=1` first.

### 1. `initialize()` cost
1. Install a build of sample-xml: `./gradlew :sample-xml:installRelease` (release numbers count), and optionally
   `installMinifiedDebug` and `installDebug`.
2. Ten times: `adb shell am force-stop com.company.admob.sample.xml`, `adb logcat -c`, then
   `adb shell am start -W -n com.company.admob.sample.xml/.SplashActivity`; wait 6 s, then
   `adb logcat -d -s CompanyAdMobPerf:I`. Each start logs one line: `config took … µs, initialize took … µs`. Read it
   after every start: the ad logs can push it out of the log buffer.
3. Record the medians. Target: `initialize` under 2 ms in release.

### 2. Main-thread I/O and `start()`
1. With the debug build installed, repeat the cold starts of 1.2, saving `adb logcat -d -v threadtime StrictMode:D
   '*:S'` after each one.
2. Count the violations whose stack has a `com.company.admob` frame outside the sample's package. Target: 0.
   Violations with only Google SDK, Chrome or sample frames are recorded but aren't library findings.
3. Method trace of a cold start: `adb shell am start -W --start-profiler /data/local/tmp/start.trace --sampling 200
   -n com.company.admob.sample.xml/.SplashActivity`, wait 8 s, `adb shell am profile stop
   com.company.admob.sample.xml`, `adb pull /data/local/tmp/start.trace`. Open it in Android Studio (Profiler →
   load from file) and check the thread of `MobileAds.initialize`, `RdpWriter.apply` and
   `SharedPrefsKeyValueStore.getInt`: a `DefaultDispatcher-worker` thread, not `main`.

### 3. Startup ad requests
1. Install the playground: `./gradlew :sample-compose:installDebug`. Launch it by its activity
   (`adb shell am start -W -n com.company.admob.sample.compose/.MainActivity`), not with `monkey`, which can pick
   LeakCanary's launcher.
2. Wait 14 s, then `adb logcat -d -v time CompanyAdMob:V '*:S'`. Count the `Loading …` lines in the 10 s after
   `State: InitializingSdk -> Ready`.
3. Expected (debug build, demo ad units): app open and one native at Ready; interstitial, rewarded and rewarded
   interstitial at about +250 ms; a second native after the first loads (O1). No `feed` or `custom` native request
   until their screens ask.

### 4. Offline retries
1. In the playground, wait for `Ready`, then turn on airplane mode.
2. On the Interstitial screen, press "Clear", so the slot reloads while offline. Leave the app in the foreground for
   5 minutes.
3. Count the interstitial `Loading …` lines after airplane mode: expected 1 request + 5 retries at about 5, 10, 20,
   40 and 80 s (±20%), then none.
4. Repeat with the app in the background for the 5 minutes: expected no requests until it's in the foreground again.

### 5. Memory
1. Baseline: set `"adsEnabled": false` in sample-xml's `res/raw/ads_config.json` (don't commit it), then
   `./gradlew :sample-xml:installRelease`.
2. Three times: force-stop, cold start, press back whenever a full-screen ad covers the app, wait until 40 s after
   the start, then `adb shell dumpsys meminfo com.company.admob.sample.xml`; note TOTAL PSS, Java Heap, Native
   Heap, Code and Graphics from the App Summary.
3. Restore the file, install again, repeat 2, and record the medians' differences.
4. Native pool expiry: leave a pooled native ad for more than 55 minutes in the foreground, then check the
   diagnostics: the pool's ad was destroyed and a new one loaded.

### 6. Compose recompositions
1. In the playground, open the Banner and Native screens. Android Studio → Layout Inspector → enable recomposition
   counts.
2. Press "Recompose screen" 100 times: it recomposes the whole screen. `AdBanner` and `AdNative` should show
   skipped recompositions, and there should be no new loads (the Native screen's load counter doesn't move, and the
   Events screen shows no new `LoadRequested`).

## Constraints respected
- No change without a measurement: F1, F2 and F5 were StrictMode violations, and F4 was a StrictMode violation that
  cost 60 to 80 ms. W1's lazy-construction option is left to a decision, not done.
- No safety check was removed or relaxed: `gad_rdp` is still committed before the SDK starts, UMP is still created
  once, and the launch-count rule still blocks app open when the count is unknown.
