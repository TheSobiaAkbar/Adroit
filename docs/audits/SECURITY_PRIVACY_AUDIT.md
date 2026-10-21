# Security and privacy audit

PROMPT 25, run on 2026-09-27 against branch `feat/adroit-foundation` (base commit `1495077`), with
ads-mobile-sdk 1.4.0, UMP 4.0.0 and Firebase BoM 34.19.0. Scope: the five library modules, the three samples, the
build logic, the CI workflows and the git history. Architecture references: `02_ARCHITECTURE.md` §23, §29, §30 and
`01_SDK_RESEARCH.md` §1.6.

This audit checks the library against Google's documented requirements. It isn't legal advice, and the host app
stays responsible for its own policy configuration (see the README's Policy section).

## Result

| | Count |
|---|---|
| PASS | 10 |
| PASS after a fix in this audit | 2 (checks 11 and 12) |
| FAIL fixed and re-verified | 2 (checks 9 and 13) |
| WARN (owner and follow-up below) | 2 (checks 10 and 15) |
| Open FAIL | 0 |

Checks 7 and 12 also gained regression tests.

## Checks

"C" is `adroit-core/src/main/kotlin/io/github/thesobiaakbar/adroit`, and "T" is `adroit-core/src/test/kotlin/io/github/thesobiaakbar/adroit`.

### Privacy and consent

| # | Check | Status | Evidence | Fix |
|---|---|---|---|---|
| 1 | No ad request before `canRequestAds()` and SDK init | PASS | The only Google load calls are in `C/internal/google/NextGenAdsSdk.kt:114-117` (full-screen), `:131` (native) and `NextGenBannerView.kt:37` (banner, via `createBannerView`). Their engine call sites are each guarded by `readiness.canLoad()`: the banner at `C/internal/banner/BannerAdHandleImpl.kt:171` (first load and width reloads) and `:284` (retries), full-screen at `C/internal/fullscreen/FullScreenAdEngine.kt:258` (the only path to `adsSdk.loadFullScreen`: preload, reload and retry), and native at `C/internal/native/NativeAdLoaderEngine.kt:235` (`pump`, the only caller of `load(pool)`). `canLoad()` is `state == Ready` (`C/internal/init/InitCoordinator.kt:80`), and `Ready` is set only after `SdkInitResult.Success` with `canRequestAds()` still true (`:218`, and `reevaluate` needs `sdkInitialized`). SDK init itself returns early unless `canRequestAds()` (`:177`). Tests: `ReadinessTest`, `StartFlowTest` (every consent outcome) and `FakeAdsSdk.onLoad` assertions in the engine tests. | — |
| 2 | Consent update on every `start()`; no consent cache | PASS | `start()` from `AwaitingStart`, or from `Unavailable(CONSENT_NOT_OBTAINED)`, runs `gather`, which always calls `consentSdk.requestUpdate` (`C/internal/consent/ConsentCoordinator.kt:80`, then `UmpConsentSdk.kt:32`). That is once per process launch, matching Google's "every app launch". A repeat `start()` in the same process joins the settled state (`StartFlowTest` "a Ready start completes immediately", which asserts one update). SharedPreferences use in the libraries: only `adroit_state/launch_count` (`C/internal/storage/SharedPrefsKeyValueStore.kt:26`) and `gad_rdp` (`C/internal/consent/RdpWriter.kt`). The consent state is read from UMP each time, never stored. | — |
| 3 | Revoking consent through privacy options stops ads | PASS | `T/internal/runtime/AdFreeTest.kt:109` (withdrawn → `Unavailable(CONSENT_NOT_OBTAINED)` and blocked listeners told), `T/internal/consent/ConsentCoordinatorTest.kt:304`, and `T/internal/runtime/StartFlowTest.kt:156`. What each engine does when blocked: `FullScreenShowTest.kt:234` (cached ad released), `NativeLoaderTest.kt:149` (pool destroyed, late loads destroyed) and `BannerAdHandleTest.kt:225` (hidden and destroyed). All pass. | — |
| 4 | Privacy-options state exposed and documented | PASS | `Adroit.privacy.isPrivacyOptionsRequired: StateFlow<Boolean>` (`C/PrivacyApi.kt:15`) and `showPrivacyOptions` (`C/internal/consent/ConsentCoordinator.kt:137`). Documented in `docs/CONSENT.md:29` and `:55-90`, with View and Compose snippets. | — |
| 5 | Debug geography and test device IDs impossible in PRODUCTION | PASS | Validation: `T/config/ConfigValidatorTest.kt:278` (debug geography in production is an error) and `:315` (test device IDs are a warning and are dropped). Effective config: `C/internal/config/EffectiveConfig.kt:40`, tested by `T/config/EffectiveConfigTest.kt:104`. UMP parameters: `ConsentCoordinatorTest.kt:358` ("PRODUCTION never sends debug geography or test devices") and `:402` (`resetConsentForTesting` is a no-op). | — |
| 6 | Age treatment through `setAgeRestrictedTreatment`; no TFCD/TFUA | PASS | `C/internal/google/GoogleRequestConfigMapper.kt:17`. A grep of the library sources for `TagForChildDirected`, `TAG_FOR_CHILD`, `tagForChild` and `tagForUnderAge` finds nothing. The only under-age call is UMP's own `ConsentRequestParameters.setTagForUnderAgeOfConsent` (`UmpConsentSdk.kt:45`, CHILD only), which the architecture requires (§30) and which isn't the deprecated GMA request tag. | — |
| 7 | RDP key written before init, removed when off | PASS | `C/internal/init/InitCoordinator.kt:193` (`rdpWriter.apply`) runs before `adsSdk.initialize` (`:195`), with `commit()` (synchronous). `RdpWriter.kt:28` removes the key when off. Tests: `RdpWriterTest` (file name, write, removal), plus a **new** regression test, `StartFlowTest` "gad_rdp is written before the SDK initializes, and removed before it when turned off", which reads the preferences from inside `FakeAdsSdk.initialize` (new `onInitialize` hook). | Test added |
| 8 | No TC/GPP string, advertising ID or user identifier read or logged | PASS | A `git grep` of every library `src/main` for `IABTCF_`, `IABGPP_`, `AdvertisingIdClient`, `ANDROID_ID`, `Settings.Secure` and `gpp` finds nothing. The only `getString(` calls read the manifest app ID (`ManifestAppIdReader.kt:22`), Google's response extras (mediation group and A/B test names, `GoogleResponseInfoMapper.kt:24`) and view attributes (`AdBannerView.kt:69`, `AdNativeView.kt:64`). None of them reads default preferences. SSV custom data prints as `<set>` (`C/FullScreenAdApis.kt:136`) and is never logged. | — |
| 9 | Docs cover Play data safety, `AD_ID` and Consent Mode ordering | **FAIL → fixed** | Play data safety and `AD_ID` were covered (`docs/CONSENT.md:152`). The Consent Mode section was **wrong**: it told apps to set Firebase consent themselves *after* `start()`. Google's page says to turn on consent mode in the AdMob UI, use UMP 3.2.0 or newer (which applies the choices), and **initialize Firebase before starting UMP**. Rewritten from Google's page (`docs/CONSENT.md:141`), and `check-docs.mjs` passes. | `docs/CONSENT.md` |

### Security

| # | Check | Status | Evidence | Fix |
|---|---|---|---|---|
| 10 | No secrets in the repo or history; old publisher ID gone | **WARN** | `git log -p --all \| grep -Ei "(password\|secret\|token\|api[_-]?key)"` gives 65 unique lines, all reviewed. They're only env and secret *names* (`SIGNING_KEY`, `COMPANY_MAVEN_PASSWORD`, `gpr.key`), `${{ secrets.* }}` references, placeholders (`<personal-access-token>`) and the `FullScreenCoordinator.Token` code. There's no credential value. `forbidden-patterns.sh` finds the old publisher ID nowhere in HEAD. **History:** the ID is still in `2f76e72` (the initial commit's manifest, already on `origin/master`) and in the local, unpushed commit `bc55985` (the manifest, plus three prompt files that quoted it in a `git grep` command). It was removed in `b61b3b3`. See WARN-1. | Owner decision |
| 11 | CI secrets only via `secrets.*`; least privilege; SHA pins | PASS after fix | Secrets appear only as `${{ secrets.* }}`, and nothing echoes them. The workflows default to `contents: read`; only the release job gets `contents: write` and `packages: write`. **Fixed:** `actions/checkout`, `setup-java`, `upload-artifact` and `cache` were pinned by tag. They're now pinned by commit SHA, with the version in a comment, as the third-party actions already were, and Dependabot's `github-actions` ecosystem keeps them current. **Fixed:** `SIGNING_KEY` and `SIGNING_PASSWORD` were job-level env in `release.yml`, visible to every step (the whole build and test suite, and the third-party release action). They're now set only on the two publish steps. **Fixed:** checkout left the job token in `.git/config`. Every checkout now uses `persist-credentials: false`, since no workflow pushes. | `.github/workflows/*.yml` |
| 12 | PRODUCTION logs: WARN default, ad unit IDs redacted, no `println` or stray `Log.` | PASS after fix | Default level: `C/internal/config/EffectiveConfig.kt:46`. `forbidden-patterns.sh` prints "none found" (no `println(`, and `android.util.Log` only in `AndroidAdLogger`). **Found:** `RetryScheduler` logged its key, `"<FORMAT>:<full ad unit ID>"`, at DEBUG, and override and validation messages can carry full IDs. At the default WARN level these weren't written, but a PRODUCTION app that lowered the level got full IDs, against §23. **Fixed at the log layer:** `AdLog` now shortens every full ad unit ID in every message when `redactAdUnitIds` is set (`C/internal/logging/AdLog.kt:43`), and `AdsRuntime` sets it for PRODUCTION (`C/internal/runtime/AdsRuntime.kt:72`). The views, Compose and Firebase modules log through the same `AdLog` (`AdroitInternal.log`). The regex is shared with the diagnostics report (`redactAdUnitIdsIn`). Tests: `AdLogTest` (redacted at every level; unredacted outside PRODUCTION). | `AdLog.kt`, `AdEvent.kt`, `AdsRuntime.kt`, `DiagnosticsApi.kt` |
| 13 | Escape hatch opt-in at ERROR; customizer failures contained | **FAIL → fixed** | `@RequiresOptIn(level = ERROR)` is at `C/GoogleSdkEscapeHatch.kt:9`. **Found:** `GoogleRequestFactory` caught only `Exception`. The escape hatch's own warning is that a customizer may break on a Google SDK upgrade, and that break shows up as a `LinkageError` (`NoSuchMethodError` or `NoClassDefFoundError`), which escaped and would crash the load. **Fixed:** `LinkageError` is contained too (`C/internal/google/GoogleRequestFactory.kt:37`), and the KDoc says so. New `GoogleRequestFactoryTest` (3 tests: the builder type passed in, an exception contained, `NoSuchMethodError` contained). | `GoogleRequestFactory.kt` |
| 14 | No exported components added by the libraries | PASS | The libraries' release merged manifests (`build/intermediates/merged_manifest/release/…`): adroit-core adds only `<uses-permission INTERNET>`. adroit-views, adroit-compose and adroit-testing are empty, and adroit-firebase has no manifest. For the whole app (sample-xml release merged manifest), every exported component other than the app's launcher comes from AndroidX (brought in by the Google SDK) and is protected by a system permission: WorkManager's `SystemJobService` (`BIND_JOB_SERVICE`), and its `DiagnosticsReceiver` and ProfileInstaller's receiver (`DUMP`). The Google SDK also merges `AD_ID`, `READ_BASIC_PHONE_STATE`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` and `FOREGROUND_SERVICE`; `AD_ID` is documented in `docs/CONSENT.md`. | — |
| 15 | Dependency vulnerabilities | **WARN** | The resolved `releaseRuntimeClasspath` of `:sample-mixed -PwithMediation=true` (168 coordinates) and `:adroit-firebase` (129) was queried against the GitHub Advisory Database through OSV.dev (`/v1/querybatch`). `gh` isn't installed here, and OWASP dependency-check wasn't run. Findings, both **transitive from `ads-mobile-sdk` 1.4.0** and unchanged in 1.5.0's POM: `protobuf-javalite` 3.22.3 through Cronet 143.7445.0 has [GHSA-735f-pc8j-v9w8](https://github.com/advisories/GHSA-735f-pc8j-v9w8) (CVE-2024-7254, HIGH, a stack overflow on deeply nested input, fixed in 3.25.5). `okio` 3.2.0 through OkHttp 4.11.0 has [GHSA-w33c-445m-f8w7](https://github.com/advisories/GHSA-w33c-445m-f8w7) (CVE-2023-3635, MODERATE, a malformed-gzip DoS, fixed in 3.4.0; apps with Firebase already resolve 3.4.0). No advisories for OkHttp 4.11.0, Guava 32.0.0-android, Gson 2.13.2, WorkManager 2.7.0, Room 2.2.5, Kotlin 2.4.20, coroutines 1.10.2, the Firebase artifacts or the AppLovin adapter. See WARN-2. | Owner decision |
| 16 | `rewardId` random, not persisted; no persistent identifiers | PASS | `UUID.randomUUID()` per rewarded show (`C/internal/fullscreen/RewardSession.kt:27`), kept in memory only. The only persisted values are the launch count and `gad_rdp` (check 2). | — |

## Follow-ups

**WARN-1: the old publisher ID in git history.** *Owner:* the repository owner.
- An AdMob publisher ID is a public identifier: it ships in every APK (architecture §29), so this isn't a leaked secret.
- `2f76e72` is already on `origin/master`. Removing it there needs a history rewrite and a force-push, which is the
  owner's decision.
- `bc55985` hasn't been pushed. If the ID must not appear in the new history, rewrite that commit's prompt text and
  manifest before the branch is first pushed.
- Otherwise, accept it as is: `forbidden-patterns.sh` keeps it out of every future commit.

**WARN-2: protobuf-javalite and okio advisories in Google's SDK dependencies.** *Owner:* the library maintainers.
- Both are reachable only through data from Google's and the mediation networks' servers over TLS, so exploiting
  them needs a compromised or impersonated ad server.
- *Follow-up:* decide whether `adroit-core` should publish dependency constraints (`okio` ≥ 3.4.0,
  `protobuf-javalite` ≥ 3.25.5). Upgrading those runtimes within 3.x is backward compatible, and Firebase apps
  already run okio 3.4.0 with the SDK.
- Such constraints change every consumer's classpath, so they need a device run of the samples (and a minified
  build) before release. Until then, apps can add the constraints themselves.
- Re-run this scan on every Google SDK upgrade (docs/RELEASE.md), and report the versions to Google through the SDK
  issue tracker.

## Other changes made during the audit

- `adroit-firebase/lint-baseline.xml` held a stale `MissingPermission` entry from lint's first run. It's reset to
  empty like the other modules, so lint's result now rests on the documented `@SuppressLint` alone.
- `CHANGELOG.md` `[Unreleased]`: the fixes, under "Fixed" and "Security".

## Validation

The CI command set from `.github/workflows/ci.yml` was run locally after the fixes. Every step passed:

| Step | Result |
|---|---|
| `forbidden-patterns.sh` | "none found" |
| `build lint apiCheck checkModuleDependencies testDebugUnitTest verifyPublication` and the three minified samples | pass (21 min 50 s). Unit tests: core 411, views 22, compose 20, testing 14, firebase 13; 0 failures. Lint passes with every baseline empty. `apiCheck` passes, with no public API change. |
| Mediation sample and its dependency guard (`-PwithMediation=true`) | pass |
| Docs snippets module, and `check-docs.mjs` | pass: 21 files, 44 snippets |
| `publishToMavenLocal`, then `consumer-smoke` `assembleDebug verifyConsumerClasspath` | pass |

## Billing

Added by the billing readiness audit on 2026-10-21 ([BILLING_READINESS_AUDIT.md](BILLING_READINESS_AUDIT.md), check
7), for the five `adroit-billing*` modules.

| # | Check | Status | Evidence |
|---|---|---|---|
| B1 | No purchase token, order ID with account, or person in logs, events or preferences | PASS | `TokenLeakScanTest` scans every billing `src/main`; `PurchaseProcessorTest` "no purchase token reaches the logs, the events or the ledger"; `AttributionTest` "no purchase token is stored"; `EntitlementEngineTest` "the cache holds no purchase token". Correlation uses `redact()`, `sha256(token).take(12)` (`adroit-billing/…/internal/BillingLog.kt:35`). |
| B2 | No server credentials in the repository | PASS | `git grep` for `private_key`, `service_account`, `client_secret` and private-key headers finds only `docs/BILLING_BACKEND.md`'s warnings. The Play Developer API key belongs on the app's server ([BILLING_BACKEND.md](../BILLING_BACKEND.md)). The samples' `google-services.json` Firebase client keys are the readiness audit's WARN-1. |
| B3 | The verifier talks HTTPS only | PASS | `HttpPurchaseVerifier` rejects other schemes, except `http://` to local hosts when allowed for development (`HttpPurchaseVerifier.kt:121`; `HttpPurchaseVerifierTest` "only https, or local http when allowed"). |
| B4 | No grant on pending, suspended, unspecified or a failed query | PASS | `PurchaseProcessorTest` (pending, suspended, unspecified, rejected), `EntitlementSnapshotTest` "an unknown snapshot grants nothing, even with active grants", `EntitlementEngineTest` "without a cache the start is unknown and grants nothing". |
| B5 | Client-only limits documented | PASS | [BILLING.md](../BILLING.md) "What the app can and can't know" and its security box: without a server, a rooted device can fake access, and Firebase can't grant premium. |
