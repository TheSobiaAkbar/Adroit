# PROMPT 10: App-open controller

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §15 (all), §12, §20.6, and `01_SDK_RESEARCH.md` §1.4 (the app-open guidance).

## 1. Objective
Implement an application-scoped `AppOpenController` that shows app-open ads on warm foregrounds and, optionally, from a cold-start loading screen. Include every gate needed to avoid loops, bad timing, and showing over other full-screen content, billing, or excluded screens.

## 2. Context (Google guidance, Next-Gen app-open docs)
- An app-open ad expires 4 h after loading (library TTL 3 h 55 min).
- Don't show one on the very first app start. Show the first one only after the user has opened the app several times.
- On cold start, show only from a loading screen. If the content becomes ready before the ad loads, **don't show** it.
- If the loading screen finishes while the ad is displayed, dismiss the loading screen from the ad-dismissed callback.

## 3. Files to create (`admob-core/src/main/kotlin/com/company/admob/internal/appopen/`)
- `AppOpenController.kt`: collects `AppForegroundSource` events and uses `FullScreenAdEngine` (APP_OPEN strategy) for cache, load, and show, plus `ActivityTracker`, `FrequencyGate`, `FullScreenCoordinator`, `KeyValueStore` (launch count), and `Clock`.
- `AppOpenSuppression.kt`: the `skipNextForeground` flag, lifecycle-scoped suppressors (`suppressWhile(owner)`: active while `owner.lifecycle ≥ STARTED`, auto-removed on `ON_DESTROY`), and the post-full-screen suppression window.
- `AppOpenApiImpl.kt`: implements `AppOpenApi`.
- `AppOpenStrategy` (in `fullscreen/`)

## 4. Files to modify
- `AdsClientImpl.kt` (wire `appOpen`) and `FullScreenAdEngine.kt` (only if a hook is needed so app open can call "show if available now, else load" without the generic `NotReady` load-and-return behaviour being exposed as an event to the host).

## 5. Implementation requirements
1. Warm foreground event (`isColdStart == false`), in this gate order. On the first failure, **don't show**, and make sure a preload exists for next time:
   1. `readiness.canLoad()`, app open enabled (config/remote), not ad-free
   2. `launchCount > minLaunchesBeforeFirstShow` (default 2)
   3. `!skipNextForeground` (consume the flag)
   4. No active suppressor
   5. `!coordinator.isShowing` and `now - coordinator.lastFullScreenEndedAt > suppressAfterFullScreenAd` (5 s) and `now - coordinator.lastClickAt > suppressAfterFullScreenAd`
   6. There's a resumed Activity, it passes `isValidForShow`, and its class name isn't in `excludedActivityClassNames`. **Wait up to 1 s after ON_START for an Activity to resume**, because ON_START can precede `onResume`.
   7. `FrequencyGate` (cooldown default 4 min)
   8. A cached, fresh ad exists. **If not, load now and never show it late for this foreground.**
2. Cold start: the process's first ON_START is **ignored** by the automatic path. `showOnColdStart(activity, maxWait, onComplete)`:
   - Waits up to `maxWait` for `Ready` + a loaded ad (triggering the load as soon as Ready), then applies gates 1–7 (with gate 2 still applying) and shows.
   - `onComplete(FullScreenResult)` is called exactly once: after dismissal, immediately on `NotShown`, or at the timeout with `NotShown(Timeout)`, in which case the ad is kept for next time.
   - If the Activity is paused or destroyed during the wait → `NotShown(ActivityUnavailable)`.
   - `config.appOpen.showOnColdStartAllowed = false` → immediate `NotShown(Disabled)`.
3. Loop prevention: after an app-open ad is dismissed, the next ON_START (which returning from the ad doesn't trigger, but the ad's click-through can) is suppressed by gate 5. Add a hard rule too: never show two app-open ads within the cooldown, even across `showOnColdStart`.
4. Preload: when `Ready` and app open is enabled → preload (staggered after the other formats; app open goes first if `showOnColdStartAllowed`).
5. The controller holds no Activity references except inside a `show` call. Suppressors hold `LifecycleOwner`s only until `ON_DESTROY`.
6. `setEnabled(false)` → stops automatic showing, keeps the cache (so re-enabling is instant), and cancels pending waits with `NotShown(Disabled)`.

## 6. Public API requirements
`AppOpenApi` as in 03 §3.4: `showOnColdStart`, `skipNextForeground`, `suppressWhile`, `setEnabled`, and `availability`.

## 7. Constraints
- Don't depend on `androidx.fragment` or Navigation.
- Don't read Activity intents or extras.

## 8. Dependencies
None new.

## 9. Tests (fakes, virtual time, and Robolectric for the Activity/lifecycle pieces)
- A warm foreground with a cached ad and all gates passing → shown once.
- The first launch and launch count ≤ 2 → not shown.
- The cold-start ON_START → not shown automatically.
- `skipNextForeground` → the next foreground is skipped and the following one shows.
- `suppressWhile(owner)`: suppressed while started, auto-removed on destroy (no leak).
- Returning from an interstitial click-through within 5 s → not shown.
- An excluded Activity (splash) → not shown.
- Cooldown → not shown again within 4 min.
- No cached ad → not shown, a load is started, and when the load completes while still in the foreground → **still not shown** (no late show).
- An expired ad (advance 3 h 56 min) → not shown, and a reload happens.
- Another full-screen ad showing → not shown.
- `showOnColdStart`: an ad loads within maxWait → shown and `onComplete` after dismissal. Timeout → `NotShown(Timeout)` and the ad is kept. Activity destroyed during the wait → `ActivityUnavailable`. Called twice → the second gets `AlreadyShowing` or attaches (decide, document, test).
- A rapid background/foreground ×20 → at most 1 show (the loop test).

## 10. Validation commands
```
./gradlew :admob-core:testDebugUnitTest --tests "com.company.admob.internal.appopen.*" :admob-core:lint apiDump build
```
Manual: in the sample, press Home and return (with the launch count bumped through `adb shell` or a debug menu that resets it). A test app-open ad appears. Rotate and navigate: no repeats.

## 11. Acceptance criteria
- [ ] Every scenario in §9 passes, including the 20× loop test.
- [ ] App open never shows over the configured splash, a billing flow, or another full-screen ad.
- [ ] No Activity or LifecycleOwner leaks (Robolectric + WeakReference assertions).

## 12. Completion report
Per 00_WORKING_RULES §D, including the decision on the second `showOnColdStart` call.
