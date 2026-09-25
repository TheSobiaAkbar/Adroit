# PROMPT 23: Documentation set

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read **all** of `docs/architecture/` and skim the public API (`*/api/*.api`) and the samples.

## 1. Objective
Write the complete developer documentation with **real, compiling code examples** taken from, or verified against, the samples.

## 2. Context
- The audience is app developers at the company who receive the AAR. Most never read the architecture docs.
- Policy wording rule: **never** write "guarantees AdMob/Play policy compliance". Use: "Follows Google's documented requirements as of <date>. Your app remains responsible for its own policy configuration and product usage."

## 3. Files to create (repository root `docs/` unless noted)
`README.md` (repo root), `docs/GETTING_STARTED.md`, `docs/ARCHITECTURE.md` (a developer-facing summary linking to `architecture/02_ARCHITECTURE.md`), `docs/CONFIGURATION.md`, `docs/CONSENT.md`, `docs/BANNER.md`, `docs/INTERSTITIAL.md`, `docs/REWARDED.md`, `docs/REWARDED_INTERSTITIAL.md`, `docs/APP_OPEN.md`, `docs/NATIVE.md`, `docs/COMPOSE.md`, `docs/XML.md`, `docs/MEDIATION.md`, `docs/ANALYTICS.md`, `docs/REMOTE_CONFIG.md`, `docs/TESTING.md`, `docs/TROUBLESHOOTING.md`, `docs/MIGRATION.md`, `docs/RELEASE.md`, `CHANGELOG.md` (repo root, Keep a Changelog format).

## 4. Content requirements per document (minimum)
- **README**: what it is, the module table, a 10-line quick start, supported versions (minSdk 24, Kotlin ≥ the consumer minimum from ADR-010, Java 11 bytecode, Next-Gen `ads-mobile-sdk` version), links, and the policy disclaimer.
- **GETTING_STARTED**: repository setup (GitHub Packages / company repo credentials), dependencies, the manifest app ID meta-data (**required by UMP**), `Application.initialize`, `start` from the launcher Activity, a first banner (XML and Compose), a first interstitial, Hilt binding (`@Provides fun ads(): AdsClient = CompanyAdMob.client`), and non-Hilt usage.
- **CONFIGURATION**: the full DSL reference, the environments table, test-ID safety rules, validation errors and warnings (the full list), defaults (retry, expiry, frequency), placements design, and the Java builder.
- **CONSENT**: the flow diagram, what the library does and doesn't do, AdMob UI setup (Privacy & messaging, GDPR and US-states messages), the privacy options entry requirement, testing with debug geography and test device hashed IDs, `resetConsentForTesting`, age treatment, max content rating, RDP, Consent Mode with Firebase ordering, Play data safety and the `AD_ID` permission for child-directed apps.
- **Per-format docs** (BANNER … NATIVE): when to use it, integration (XML + Compose + plain View), lifecycle, the state model, events, errors, retry/expiry, frequency, format-specific policy notes (rewarded interstitial intro screen, app-open cold-start rules, native attribution/AdChoices/no custom click handlers, and hardware acceleration for video), and FAQ.
- **COMPOSE / XML**: every composable or extension with its parameters, the recomposition and lifecycle guarantees, lists, and configuration changes.
- **MEDIATION**: supported model, adding adapters, the **required global exclusion** block, the duplicate-class error symptom, testing mediation (no Test Ad label, per-network test modes), Ad Inspector, the third-party mediation-platform compatibility list with a link, and age-treatment adapter minimum versions (link).
- **ANALYTICS**: the `AdEvent` catalogue (table), `AdEventListener`, the Flow, paid events and ILRD enablement in the AdMob UI, and a Firebase/Amplitude mapping example.
- **REMOTE_CONFIG**: `AdConfigProvider`, `AdOverrides`, precedence, validation, and the Firebase provider (Prompt 24; if it's skipped, show a custom example).
- **TESTING**: `admob-testing`, `FakeAdsClient`, `CompanyAdMobTestRule`, Robolectric notes, and test devices for manual QA.
- **TROUBLESHOOTING**: at least 15 entries. Include: no ads in DEVELOPMENT, `Failed(InvalidConfiguration)`, consent never shown (not configured in the AdMob UI, not in EEA, use debug geography), `canRequestAds` false, the duplicate class `play-services-ads` error, `APP_ID_MISSING`, the manifest meta-data crash for UMP, app open not showing (launch count, cold start, suppression), `FrequencyCapped`, video not playing (hardware acceleration), OkHttp 5 conflict (`force_use_cronet`; check whether it should be exposed through `advanced`), R8 issues, `NO_FILL` in new ad units, and memory warnings for native ads.
- **MIGRATION**: from hand-rolled legacy `play-services-ads` code to CompanyAdMob (a mapping table from legacy calls to library calls), removing the legacy dependency, and moving the app ID; also library version migration notes (empty for 0.1.0).
- **RELEASE**: SemVer rules (§31), the release checklist, the **Google SDK upgrade checklist** (read the Next-Gen release notes and deprecation page → update `01_SDK_RESEARCH.md` → bump the catalog → full CI + nightly → API dump unchanged → CHANGELOG "Google SDK" section), and the tag and publish procedure.
- **CHANGELOG**: `## [Unreleased]` + `## [0.1.0]` summarising the features.

## 5. Implementation requirements
1. **Snippets must compile.** Put every non-trivial snippet in `docs-snippets/`, a small Gradle module **not published**, included only when `-PincludeDocsSnippets` is set, or in a sample's `src/test` if simpler. Reference it from the docs with a comment `<!-- snippet: path#region -->`, and add a CI step compiling it. Alternatively copy the snippets verbatim from sample code that compiles, and list their sources in the report.
2. Every doc ends with "Last verified against: ads-mobile-sdk X.Y.Z, UMP 4.0.0, <date>".
3. Use Mermaid for flows (consent, init, app open).
4. No claims about Google behaviour without a link to the official page.

## 6. Public API requirements
None. If writing the docs reveals API gaps, list them in the report.

## 7. Constraints
Don't duplicate the full architecture doc. Link to it.

## 8. Dependencies
None (a `docs-snippets` module only if chosen).

## 9. Tests
Compile the snippets (CI step), and run a markdown link check (`lychee` or a simple script) over the docs if it's available.

## 10. Validation commands
```
./gradlew -PincludeDocsSnippets :docs-snippets:compileDebugKotlin   # if the snippets module is used
```

## 11. Acceptance criteria
- [ ] All 21 files exist, with the content minimums in §4.
- [ ] The snippets compile, or are traced to compiling sample code.
- [ ] The policy disclaimer wording is correct everywhere (`grep -ri "guarantee" docs/` is reviewed).
- [ ] The "Last verified" footer is present on each doc.

## 12. Completion report
Per 00_WORKING_RULES §D, plus the list of API gaps found while documenting.
