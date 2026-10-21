# Standing rules for every setup prompt (S01–S08, S-FINAL)

This series covers three things:
- One clean way to integrate Adroit in each app shape (AdMob only, IAP only, AdMob + IAP), with no duplicated code or
  state.
- A Firebase-controlled in-app update.
- A splash that can skip showing its ad.

Every prompt starts by telling Claude to read this file. It adds to `../00_WORKING_RULES.md` (ads modules) and
`../billing/B00_WORKING_RULES.md` (billing modules), which still apply to the modules they cover. Where they differ,
this file wins.

## A. Before changing anything
1. Read these first:
   - this file and `README.md` (the roadmap and the decisions below);
   - `docs/architecture/02_ARCHITECTURE.md` §7 (modules and dependency rules) and
     `docs/architecture/06_BILLING_ARCHITECTURE.md` §2;
   - the earlier S-prompts' completion reports, if pasted.
2. Inspect the real repository: `settings.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/convention`, the
   dependency guard (`checkModuleDependencies`), and every module the prompt touches. Follow the structure that
   **exists**. If a file the prompt names moved or was renamed, use the real one and say so.
3. Never assume a dependency version. Check `maven-metadata.xml` on Google Maven or Maven Central, then add it to the
   catalog.

## B. Decisions (made by the user, don't re-open)
4. **Module rules:**
   - Billing never depends on ads, and ads never depend on billing.
   - The only coupling in an app with both is the app's one line:
     `AdroitBilling.entitlements.collect { Adroit.setAdFree(it.adFree) }`.
   - No "bundle" module.
5. **One Remote Config fetch per process.** It lives in the new `adroit-remote-config` module, which has no ads or
   billing code. `adroit-firebase`, `adroit-billing-firebase` and `adroit-update` all use it. No app needs to call
   `RemoteConfigSession.start` (it stays public for a custom Remote Config instance).
6. **One premium source.** `Adroit.isAdFree` is what `SplashFlow` uses by default, so apps don't pass premium twice.
7. **Firebase `AdSettings.splashAdMode`:**
   - `"show"` (default): today's behaviour.
   - `"preload"`: the splash loads the ad, doesn't show it, keeps it cached, and uses the short timer.
   - `"later"`: the splash doesn't request it at all and uses the short timer.

   In both new modes, `SplashState.Finished.showAdLater = true`. The app then shows the same enum ad with
   `AdSlots.showFullScreen` on the next screen, for example on the paywall's X. With no ad cached yet, that call
   loads it with the loading screen.
8. **Firebase `AppUpdate`:** `{"type": "flexible"|"force"|"off", "forceBelowVersionCode": 0}`.
   - The default is `flexible`.
   - The update is forced when `type == force`, or when `forceBelowVersionCode > 0` and the installed versionCode is
     below it.
   - If the user backs out of a forced update, the app closes (`finishAffinity`). The next launch asks again.
   - In flexible mode the user can decline and continue.
9. **Integration guides:** six guides, in `docs/guides/`, one per {AdMob only, IAP only, AdMob + IAP} ×
   {Compose + Kotlin, XML + Kotlin}. AdMob + IAP is the main one. The old `NEW_APP_*` and `BILLING_NEW_APP_*` guides
   are merged into these and deleted.

## C. While changing
10. **Package:** new public classes keep the package of the area they belong to.
    - `RemoteConfigSession` stays `io.github.thesobiaakbar.adroit.firebase`, so the public API doesn't move.
    - The update module is `io.github.thesobiaakbar.adroit.update`.
    - Explicit API mode, `internal` by default, KDoc on every public declaration.
11. **No Firebase or Play Core types in public signatures**, except `FirebaseRemoteConfig` where it is already
    public today.
12. **Seams for tests:** Play and Firebase calls go behind a small internal port, so the logic gets plain JVM tests
    with a fake, the same way `SplashPorts` and `RemoteConfigSource` work today.
13. Any Activity passed in is used only for the call. It is never stored in a field, a static, or a lambda that
    outlives the call.
14. **Smallest coherent change:**
    - Delete code that the new shared piece replaces; don't leave a second copy behind.
    - Don't refactor unrelated code.
    - Comment density matches the surrounding code.

## D. After changing
15. Run, in this order, for the touched modules:
    - `build`
    - `testDebugUnitTest`
    - `lint`
    - `apiDump` (review the diff), then `apiCheck`
    - `checkModuleDependencies`

    Library modules use `allWarningsAsErrors`.
16. If you can't run a command (for example no device, no Play-installed build), say so and give the exact command
    or steps for the user. **Never report a test as passing that you didn't run and observe.**
17. Review your own diff before the report. Don't commit unless asked. The commit message is
    `feat(setup): S<NN> <title>`.

## E. Completion report (always)
1. Summary (3–6 bullets).
2. Files created, modified and deleted.
3. Public API diff (`.api`), or "none".
4. Commands run, with pass/fail counts.
5. Acceptance checklist, each item ✅/❌ with a one-line reason.
6. Deviations and why.
7. Open issues and risks.

The stage is DONE only when every acceptance item is ✅.
