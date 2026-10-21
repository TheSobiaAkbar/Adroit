# Billing production-readiness audit

B-FINAL, run on 2026-10-21 against `master` at `a8c6350` (B24, version `1.0.0-rc6`). Scope: the five billing modules
(`adroit-billing`, `-views`, `-compose`, `-firebase`, `-testing`), the billing parts of both samples, the billing
guides and `docs-snippets`. Source of truth: `docs/architecture/06_BILLING_ARCHITECTURE.md` and
`docs/implementation-prompts/billing/B00_WORKING_RULES.md`.

The working tree also held another session's uncommitted `adroit-core` change (native pools on test ad units). The
full build below compiled and tested it along with everything else; it touches no billing code.

## Result

| | Count |
|---|---|
| PASS | 10 (checks 1–8, 11 and 12; 7 carries WARN-1) |
| PASS after a fix in this audit | 2 (checks 9 and 10; 10 carries WARN-2) |
| WARN (owner and follow-up below) | 2 |
| Open FAIL | 0 |

## Checks

"B" is `adroit-billing/src/main/kotlin/io/github/thesobiaakbar/adroit/billing`, and "BT" is
`adroit-billing/src/test/kotlin/io/github/thesobiaakbar/adroit/billing`. Test names are quoted as in the sources.

| # | Check | Status | Evidence | Fix |
|---|---|---|---|---|
| 1 | Play API currency | PASS | `playBilling = "9.1.0"` (`gradle/libs.versions.toml:28`). Google's release notes (read 2026-10-21): 9.1.0 (2026-06-18) is the latest; 9.0.0, 8.3.0 and 8.2.1 before it. Play's deprecation page: version 9 is accepted for new apps and updates until 2028-08-31 (extension 2028-11-01); today's floor is 8 (2026-08-31). A grep of every billing `src/main` for `queryPurchaseHistory`, `querySkuDetails`, `SkuDetails`, no-arg `enablePendingPurchases()`, `queryPurchasesAsync("…`, `setSubscriptionReplacementMode` and the deprecated external-offer APIs finds nothing. Used instead: `enablePendingPurchases(PendingPurchasesParams…enableOneTimeProducts().enablePrepaidPlans())` (`B/internal/play/PlayBillingAdapter.kt:51`) and `SubscriptionProductReplacementParams.setReplacementMode` (`:106-109`). | — |
| 2 | Purchases | PASS | Consumable, non-consumable, subscription: `PlayMappersTest` "a subscription maps every base plan, offer and phase", "a non-consumable maps each purchase option", "a consumable in the single-offer shape maps to one option". Pending: `PurchaseProcessorTest:89` "a pending purchase grants nothing until Play says it is purchased", `PurchaseLauncherTest:292`. Suspended: `PurchaseProcessorTest:119`, `EntitlementEngineTest:150`, `:159`. Unspecified ignored: `PurchaseProcessorTest:127` (code: `B/internal/purchase/PurchaseProcessor.kt:66-70`). Acknowledge: `PurchaseProcessorTest:134`, `:186` (retried with backoff), `:196` (three failures reported, retried on the next sync, no second grant), `ResilienceTest:106`. Consume after confirm: `PurchaseProcessorTest:244`, `:272`, `:283`, `ResilienceTest:127`. Concurrency and idempotency: `PurchaseProcessorTest:211` "the listener and a sync delivering the same purchase together give one grant and one acknowledge", `:225`, `PurchaseLauncherTest:272`, `ResilienceTest:87`, `:229`. | — |
| 3 | Subscriptions | PASS | Several products and base plans: `PaywallResolverTest:161` "the same product twice with different base plans"; both samples sell weekly, monthly and yearly plans plus lifetime. Durations are Play's ISO-8601 periods, parsed (`B/ProductInfo.kt:177`); a grep for fixed day counts finds only the calendar constant `DAYS_PER_YEAR = 365.2425` used to compare periods (`B/ProductInfo.kt:173`) and the made-up DEMO catalog (`B/internal/play/DemoBillingPort.kt:143-150`). Upgrade/downgrade: `PurchaseLauncherTest:372`, `:383`, `:394`, `:409`, `:428`. Manage link: `AdroitBillingTest:86`, `:98`. In-app messages: `B/AdroitBilling.kt:174`, `AdroitBillingTest:107`. | — |
| 4 | Trials and offers | PASS | No unavailable trial is advertised: `PaywallResolverTest:168` "no plan in any model advertises a trial or intro its offer doesn't have", `:60`, `:78`, `:116`, `:134`; `PurchaseLauncherTest:206` "a missing offer fails without launching and is never substituted"; `DefaultOfferSelectorTest:44`. Prices and terms come only from Play: `PriceTextTest:113`. Fallbacks documented: `docs/PAYWALL_REMOTE_CONFIG.md:154` and `:192` (`onOfferUnavailable`: `BASE_PLAN`, `HIDE_PLAN`, `HIDE_PAYWALL`). | — |
| 5 | Firebase | PASS | Every paywall field is remote: `RemoteConfigDocTest` compares the parser's keys with the doc's tables (2/2 pass). Close delay: `PaywallJsonTest:94` "the close delay accepts -1 to 60 and clamps larger values"; rendered 0/3/−1 in `PaywallTest:88`, `:98`, `:111` (Compose) and `AdroitPaywallActivityTest:101`, `:115`, `:132` (Views). Malformed config: `PaywallJsonTest:65`, `:71`, `:114`, `EntitlementEngineTest:231`. One fetch: `FirebaseBillingConfigTest:71` "at most one fetchAndActivate per process, and the caller never waits", `:84`. No entitlements: `EntitlementEngineTest:213` "the remote mapping remaps declared products and can't add any", `:224` "grants nothing without a purchase". No prices: the config parsers (`B/internal/config/`) read no price, micros or currency key. | — |
| 6 | Attribution | PASS | Placement is its own field next to paywall, campaign, product, base plan and offer (`B/Attribution.kt:20-28`). Rotation: `AdroitPaywallActivityTest:201`, `PaywallTest:258`. Process death: `AttributionTest:32` (round trip through SharedPreferences), `ResilienceTest:87` "killed while Play's sheet is open - the next process grants once, acknowledges, and keeps the placement". Unknown: `AttributionTest:64` "a purchase matching no attempt is unknown, never another product's placement" (`Attribution.UNKNOWN_PLACEMENT`, `B/Attribution.kt:33`). | — |
| 7 | Security and privacy | PASS, WARN-1 | Tokens: `TokenLeakScanTest` (scans every `src/main` for unredacted tokens), `PurchaseProcessorTest:323` "no purchase token reaches the logs, the events or the ledger", `PlayMappersTest:74`, `AttributionTest:78`, `EntitlementEngineTest:440`; correlation uses `redact()` = `sha256(token).take(12)` (`B/internal/BillingLog.kt:35`); the account ID only as a hash (`PurchaseProcessorTest:134`). Secrets: `git grep` for `private_key`, `service_account`, `client_secret`, private-key headers and `AIza…` keys finds only the warnings in `docs/BILLING_BACKEND.md` and the two samples' `google-services.json` Firebase client keys (WARN-1). HTTPS only: `B/HttpPurchaseVerifier.kt:121`, `HttpPurchaseVerifierTest:175`. Client limits documented: `docs/BILLING.md:61` "What the app can and can't know" and the security box at `:86`. No grant on failure or pending: `PurchaseProcessorTest:89`, `:152`, `EntitlementSnapshotTest:26`, `:34`, `EntitlementEngineTest:132`. See `SECURITY_PRIVACY_AUDIT.md`, "Billing". | — |
| 8 | API stability | PASS | Explicit API mode and `allWarningsAsErrors` for every library (`build-logic/…/ProjectExtensions.kt:51`, `:58`). `apiCheck` passes against the five committed dumps. No Play type public: `NoGoogleTypesInPublicApiTest` reads all ten dumps for `com/android/billingclient`. Firebase types appear only in `adroit-billing-firebase.api` (`FirebaseBillingConfig.source`, `FirebaseBillingEvents`), the allowed exception (ADR-017); the guard keeps the Firebase SDK off the other billing modules. Experimental items carry `@ExperimentalAdroitBillingApi` (`B/ExperimentalAdroitBillingApi.kt:7`, opt-in WARNING). See `API_STABILITY_AUDIT.md`, "Billing". | — |
| 9 | Modularity | **FAIL → fixed** | Billing → ads: every billing module forbids the ads modules and the Mobile Ads SDK (`BILLING_NEVER`), and the XML module forbids Compose (only `runtime-annotation`, via androidx.activity). `adroit-billing`'s release runtime classpath has no Compose, Firebase SDK or ads (only Play Billing's `firebase-encoders*`). **Found:** nothing stopped an ads module from depending on billing; an ads-only app pulled no billing only because no one had added it. **Fixed:** `ADS_NEVER` (`project :adroit-billing.*`, `com.android.billingclient:.*`) on all five ads modules. Proved by a probe: `implementation(project(":adroit-billing"))` added to `adroit-testing` failed `checkModuleDependencies` with both lines, and was reverted. | `build-logic/…/DependencyGuard.kt` |
| 10 | UX and accessibility | **FAIL → fixed**, WARN-2 | **Found on the device:** sample-compose's `FeedTest.homeKeepsItsBannerAcrossTabsAndRotation` failed LeakCanary: a destroyed `MainActivity` was held by `BillingRuntime.showInAppMessages`'s coroutine, which captured the Activity while it waited for a connection and then for Play's callback, and Play keeps that callback in a native global until it answers (against B00 rule 12). **Fixed:** the Activity is held in a `WeakReference` and a destroyed one is skipped (`B/internal/BillingRuntime.kt`, `showInAppMessages`). New test `AdroitBillingTest` "waiting for Play's in-app message answer doesn't keep a destroyed activity" (a fake Play that never answers): it fails without the fix and passes with it; the device test passes after it. Disclosure: always under the purchase button in the built-in templates (Views: required id `adroit_billing_disclosure`, `PaywallBinder.kt:65`; Compose: `Paywall.kt:274`); `PaywallTest:142`, `AdroitPaywallActivityTest:178`. Escape hatch on error: loading and unavailable states always draw a close button (`Paywall.kt` `AlwaysClose`), `PaywallTest:209` "a paywall that can't load can always be closed", `AdroitPaywallActivityTest:230`. TalkBack: plans read as radio buttons, the close button is last (Compose `traversalIndex = 1f`; Views: included last in each layout) and hidden while counting down (`PaywallBinder.kt:170-187`). 48dp: `PaywallTest:156`, `adroit_billing_close_button.xml`. RTL and font scale 2.0: `PaywallConfigurationTest` in both UI modules (`ar`, `fontScale = 2.0f`, every template; no text cut off or off screen). | — |
| 11 | Samples and docs | PASS | Parity: the two `SampleBilling.kt` files define the same products and paywalls; a diff differs only in comments. Guides followed end to end in B23: a scratch app on AGP 9.1.1 built both the XML and the Compose guide from `mavenLocal()` and showed the Onboarding paywall on a Pixel 7a. Snippets: `./gradlew -PincludeDocsSnippets :docs-snippets:build` passes, and `check-docs.mjs` prints "Docs OK: 33 files, 91 snippets match their compiled sources" (after fixing a dead CHANGELOG link to the deleted `docs/ACCESS.md`). Device matrix: every billing device test passes; the one ads test that kept failing, sample-xml's `StartupSmokeTest`, exposed a splash bug fixed during the audit ("Device matrix" below). | `CHANGELOG.md` link |
| 12 | Tests | PASS | Counts from the run below, 0 failures, 0 skipped: adroit-billing 289 (288 in the full run, plus the leak test), adroit-billing-views 18, adroit-billing-compose 18, adroit-billing-firebase 11, adroit-billing-testing 9, docs-snippets 6. The rest of the build: adroit-core 554, adroit-views 52, adroit-compose 30, adroit-testing 14, adroit-firebase 26, sample-compose 3 (sample-xml has no unit tests). | — |

## Device matrix

Pixel 7a, Android 17 (API 37), unlocked, stay-awake while charging, demo billing. sample-xml runs
`connectedMinifiedDebugAndroidTest` (its test build type), sample-compose `connectedDebugAndroidTest`.

| Run | sample-xml (9 tests) | sample-compose (4 tests) |
|---|---|---|
| 1, before the fix | 8 pass; `AdProfilesDeviceTest` failed (`not_initialized` within 20 s) | 3 pass; `FeedTest.homeKeepsItsBannerAcrossTabsAndRotation` failed: the billing leak in check 10 |
| 2, after the fix | 6 pass; `StartupSmokeTest`, `FullScreenLeakTest` ("MainActivity wasn't resumed") and `FeedScrollLeakTest` (list not found) failed | **4 pass** |
| 3, the three failed XML tests again | `FeedScrollLeakTest` passes; `StartupSmokeTest` and `FullScreenLeakTest` fail again | — |
| 4, whole XML suite, phone left alone | **8 pass**; `StartupSmokeTest` fails (the splash bug below) | — |
| 5, `StartupSmokeTest` alone on a fresh install (`am instrument`) | fails the same way | — |
| 6, after the splash fix and the new `StartupSmokeTest` | **9 pass** | **4 pass** |

Billing on the device: `PaywallLeakTest.openingAndClosingEveryPaywallLeavesNoLeaks` passed in every run of both
samples, and `ReleaseSmokeTest` (the minified XML app) passed. Runs 1 to 3 overlapped with the phone being used (WhatsApp and the Recents gesture in logcat), which explains the
scattered ads failures. Runs 4 and 5 isolated a real ads bug, found by streaming only the Adroit tags while the test
ran (the Mobile Ads SDK's network logging otherwise pushes the early lines out of the buffer):

- The splash requested `SplashAd` as an app open ad (the values active from the last session), and 0.3 s later this
  session's Firebase fetch swapped it back to an interstitial. The swap releases the app open placement, and nothing
  requested the interstitial, so `isAdReady` stayed false for the whole 11 s timer and no ad showed.
- **Fixed** in `adroit-core`'s `SplashFlow`: while the timer runs it watches the format the ad is served in
  (`SplashPorts.servingFormat`) and requests the ad again after a swap. New test `SplashFlowTest` "a format swap
  during the timer requests the ad again in its new format, and shows it" fails without the fix. On the device the
  interstitial is then requested, loads and shows.
- `StartupSmokeTest` allowed a fixed 10 s, shorter than the sample's 11 s splash timer (`splashSeconds = 11` since
  PROMPT 28E), and it never closed a splash ad that did show. It now closes the ad when one shows and allows the
  sample's `splashSeconds` plus 4 s for consent and SDK init (`splashReachesMainWithinItsTimer`).

## Warnings

- **WARN-1, Firebase client keys in the samples.** `sample-xml/google-services.json` and
  `sample-compose/google-services.json` (added in PROMPT 28A for the ads samples) hold the sample Firebase project's
  Android API key. Firebase says these identify the project and aren't secrets, and no billing code reads them. Owner:
  Sobia Akbar (repository owner). Follow-up: confirm in Google Cloud Console that the key is restricted to the two
  sample package names and their signing certificates, and to the Firebase APIs they use.
- **WARN-2, a custom Compose layout can leave out the terms or the X.** `Paywall(…, content = { … })` replaces the
  whole layout, so an app that skips `Disclosure()` or `CloseButton()` shows no renewal terms or no X. The built-in
  templates always have both, the error states always have an X, and the guide says to keep `Disclosure()`
  (the Compose billing guide of the time, now section 7 of `docs/guides/IAP_COMPOSE.md`); the `content` KDoc now says so too. Owner: Sobia Akbar. Follow-up: decide
  whether a debug build should warn when a custom layout composes no disclosure.

## Changes made during the audit

- `adroit-billing/…/internal/BillingRuntime.kt`: `showInAppMessages` holds the Activity weakly (check 10), with a
  regression test in `AdroitBillingTest` and a `FakePlayBillingPort.inAppMessageAnswer` hook for it.
- `build-logic/…/DependencyGuard.kt`: `ADS_NEVER` on the ads modules (check 9).
- `adroit-core/…/splash/SplashFlow.kt`: requests the splash ad again after a format swap during the timer, with a
  `SplashFlowTest` case; `docs/SPLASH.md` says so, and `CHANGELOG.md` has it under "Fixed".
- `sample-xml/…/StartupSmokeTest.kt`: closes a splash ad and allows the sample's splash timer.
- `adroit-billing-compose/…/Paywall.kt`: the `content` KDoc names the blocks a custom layout must keep (WARN-2). No
  API change.
- `CHANGELOG.md`: the rc4 entry's "Getting access" link points at `docs/ACCESS.md` in the `v1.0.0-rc4` tag.
- `SECURITY_PRIVACY_AUDIT.md` and `API_STABILITY_AUDIT.md`: a "Billing" section each.

## Validation

| Command | Result |
|---|---|
| `./gradlew clean build testDebugUnitTest lint apiCheck checkModuleDependencies --continue` | BUILD SUCCESSFUL in 1 h 36 min (1,328 tasks); counts in check 12 |
| `./gradlew :adroit-billing:build :adroit-billing-compose:testDebugUnitTest :adroit-billing-views:testDebugUnitTest checkModuleDependencies` after the fixes | BUILD SUCCESSFUL; 289 + 18 + 18 tests, 0 failures |
| Device matrix, six runs | see "Device matrix"; the last: sample-xml 9/9, sample-compose 4/4 |
| `./gradlew :adroit-core:testDebugUnitTest :adroit-core:apiCheck :adroit-core:lint :sample-xml:lint` after the splash fix | BUILD SUCCESSFUL; adroit-core 555 tests, 0 failures, no API change |
| `./gradlew checkModuleDependencies` after the guard change | pass; the probe described in check 9 fails as intended |
| `./gradlew -PincludeDocsSnippets :docs-snippets:build :adroit-billing-compose:apiCheck` | BUILD SUCCESSFUL in 12 min; 6 tests, 0 failures |
| `node .github/scripts/check-docs.mjs` | "Docs OK: 33 files, 91 snippets match their compiled sources" |
| B24 (`a8c6350`): `publishToMavenLocal`, `verifyPublication`, `consumer-smoke` XML and Compose, `centralBundle --configuration-cache` | pass; `centralBundle` stops at its signing check without a key |

Last verified against: Play Billing Library 9.1.0, 2026-10-21
