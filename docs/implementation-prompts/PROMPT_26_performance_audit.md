# PROMPT 26: Performance audit

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §11 (phase A cost), §12, §18, §25, §33.

## 1. Objective
Measure and reduce startup overhead, main-thread work, memory, network requests, and recompositions caused by the library. Record the results in `docs/audits/PERFORMANCE_AUDIT.md`. Measure; don't guess.

## 2. Measurements (each with a method, a number, and a verdict)
1. **`initialize()` cost**: wall time on main, measured with `SystemClock.elapsedRealtimeNanos` around the call in `sample-xml` (debug and minifiedDebug), median of 10 cold starts. Target < 2 ms. Also confirm that no disk I/O happens on main during `initialize` (StrictMode `detectDiskReads/Writes` in the samples' debug builds; the launch-count write must use `apply()`. The first SharedPreferences read loads the file from disk; if StrictMode flags it, move the launch-count read and increment off main, making sure the value is ready before the app-open controller needs it).
2. **`start()` main-thread blocking**: StrictMode + a Perfetto/System Trace capture around startup. Confirm `MobileAds.initialize` runs on a background thread (thread name in the trace).
3. **Startup network requests**: count the ad requests in the first 10 s after `Ready` with the default config in `sample-compose` (via the `events` LoadRequested count). Verify the stagger (250 ms) and that `ON_DEMAND`/`NONE` placements aren't requested.
4. **Retry behaviour offline**: airplane mode for 5 minutes → the number of load attempts per slot must equal the policy (≤ 5 for full-screen), and there are none while backgrounded.
5. **Memory**: heap after loading 1 banner + 1 native + cached interstitial/rewarded vs. baseline (Android Studio Profiler or `dumpsys meminfo`). Native pool destroyed on expiry (verified with the fake clock test plus a real-device observation).
6. **Compose recompositions**: Layout Inspector recomposition counts on the Banner and Native playground screens while an unrelated counter recomposes the parent 100×. `AdBanner`/`AdNative` internals should skip.
7. **Allocation on hot paths**: `AdLog` lambdas aren't evaluated below the level (test exists). `availability()` returns cached `StateFlow` instances, not new ones per call (add a test).
8. **APK size contribution** (from Prompt 20), noted.

## 3. Files to create / modify
- `docs/audits/PERFORMANCE_AUDIT.md`
- StrictMode setup in the samples' debug `Application` (if not present)
- Fixes for any regression found, with tests where possible.

## 4. Constraints
Don't micro-optimise without a measurement. Don't remove safety checks for speed.

## 5. Validation commands
The Prompt 22 CI set, plus the measurement procedures (documented step by step so a teammate can repeat them).

## 6. Acceptance criteria
- [ ] All 8 measurements are recorded with methods and numbers (or marked "not measured" with a reason; the device-dependent ones need an emulator or device).
- [ ] No main-thread disk or network I/O from the library under StrictMode.
- [ ] Any finding above target is fixed or has a justified WARN.

## 7. Completion report
Per 00_WORKING_RULES §D, plus the measurements table.
