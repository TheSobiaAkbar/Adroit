# FINAL PRODUCTION READINESS AUDIT

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read **every** file in `docs/architecture/` and `docs/audits/`, and inspect the **complete** repository: every module, build file, test, sample, workflow, and doc.

## Objective
Decide whether CompanyAdMob is ready to ship `1.0.0` to company apps. Produce `docs/audits/FINAL_PRODUCTION_READINESS.md` with a **PASS / WARN / FAIL** verdict per category, backed by concrete evidence. **"Looks good" isn't acceptable.** For every WARN or FAIL, give the file:line, the problem, the fix, and either apply the fix (preferred) or create a tracked follow-up item in the report with an owner placeholder.

## Rules for the auditor
- Evidence means a command output, a test name that passed, a file:line reference, or a measurement. An opinion isn't evidence.
- Re-verify Google facts **against the live official documentation today**, not against `01_SDK_RESEARCH.md`: the latest Next-Gen version, any new deprecations, and the UMP version. If anything changed, mark the affected categories WARN and describe the required upgrade.
- Run everything: `./gradlew clean build lint apiCheck checkModuleDependencies testDebugUnitTest verifyPublication`, all the sample release builds (including `-PwithMediation=true`), the `consumer-smoke` build, and the connected tests if an emulator is available. Paste the summaries.
- A category is **FAIL** if any mandatory item fails, **WARN** if items are unverified or have minor issues, and **PASS** only if every item has evidence.

## Categories and mandatory items

### 1. Architecture
- Modularity matches `02_ARCHITECTURE.md` §7. There are no extra or undocumented modules.
- Separation: Google imports only in `internal/google` (the test passes). Engines depend on ports only.
- Dependency direction: `checkModuleDependencies` passes, and there's no core → compose/views/firebase/appcompat/material/fragment/hilt dependency.
- API stability: the BCV dumps are committed, `apiCheck` passes, and there are no Google types in the dumps.
- ADRs 001–013 are all reflected in the code (spot-check each with a file reference).

### 2. AdMob SDK integration
- Uses Next-Gen `ads-mobile-sdk` (the version, and whether it's still current vs. the live release notes).
- **No deprecated API usage**: compile with deprecation warnings as errors, and grep for `getCurrentOrientationAnchoredAdaptiveBannerAdSize`, `setTagForChildDirectedTreatment`, `setTagForUnderAgeOfConsent`, `BannerAd.load`, and `getView(`.
- `MobileAds.initialize` runs on a background thread, only after consent, only once.
- Every SDK callback is marshalled to main.
- Formats: banner, interstitial, rewarded, rewarded interstitial, app open, and native are all implemented per the architecture sections, with their tests listed.
- Expiry values: 55 min / 3 h 55 min, still consistent with Google's guidance today.
- Error-code mapping covers every current `LoadAdError.ErrorCode` and `FullScreenContentError.ErrorCode`, re-checked against the live reference.

### 3. Consent and privacy
- The UMP flow matches Google's current documented order (re-read the live page).
- There's no path to an ad request before `canRequestAds()`, with each call site traced.
- Privacy options: exposed, documented, and revocation handled.
- Child/teen treatment via `setAgeRestrictedTreatment`, max content rating, RDP, and debug-only settings blocked in production.
- No consent caching and no logging of consent strings or identifiers.
- The Prompt 25 audit findings are closed.

### 4. Android lifecycle and platform
- Lifecycle: Activity validation before show, the ActivityTracker clears references, and banner/native are bound to lifecycle owners.
- Configuration changes: consent attaches to the in-flight operation, the rewarded unclaimed channel works, and native retention works (the tests are named).
- Memory leaks: LeakCanary instrumentation results (Prompt 20) and the WeakReference unit tests.
- Threading: main-thread confinement, and StrictMode clean (Prompt 26).
- R8: minified builds pass the smoke tests, and consumer rules are minimal and justified.
- minSdk 24 / compileSdk / targetSdk (samples) are correct and current vs. the Play target-API requirement **as of today** (check the live Play requirement).

### 5. Compose
- No reload on recomposition, and no duplicate AdView (tests).
- Disposal destroys the banner, and the ViewModel clear destroys native ads (tests).
- `AndroidView` factories return existing views, and the update lambdas are idempotent.
- No Material dependency in `admob-compose`.

### 6. XML / Views
- The Fragment view lifecycle is respected (tests).
- `AdBannerView`/`AdNativeView` XML inflation works in minified builds.
- Templates: attribution present, AdChoices space, RTL, dark mode, and no custom click listeners on assets.
- Cleanup on destroy and detach.

### 7. Reliability
- Retry: backoff values, caps, no background retries, and non-retryable errors stop (tests).
- Expiry and caching: single-use full-screen ads, pool limits, and expiry destroys.
- Failure handling: airplane-mode walkthrough results from the samples. Every show returns exactly one result (tests, including the watchdog).
- Duplicate display protection: coordinator tests, the app-open loop test, and duplicate reward protection (the race test).

### 8. Configuration and test safety
- DEVELOPMENT always uses demo IDs (test). PRODUCTION rejects demo IDs (test). Remote overrides can't bypass this (test).
- The demo IDs still match Google's live test-ads page **today** (re-fetch and compare).

### 9. Testing
- Unit test counts per module and their pass status. Coverage for `internal/reliability` and `internal/consent`.
- Robolectric, Compose UI, instrumentation, and leak tests: the last run results.
- The samples build in debug, staging, and release.
- `admob-testing` works in a host-style test.

### 10. Publishing and CI/CD
- `verifyPublication` passes. POM metadata, sources, and javadoc are present. `consumer-smoke` resolves.
- SemVer version set. CHANGELOG has the release section.
- Workflows: CI green on the latest PR (or locally equivalent), nightly instrumentation configured, the release guard works, and actions are pinned.

### 11. Documentation
- All 21 docs exist and meet the Prompt 23 content minimums. The snippets compile.
- README quick start works **verbatim** in a fresh project (do it with `consumer-smoke`).
- MIGRATION and TROUBLESHOOTING are complete. The policy disclaimer wording is correct and there are no compliance guarantees.
- The "Last verified" footers match the actual SDK versions.

### 12. Security
- No secrets (the scans from Prompt 25, re-run).
- Logging levels and redaction in PRODUCTION.
- Config handling: IDs treated as public identifiers, no credentials anywhere.

### 13. Performance
- Prompt 26 measurements within targets: `initialize()` < 2 ms, no main-thread I/O, the startup request count as designed, no background retries, and memory within expectations.
- No unnecessary eager loading (`ON_DEMAND`/`NONE` respected).

## Output format (`docs/audits/FINAL_PRODUCTION_READINESS.md`)
```
# Final Production Readiness — <date> — CompanyAdMob <version>
## Verdict: READY | READY WITH WARNINGS | NOT READY
## Scorecard
| # | Category | Verdict | Key evidence | Open items |
|---|----------|---------|--------------|------------|
| 1 | Architecture | PASS/WARN/FAIL | … | … |
…
## Findings
### F-01 [FAIL|WARN] <title>
- Category / Location: path:line
- Problem:
- Evidence:
- Fix: (applied in <files> | proposed)
- Verification after fix:
…
## Google SDK re-verification (live, <date>)
## Commands run and results
## Follow-ups (owner, due)
```

## Acceptance criteria
- [ ] All 13 categories were assessed with evidence. There are no evidence-free PASS verdicts.
- [ ] Every FAIL is fixed and re-verified, or the overall verdict is NOT READY.
- [ ] The Google facts were re-verified live and dated.
- [ ] The report was written and the full CI command set is green after the fixes.

## Completion report
Per 00_WORKING_RULES §D, plus the overall verdict and the scorecard table pasted inline.
