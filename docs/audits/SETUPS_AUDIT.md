# Setups audit (S-FINAL)

The final check of the setup series (S01–S08): one way to integrate Adroit in each app shape (AdMob only, in-app
purchases only, AdMob + in-app purchases), the Firebase-controlled in-app update, and the splash ad that can wait for
the next screen. Read-only: nothing was fixed here. Run on 2026-10-21, on `master` at `3a94ec3`, on a Windows 10 PC
with the Pixel 7a used for the S06/S07 device checks.

**Result: every check PASS.** The one FAIL found (a guide gap) is fixed; the row says how.

**Later change:** `splashAdMode` (`"show"`, `"preload"`, `"later"`) and `SplashState.Finished.showAdLater` were removed
after this audit. A splash without an ad is now the splash ad turned off (`"enabled": false`) or `null`, on the short
timer, and where an ad shows instead is each app's choice ([Splash screen](../SPLASH.md#a-splash-without-an-ad)). The
rows and risks below that name them describe the code as it was audited.

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | No duplication | PASS | `fetchAndActivate` in main sources: only `adroit-remote-config` (`SessionSource.kt`, called from `RemoteConfigSession.kt`). Online check (`NET_CAPABILITY_INTERNET`): only `adroit-remote-config`; none left in `adroit-firebase` or `adroit-billing-firebase`. `registerDefaultNetworkCallback`: `adroit-remote-config` and `adroit-core`'s ad network monitor, which the session can't use (it may depend on no `adroit-*` module). `isPremium =` passed to `SplashFlow`: none in the samples, `docs-snippets` or the docs. |
| 2 | Dependency rules | PASS | `checkModuleDependencies` passes for every module. `releaseRuntimeClasspath` of the IAP-only set (`adroit-billing`, `-billing-views`, `-billing-compose`, `-billing-firebase`, `adroit-update`): no `adroit-core`, `adroit-views`, `adroit-compose`, `adroit-firebase`, `ads-mobile-sdk`, UMP or `play-services-ads`. The only match for "ads" is `play-services-ads-identifier`, brought by Firebase Analytics (see the risks). The AdMob-only set (`adroit-views`, `adroit-compose`, `adroit-firebase`, `adroit-update`): no `adroit-billing*` and no `billingclient`. |
| 3 | API | PASS | `apiCheck` passes. Since `2d94bd0` (the commit before S01): additions in `adroit-core` (`Adroit.isAdFree`, `SplashAdMode`, `SplashTimings.splashAdMode`/`adMode`, `AdSettings.splashAdMode`, `SplashState.Finished.showAdLater`) and two new dumps (`adroit-remote-config`, `adroit-update`). Removed: `RemoteConfigSession` from `adroit-firebase.api`, every line of which is in `adroit-remote-config.api` with the same signature (the documented move); and the hidden default-argument constructors of `AdSettings` and `SplashTimings`, replaced by ones with one more parameter (documented in S03: source-compatible, a rebuild for an already-compiled Kotlin caller). |
| 4 | Tests | PASS | `./gradlew testDebugUnitTest`, 0 failures, 0 skipped: adroit-core 564, adroit-views 52, adroit-compose 30, adroit-testing 14, adroit-firebase 19, adroit-remote-config 9, adroit-update 15, adroit-billing 289, adroit-billing-views 18, adroit-billing-compose 18, adroit-billing-firebase 6, adroit-billing-testing 9, docs-snippets 6, sample-xml 2, sample-compose 3, sample-mixed 1. The working tree held unrelated uncommitted native-ads edits in adroit-core at the time. |
| 5 | Release | PASS | `:sample-xml:assembleRelease` and `:sample-compose:assembleRelease` (R8 on) pass. consumer-smoke after `publishToMavenLocal`: `assembleRelease verifyConsumerClasspath` pass for the Compose host and the View host (`-Psmoke.compose=false`). |
| 6 | Guides | PASS (fixed) | Code: every code block of the six guides quotes a compiled region of `docs-snippets` (`node .github/scripts/check-docs.mjs`: 36 docs, 142 snippets match; `:docs-snippets:assembleDebug` passes). Symbols named only in prose exist with that signature (`ViewGroup.attachAd(AdDefinition, refresh)`, `AdSlotView.load(refresh)`, `AdDefinitions.firebaseTemplate`, `unclaimedResults`, `AdroitBilling.showInAppMessages`, `PaywallLaunch`, `Adroit.privacy.*`, `AdroitUpdate.readyToInstall`). JSON: a scratch test (deleted afterwards) fed every block of step 8 to the library's parsers: the 8 ads and `AdSettings` of the four ads guides through `AdProfiles.parseRemote`, `AdroitBilling` and every paywall of the four billing guides through `BillingGlobalJson` and `PaywallJson`, and `AppUpdate` of all six through `UpdateCoordinator`: no issue, no warning. **Found and fixed:** the IAP-only guides' "Consumables (optional)" told the reader to call `Coins.start(context, scope)` from `Application.onCreate`, but their `IapApp` had no `appScope` to pass. The `guide-iap-app` region now has one (with a `Coins.start(this, appScope)` comment), and both guides say to use it; `:docs-snippets:compileDebugKotlin` and the docs check pass. |
| 7 | Device | PASS | On the Pixel 7a, 2026-10-21: S06 checks 1–7 on sample-xml and S07 checks 1–7 on sample-compose (show, preload, later, paywall skipped, premium, the update check without Play, rotation), with logcat evidence in the S06/S07 reports. No sample or library main source changed after them (`git diff 667a81c HEAD` on those paths is empty: S08 touched docs and `docs-snippets` only). Not re-run during this audit: the phone wasn't connected. |
| 8 | Firebase defaults | PASS | With no `splashAdMode` and no `AppUpdate` parameter in the Firebase project (logcat: `No value … for parameter key 'AppUpdate'`), both samples ran `ad mode SHOW` with the 11 s timer and the flexible update path. Unit tests cover the same with no Firebase at all: `AdroitUpdateTest` (no Play, no Firebase: returns), `UpdateCoordinatorTest` "no key - flexible", `AdProfilesTest` (no `splashAdMode`: `SHOW`) and the unchanged SplashFlow tests. |

## The FAIL and its fix

| Check | Fix (applied after the audit) | Belongs to |
|---|---|---|
| 6: IAP-only guides, consumables | `private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` added to the `guide-iap-app` region (`docs-snippets/.../guides/Apps.kt`), with a `// Consumables (step 7): Coins.start(this, appScope)` line; the consumables paragraph of IAP_COMPOSE.md and IAP_XML.md now says to replace that comment with the call. | S08 |

## Open risks

- **The real in-app update flows are untested on a device.** Debug builds can't receive them. They need two builds on
  Internal App Sharing or the internal testing track (the second with a higher `versionCode`), installed from Play:
  [In-app update: Testing](../APP_UPDATE.md#testing).
- **`splashAdMode` was device-tested through temporary local defaults, not a Firebase publish.** The parse path from
  Firebase is unit-tested and the guides' values parse (check 6); a console publish of `"later"` is the remaining
  end-to-end step.
- **IAP-only apps carry the advertising ID.** Firebase Analytics (from `adroit-billing-firebase`) brings
  `play-services-ads-identifier` and its `AD_ID` permission: declare it in Play's data safety form, or remove the
  permission if the app collects no advertising ID ([Consent](../CONSENT.md#play-data-safety-and-the-advertising-id)).
- **`"preload"` isn't instant on the paywall's close:** the standard loading screen shows for `loadingSeconds` (1 s)
  first. `{ "loadingSeconds": 0 }` on `SplashAd` removes it.
- **A rotation during Google Play's update dialog** keeps the first splash Activity referenced until the check returns
  (the check runs in the ViewModel). LeakCanary may report it; nothing else is affected.
- **The XML paywall loses its plan selection's radio state on rotation** (the card stays outlined). Seen during S06;
  `adroit-billing-views`, outside this series.
- **consumer-smoke's older matrix rows** (AGP 8, Kotlin 2.1) weren't run with `adroit-update` added: they need a local
  Gradle 8.
- **Two default-network callbacks** in an app with ads until the first Remote Config fetch succeeds (the session's is
  then removed). Harmless, and the price of `adroit-remote-config` depending on no `adroit-*` module.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, Play Billing Library 9.1.0, Play In-App Updates 2.1.0, 2026-10-21
