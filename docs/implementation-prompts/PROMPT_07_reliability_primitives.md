# PROMPT 07: Reliability primitives (retry, expiry, frequency, full-screen coordinator)

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §12, §13 (the gate list), §15 (gates 4–7), §20.5–20.6, §25, §33.

## 1. Objective
Build the small, shared, fully tested building blocks every ad engine uses, so no format re-implements backoff, TTLs, caps, or "one full-screen ad at a time".

## 2. Context
- Engine state is main-thread confined. Time comes from `Clock` (monotonic `elapsedRealtimeMillis`). Scheduling uses coroutine `delay` in the library scope, so tests use virtual time (`StandardTestDispatcher` + `FakeClock` kept in sync; provide a `TestClock` that reads the `TestCoroutineScheduler`'s `currentTime`).
- Default values come from `FormatDefaults` (Prompt 03).

## 3. Files to create (`admob-core/src/main/kotlin/com/company/admob/internal/reliability/`)
- `Backoff.kt`: pure function `delayFor(attempt: Int, policy: RetryPolicy, random: Random): Duration`. Exponential, capped at `maxDelay`, jitter `±jitterRatio`, `NoFill` floor 30 s (a parameter), and `attempt` 1-based.
- `RetryScheduler.kt`: `schedule(key, attempt, policy, error, block)`. Cancels any pending job for the key. Doesn't schedule if `attempt > maxAttempts` or the error isn't retryable. `cancel(key)`, `cancelAll()`. It pauses while the app is in the background: jobs that would fire in the background are deferred until the next foreground (uses `AppForegroundSource` / `startedActivityCount`).
- `ExpiringValue.kt`: `class Cached<T>(val value: T, val loadedAtElapsed: Long, val ttl: Duration)` with `isExpired(clock)` and `expiresAtElapsed`.
- `FrequencyGate.kt`: per-placement (`minInterval`, `maxPerSession`) + global (`minIntervalBetweenFullScreenAds` over formats in a configurable set, default {INTERSTITIAL, REWARDED_INTERSTITIAL, APP_OPEN}). `check(placement): FrequencyDecision (Allowed | Capped(retryAfter))`, `recordShown(placement)` (called on **impression/shown**, not on request). Override hooks for remote config come in Prompt 15.
- `FullScreenCoordinator.kt`: `tryAcquire(owner: String): Boolean`, `release(owner)`, `isShowing`, `lastFullScreenEndedAtElapsed`, `lastClickAtElapsed`, `markClicked()`. A token-based release ensures a stale release from an old session can't release a new one.
- `ShowSession.kt`: generic `ShowSession<R>(onResult: OnceCallback<R>, watchdogTimeout)`, `resolve(r)` (first wins), and a watchdog job that resolves with a `timeoutResult` if neither shown nor failed arrives in time. `markShown()` cancels the watchdog.

## 4. Files to modify
None besides the new files. The engines adopt these in Prompts 08–12.

## 5. Implementation requirements
1. Everything here is `internal` and has no Android dependencies except via ports, so it's testable on the plain JVM.
2. `Backoff` with jitter uses an injected `Random` (seeded in tests).
3. `RetryScheduler` keys are strings like `"INTERSTITIAL:<adUnitId>"`.
4. `FrequencyGate` resets per process (in memory). Counters use elapsed time.
5. `ShowSession` watchdog default: `config.advanced.showTimeout` (default 5 s).
6. Keep each file under ~150 lines. If one grows beyond that, reconsider the design.

## 6. Public API requirements
None.

## 7. Constraints
No persistence, no WorkManager, and no `Handler.postDelayed`. Use coroutines only.

## 8. Dependencies
None new.

## 9. Tests
- `BackoffTest`: the sequence 5, 10, 20, 40, 80 s for the full-screen defaults (jitter 0), jitter bounds (1000 samples within ±20%), the cap, and the NoFill floor.
- `RetrySchedulerTest`: schedules and fires under virtual time. Non-retryable → none. Max attempts. Rescheduling cancels the previous job. The background defers until foreground. `cancelAll`.
- `CachedTest`: expiry exactly at the TTL boundary.
- `FrequencyGateTest`: per-placement interval, session cap, the global interval across formats, rewarded exempt by default, and `retryAfter` values.
- `FullScreenCoordinatorTest`: single owner, a stale token can't release, and click timestamps.
- `ShowSessionTest`: resolve once under races, the watchdog fires `Timeout`, `markShown` cancels the watchdog, and a late resolve is ignored.

## 10. Validation commands
```
./gradlew :admob-core:testDebugUnitTest --tests "com.company.admob.internal.reliability.*" :admob-core:lint build
```

## 11. Acceptance criteria
- [ ] 100% line coverage of `internal/reliability` (enable JaCoCo or Kover for this package if cheap, or justify manually per branch).
- [ ] No real sleeping in the tests (virtual time only). The whole suite runs in < 5 s.
- [ ] The defaults match architecture §25 (asserted).

## 12. Completion report
Per 00_WORKING_RULES §D.
