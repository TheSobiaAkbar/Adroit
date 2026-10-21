# S06 — sample-xml: the AdMob + IAP reference app

Read `docs/implementation-prompts/setup/S00_WORKING_RULES.md` first and follow it.

## Objective
Make `sample-xml` the reference for the main setup, AdMob + IAP, with the S01–S04 features:
- an update check on the splash;
- Firebase-driven splash ad modes routed through the Onboarding paywall;
- the update snackbar;
- no duplicated premium state.

The S08 guide `docs/guides/ADS_IAP_XML.md` copies this code.

## Context
- `SampleApplication.kt`: `Adroit.initialize`, `AdroitBilling.initialize` and the bridge line. S01 removed
  `RemoteConfigSession.start`.
- `SplashActivity.kt`: `render()` and `goToMain()`. `SplashViewModel.kt` has no `isPremium` argument since S02.
- `MainActivity.kt` currently opens `AdroitPaywall.show(this, SamplePaywalls.Onboarding, "onboarding")`. Move that
  decision to the splash routing.
- The ad enum is `ads/SampleAds.kt` (`SplashAd`). The billing enums are in `billing/SampleBilling.kt`.

## Changes
1. **Update check:** in `SplashActivity`, call `AdroitUpdate.check(this)` before `viewModel.flow.start(this)`.
   - It runs once per ViewModel. A rotation must not restart it: keep a flag or job in the ViewModel.
   - Add `implementation(project(":adroit-update"))`.
2. **Routing** on `SplashState.Finished`:
   - **Onboarding paywall wanted** (`AdroitBilling.paywalls.shouldShow(SamplePaywalls.Onboarding, "onboarding")`):
     open it with `AdroitPaywall.contract()`/launcher, so the splash learns when it closes. When it closes:
     - if `state.showAdLater`: `AdSlots.showFullScreen(this, SampleAds.SplashAd) { goToMain() }`;
     - else `goToMain()`.
   - **Not wanted:**
     - if `showAdLater`: show the ad, then `goToMain()`;
     - else `goToMain()`.
   - Keep the splash alive (not finished) until the routing ends, and handle a rotation while the paywall is open.
   - Remove the paywall call from `MainActivity`.
3. **Snackbar:** in `MainActivity`, collect `AdroitUpdate.readyToInstall` and show a Snackbar with the text
   "An update is ready" and the action "Restart", which calls `AdroitUpdate.install()`. Add the strings to
   resources.
4. **Firebase template:** in `sample-xml/firebase/remote_config_template.json`:
   - add `"AppUpdate": {"type": "flexible", "forceBelowVersionCode": 0}`;
   - add `"splashAdMode": "show"` to `AdSettings`.

   `docs/firebase/sample_billing_remote_config.json` stays billing-only. Note in both files that the keys share one
   Firebase project.
5. **Developer screen:** if the sample has a debug or diagnostics screen, show the current `splashAdMode` and
   `AppUpdate` type there. Skip this if no such screen exists.

## Device checks (Pixel 7a via adb; see the memory notes for the adb path and etiquette)
Use the local defaults or a Firebase publish for each mode (ask the user to publish in the console):
1. **Default (`show`):** the splash shows the ad as before, then the Onboarding paywall, then main.
2. **`preload`:** a short splash, then the paywall. On X, the ad shows at once (no loading screen), then main.
3. **`later`:** a short splash, then the paywall. On X, a loading screen, then the ad, then main.
4. **Paywall skipped** (`maxPerSession` reached or `enabled: false`) with `preload`: the ad shows right after the
   splash, then main.
5. **Premium** (demo purchase): no consent, no ad, the premium timer, no ad on X.
6. **Update:** a debug build passes `check` at once. Real flows wait for a Play-installed build (user step;
   `docs/APP_UPDATE.md`).
7. **Rotation:** during the splash and while the paywall is open, nothing restarts and there's no double ad.

## Verify
```
./gradlew :sample-xml:assembleDebug :sample-xml:lint
```
Install, run checks 1–7, and capture the logcat lines that prove each one.

## Acceptance criteria
- [ ] Each of device checks 1–5 and 7 passes, observed on the device (or is reported as not run, with the reason).
- [ ] `MainActivity` no longer decides the launch paywall.
- [ ] No `isPremium` argument and no `RemoteConfigSession.start` in the sample.

## Report
S00 §E, plus the logcat evidence per check.
