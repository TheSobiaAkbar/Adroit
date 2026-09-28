# Part 4: Development roadmap and how to use these prompts

## How to use them
1. Paste **one** prompt file into a fresh Claude Code session opened at the repo root.
2. Claude reads `00_WORKING_RULES.md` plus the architecture docs, implements the phase, and ends with a completion report.
3. Check the report against the prompt's **Acceptance criteria**. **Don't start the next prompt until every item is ✅.** If something is ❌, paste back: "Acceptance item X failed: fix it and re-report."
4. Commit after each phase (`feat(<module>): PROMPT NN <title>`). Each phase is designed to leave the build green.
5. Placeholder names: `CompanyAdMob`, `com.company.admob`, `com.company:admob-*`, and `company_admob_` are placeholders. If the final brand is chosen **before Prompt 01**, find-and-replace those tokens across `docs/` first. Otherwise Prompt 01 records them in one place for a later rename.

## Phase order and gates

| # | Prompt | Depends on | Phase gate (DONE when) |
|---|---|---|---|
| 01 | Repository, Gradle and build-logic foundation | none | All modules exist and `./gradlew build` is green. The dependency-direction check and BCV are wired. `:app` is renamed to `:sample-xml` and uses the sample app ID. |
| 02 | Core domain model and public API skeleton | 01 | Every public type from 03_PUBLIC_API §3.2–3.3, 3.8 exists with tests. The API dump is committed. |
| 03 | Configuration system and test-ID safety | 02 | The DSL, validator, and environment matrix are fully unit-tested. |
| 04 | Internal ports and Google Next-Gen adapter | 03 | `AdsSdk`/`ConsentSdk` ports, fakes, the Next-Gen adapter, and error/response mappers exist and are tested. |
| 05 | Consent coordinator and PrivacyApi | 04 | The full UMP flow matrix is tested with fakes. |
| 06 | Init coordinator, AdsRuntime, facade, lifecycle tracking | 05 | `initialize`/`start`/`state`/`awaitReady`/`setAdFree` work end to end with fakes. The sample reaches `Ready` on an emulator. |
| 07 | Reliability primitives: retry, expiry, frequency, full-screen coordinator | 06 | Primitives are tested with virtual time. |
| 08 | Full-screen engine and interstitial | 07 | The interstitial gate matrix and reload are tested. A test interstitial shows in the sample. |
| 09 | Rewarded and rewarded interstitial | 08 | Exactly-once reward semantics are proven by tests. |
| 10 | App-open controller | 08 | The foreground, cold-start, and suppression matrix is tested. |
| 11 | Banner core | 06, 07 | The handle lifecycle is tested (Robolectric). A banner shows in the sample. |
| 12 | Native core | 06, 07 | Load, pool, binder, and destroy are tested. |
| 13 | `admob-views` module | 11, 12 | XML/Fragment integration is tested. |
| 14 | `admob-compose` module | 13 | Recomposition, disposal, and rotation tests pass. |
| 15 | Remote config, event sinks, diagnostics | 08–12 | Overrides, listeners, the diagnostics report, and Ad Inspector work. |
| 16 | `admob-testing` module | 15 | Host-style tests using `FakeAdsClient` pass. |
| 17 | Sample XML app | 13, 16 | Activity, Fragment, and all formats work on an emulator. |
| 18 | Sample Compose playground | 14, 15 | All 12 playground screens exist, with live state. |
| 19 | Sample mixed and mediation exclusion demo | 17, 18 | Interop works, and the exclusion recipe builds. |
| 20 | R8, minified release, and leak validation | 17–19 | The minified release sample works, and leak tests pass. |
| 21 | Maven publishing | 20 | `publishToMavenLocal` yields correct POMs, sources, and javadoc. A consumer smoke project resolves them. |
| 22 | CI/CD | 21 | Workflows are green on a PR. The release dry run passes. |
| 23 | Documentation set | 22 | All 21 docs exist and their code compiles (snippets are verified in samples). |
| 24 | Optional `admob-firebase` module | 15, 21 | Tested with fakes. The core has no Firebase dependency. |
| 25 | Security and privacy audit | 23 | Every finding is fixed or tracked. |
| 25A | Ad profiles: one JSON per ad, controlled from Firebase (added 2026-09-27) | 24, 25 | One ads file configures every ad; Firebase changes each ad without a release. |
| 26 | Performance audit | 25A | Measurements are recorded and regressions fixed. |
| 27 | API stability audit | 25, 25A, 26 | The public API is frozen for 1.0.0-rc1. |
| 28 | Real Firebase, enum ad definitions, format swapping, splash flow (added 2026-09-28) | 27 | See PROMPT_28; additive to the 1.0.0-rc1 API. |
| FINAL | Production readiness audit | 27 | PASS/WARN/FAIL scorecard with fixes. |

Prompt 24 is optional. Skip it if the company doesn't use Firebase.

## Why this order
- The domain model, config, and ports (02–04) come first, so every engine is built test-first against fakes, and Google only appears in one layer.
- Consent (05) and init (06) come before any ad format, so it's structurally impossible to load an ad before consent.
- The reliability primitives (07) come before the engines, so all formats share one retry, expiry, and frequency implementation.
- Full-screen formats (08–10) are built on one engine, with rewarded after interstitial because it reuses everything.
- Core view-based formats (11–12) come before the UI toolkits (13–14), so the Compose and XML layers stay thin.
- Samples (17–19) come before release hardening (20–22), because R8 and leak validation run *on* the samples.
- Audits come last, when there's a complete system to audit.
