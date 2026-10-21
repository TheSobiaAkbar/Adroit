# S04 — adroit-update: in-app update controlled from Firebase

Read `docs/implementation-prompts/setup/S00_WORKING_RULES.md` first and follow it.

## Objective
At app start, check Google Play for a newer version and act on the Firebase `AppUpdate` setting:
- **flexible** (default): Google Play's update prompt. The user may decline and continue. The update downloads in
  the background, and the app offers "Restart to update".
- **force**: Google Play's full-screen update. The user can't continue without updating. Backing out closes the app,
  and the next launch asks again.
- **off**: no check.

It works the same in AdMob-only, IAP-only and AdMob + IAP apps.

## Firebase key `AppUpdate` (JSON)
```json
{ "type": "flexible", "forceBelowVersionCode": 0 }
```
| Field | Values | Default | Meaning |
|---|---|---|---|
| `type` | `flexible`, `force`, `off` (case-insensitive) | `flexible` | How updates are offered. |
| `forceBelowVersionCode` | integer ≥ 0 | 0 (unused) | If > 0 and the installed versionCode is below it, the update is forced even when `type` is `flexible`. |

An unknown or malformed value is ignored with a warning, and the default applies.

## Module
- **Module:** new `adroit-update`, package `io.github.thesobiaakbar.adroit.update`, explicit API.
- **Dependencies:**
  - `com.google.android.play:app-update`: check the latest version on Google Maven and add it to the catalog;
  - `api(project(":adroit-remote-config"))`;
  - coroutines.
- **Must not depend on:** any ads or billing module.
- **Add to:** `settings.gradle.kts` and the dependency guard.
- **Check every Play API you call** against `https://developer.android.com/guide/playcore/in-app-updates/kotlin-java`
  and the `AppUpdateManager` reference. If the real API differs from this prompt (for example the `startUpdateFlow`
  return type), use the real API, keep the behaviour, and report the difference.

## Public API
```kotlin
public object AdroitUpdate {
    /** Suspends until the app may continue. Never returns when a forced update was declined (the app closes). */
    public suspend fun check(activity: Activity, default: UpdateType = UpdateType.FLEXIBLE)
    /** True once a flexible update finished downloading. */
    public val readyToInstall: StateFlow<Boolean>
    /** Installs a downloaded flexible update (Play restarts the app). */
    public fun install()
}
public enum class UpdateType { FLEXIBLE, FORCE, OFF }
```
Apps without Firebase pass `default`. With Firebase, the `AppUpdate` key overrides it.

## Behaviour of `check`
1. In parallel:
   - `appUpdateInfo` from Play;
   - wait up to **2 s** for `RemoteConfigSession.startDefault()?.isFetched` to turn true. On timeout, use the active
     values (the last session's fetch, or the default).
2. Resolve the type:
   - `OFF` → return.
   - `FORCE` when `type == force`, or when `forceBelowVersionCode > 0 && installedVersionCode < it`. Read
     `installedVersionCode` with `PackageInfoCompat.getLongVersionCode`.
   - Otherwise `FLEXIBLE`.
3. Return at once, without crashing, when:
   - Play is unavailable or `appUpdateInfo` fails (sideloaded or debug build, offline);
   - the result is `UPDATE_NOT_AVAILABLE`.
4. **Force:**
   - `UPDATE_AVAILABLE` with IMMEDIATE allowed, or `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS` (resume) → call
     `startUpdateFlow(info, activity, AppUpdateOptions.defaultOptions(IMMEDIATE))`. It returns a `Task<Int>`, so no
     ActivityResult launcher is needed.
   - A result other than `RESULT_OK` → `activity.finishAffinity()`, and `check` never resumes.
   - IMMEDIATE not allowed → log a warning and fall through to flexible. If that isn't allowed either, return.
5. **Flexible:**
   - If the install status is already `DOWNLOADED` (from an earlier session) → `completeUpdate()`.
   - Else, if `UPDATE_AVAILABLE` and FLEXIBLE is allowed → `startUpdateFlow(FLEXIBLE)`, then return whatever the
     user chose.
   - Register one `InstallStateUpdatedListener` (process-wide, idempotent) that sets `readyToInstall = true` on
     `DOWNLOADED` and unregisters on `INSTALLED`, `FAILED` or `CANCELED`.
6. Main thread. The Activity is used only during the call (S00 rule 13). If the Activity is destroyed mid-check, the
   check is cancelled with no crash.

## Seam
- An internal `UpdatePort` wraps `AppUpdateManager`: `info()`, `startFlow(activity, immediate): Int`,
  `installStatus` and `complete()`. `PlayUpdatePort` is the real one.
- The resolution and flow logic (an internal `UpdateCoordinator`) takes the port, a config reader
  (`(String) -> String?`), the installed version code and a clock or timeout. All of it is JVM-testable.

## Tests (JVM, fake port, fake config)
- No key → flexible; `"off"` → no Play call; `"FORCE"` → immediate.
- `forceBelowVersionCode` above, equal to and below the installed version.
- Force cancelled → `finishAffinity` called, `check` never returns.
- Force accepted (`RESULT_OK`) → no finish.
- Force with IMMEDIATE not allowed → flexible path.
- Flexible declined → returns; flexible accepted → returns; listener `DOWNLOADED` → `readyToInstall` is true.
- An already-`DOWNLOADED` status at check → `complete()`.
- `info()` throws or times out → returns, no crash.
- Remote Config not fetched within 2 s → active values used, and `check` takes ≤ 2 s plus Play time.
- Malformed JSON → default with a warning.

## Docs
- `docs/APP_UPDATE.md`: what each type does for the user, the JSON table, the snippet below, and how to test (Play
  only: Internal App Sharing or internal track with a higher versionCode).
- One line in `docs/REMOTE_CONFIG.md` and `CHANGELOG.md`. The guides come in S08.

```kotlin
// SplashActivity.onCreate
lifecycleScope.launch {
    AdroitUpdate.check(this@SplashActivity)
    viewModel.flow.start(this@SplashActivity)   // or, IAP-only: route to paywall/main
}
```

## Verify
```
./gradlew :adroit-update:build :adroit-update:testDebugUnitTest :adroit-update:apiDump :adroit-update:apiCheck checkModuleDependencies
```

## Acceptance criteria
- [ ] No Play Core or Firebase type in the public API.
- [ ] `adroit-update` resolves no ads or billing module and no Mobile Ads SDK.
- [ ] Every behaviour bullet above has a test, and all of them pass.
- [ ] A debug build without Play (emulator, sideloaded) passes `check` at once, with no crash.

## Report
S00 §E. List the `app-update` version used and where it was verified.
