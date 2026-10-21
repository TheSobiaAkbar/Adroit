# S07 — sample-compose: parity with sample-xml

Read `docs/implementation-prompts/setup/S00_WORKING_RULES.md` first and follow it, and read S06 and its report.

## Objective
Port S06 to `sample-compose`, Compose style. `docs/guides/ADS_IAP_COMPOSE.md` (S08) copies this code.

## Context
- `SplashActivity.kt` shows a `SplashScreen(state)` composable and navigates on `Finished`.
- `MainActivity.kt` checks `shouldShow`, and `ui/SampleApp.kt` then navigates to the paywall route. Move that
  decision to the splash routing.

## Changes
1. **Update check:** `AdroitUpdate.check(this)` in `SplashActivity` before the flow starts, once per ViewModel
   (rotation-safe). Add `implementation(project(":adroit-update"))`.
2. **Routing** on `Finished`, the same rules as S06 §2:
   - the paywall shows as the `Paywall` / `PaywallScreen` composable (the existing route, or a screen in the splash
     host), whichever keeps one Activity for splash → paywall → main;
   - on close: `if (showAdLater) AdSlots.showFullScreen(activity, SplashAd) { navigate main }`, else navigate to
     main.
   - Remove the old start-time paywall decision in `MainActivity` / `SampleApp`.
3. **Snackbar:** in the main scaffold, a Snackbar for `AdroitUpdate.readyToInstall` with the action "Restart", which
   calls `install()`.
4. **Firebase template:** if `sample-compose` has its own, apply the same template changes as S06 §4.

## Device checks
Run S06 checks 1–7 on `sample-compose`.

## Verify
```
./gradlew :sample-compose:assembleDebug :sample-compose:lint
```

## Acceptance criteria
- [ ] Each S06 device check passes on sample-compose (or is reported as not run, with the reason).
- [ ] The behaviour matches sample-xml screen for screen.
- [ ] No `isPremium` argument and no `RemoteConfigSession.start`.

## Report
S00 §E, plus the logcat evidence per check.
