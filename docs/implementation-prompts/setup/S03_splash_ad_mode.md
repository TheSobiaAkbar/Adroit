# S03 — Splash ad mode from Firebase: show, preload, or later

Read `docs/implementation-prompts/setup/S00_WORKING_RULES.md` first and follow it.

## Objective
Sometimes the splash should not show its ad. The user goes to another screen first (usually the IAP paywall), and
the ad shows when they close that screen. Everything else on the splash still runs: consent, the SDK start, the
update check (S04), and routing.

Firebase chooses the mode with the new `AdSettings.splashAdMode` field:

| Value | Splash loads the ad? | Splash shows it? | Splash timer | `Finished.showAdLater` |
|---|---|---|---|---|
| `"show"` (default) | yes | yes, as today | `splashSeconds` | false |
| `"preload"` | yes, and keeps it cached | no | `premiumSplashSeconds` (short), ends early when the ad is ready | true |
| `"later"` | no | no | `premiumSplashSeconds` | true |

The app shows the ad later with the existing call `AdSlots.showFullScreen(activity, SplashAd) { … }`:
- with `preload`, the ad is usually cached, so it shows at once;
- with `later`, or a preload that isn't ready yet, the existing load path runs: the loading screen, up to
  `loadTimeoutSeconds`.

Add no new show API.

## Context
- `adroit-core/.../splash/SplashFlow.kt` `run()`:
  - **Request:** `wantsAd = … ports.requestAd(key)`, which uses `engine.preload`.
  - **Loop:** the timer loop ends early when the ad is ready.
  - **No ad:** `ports.abandonAd(key)` drops the load, the retries and the cached ad.
  - **Show:** `ports.showAd`.
- Timings come from `ports.timings()`, read after consent: `SplashTimings` (`config/SplashTimings.kt`), parsed from
  Firebase `AdSettings` in `internal/config/AdProfiles.kt`. It also has a code default in `AdSettings`
  (`config/AdDefinition.kt`).
- A `splash = true` profile can be shown through `AdSlots.showFullScreen`, which takes the non-splash path
  (`AdSlots.kt` `show(..., splash = false)`). Confirm that its frequency counter and caps don't block a first show,
  and report it.
- `FullScreenAdEngine.reloadAfterShow` doesn't reload a splash ad after it is shown. That's fine: one show per
  launch.

## Files
1. `SplashTimings`: add `splashAdMode: SplashAdMode?`, merged in `orElse`. Add a public
   `enum class SplashAdMode { SHOW, PRELOAD, LATER }` in `config`.
2. The `AdSettings` code default: add a `splashAdMode` parameter (default `SHOW`).
3. Firebase parsing:
   - `"show"`, `"preload"` and `"later"` are case-insensitive;
   - an unknown value is ignored with a warning (existing pattern) and the code value applies.
4. `SplashFlow.run()`:
   - `PRELOAD`: request the ad, use the `premiumSplash` duration, break early when the ad is ready, **never show**,
     **don't call `abandonAd`**, and finish with `Finished(adShown = false, showAdLater = true)`.
   - `LATER`: don't request, use the `premiumSplash` duration, `Finished(false, showAdLater = true)`.
   - Premium or offline users: unchanged. `showAdLater = false`, since there is no ad for them.
   - `splashAd == null`: `showAdLater = false`.
5. `SplashState.Finished`: add `showAdLater: Boolean`. Keep the existing one-argument constructor working
   (`@JvmOverloads` or a secondary constructor), and update `equals`, `hashCode` and `toString`.
6. `docs/SPLASH.md`:
   - the mode table above;
   - the app routing snippet (below);
   - when to use each mode.
7. `docs/AD_PROFILES.md` / `docs/REMOTE_CONFIG.md`: the `AdSettings` field row.
8. `sample-xml/firebase/remote_config_template.json`: add `"splashAdMode": "show"`.
9. `CHANGELOG.md`.

## App routing (document; the samples adopt it in S06/S07)
```kotlin
is SplashState.Finished -> when {
    paywallWanted -> openPaywall(onClose = { showAdThen(state.showAdLater) { goMain() } })
    else -> showAdThen(state.showAdLater) { goMain() }
}
// showAdThen: if (later) AdSlots.showFullScreen(activity, AppAds.SplashAd) { next() } else next()
```

## Tests (extend the existing SplashFlow tests with the fake ports)
- `SHOW`: every existing case passes unchanged.
- `PRELOAD`:
  - requests the ad;
  - never calls `showAd` or `abandonAd`;
  - ends early when the ad is ready, at the latest after `premiumSplash`;
  - `showAdLater = true`.
- `LATER`: never calls `requestAd`; `premiumSplash` duration; `showAdLater = true`.
- Premium in `PRELOAD` mode: `showAdLater = false`, no request.
- Parsing: valid values, mixed case, an unknown value falls back.

## Verify
```
./gradlew :adroit-core:testDebugUnitTest :adroit-core:apiDump :adroit-core:apiCheck
```

## Acceptance criteria
- [ ] With no `splashAdMode` anywhere, behaviour is identical to before (existing tests untouched).
- [ ] `PRELOAD` leaves the ad cached: a later `AdSlots.showFullScreen` on the same enum shows it without a new load.
      Covered by a test, or reported for device check in S06.
- [ ] `LATER` makes no ad request during the splash.
- [ ] The `.api` diff is additions only.

## Report
S00 §E.
