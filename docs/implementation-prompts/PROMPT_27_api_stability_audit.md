# PROMPT 27: API stability audit and 1.0.0-rc1 freeze

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §31, §32 and `03_PUBLIC_API.md`, and read every `*/api/*.api` file line by line.

## 1. Objective
Review the whole public surface as a product before freezing it for `1.0.0-rc1`. Remove accidental exposure, fix naming inconsistencies, make evolution-safe choices, and document the stability guarantees.

## 2. Checks (each gets PASS / WARN / FAIL with evidence)
1. **No Google types** in any `.api` file (the test exists), except `@GoogleSdkEscapeHatch` members (listed).
2. **Accidental public**: every public declaration is intended (compare against 03_PUBLIC_API). List the extras. Make them `internal`, or move them behind `@RestrictTo`/opt-in.
3. **Binary-evolution hazards**:
   - public `data class`es that are likely to gain fields (ADR-011)
   - `sealed` hierarchies hosts may exhaustively `when` over (adding a subtype is source-breaking for `when` without `else`; document which sealed types may grow: `AdError`, `AdEvent`, and `AdsState`, and say so in their KDoc: "new subtypes may be added in minor versions; include an else branch")
   - `enum`s that may grow (the same KDoc rule)
   - interfaces hosts implement (`AdLogger`, `AdEventListener`, `AdConfigProvider`, `NativeAdRenderer`): adding members breaks them, so they must stay `fun interface` or be given defaults with `-Xjvm-default=all`
   - default arguments in public functions: adding a parameter breaks binary compatibility unless `@JvmOverloads` or overloads are used. Decide the policy and apply it.
4. **Naming consistency**: `show`/`preload`/`availability` across formats, `Ad`-prefix usage, `placement` parameter naming, callback parameter names (`onResult`, `onComplete`, `onDismissed`), and duration types (`kotlin.time.Duration` everywhere).
5. **Java interop**: `@JvmStatic` on facade functions, `@JvmOverloads` where needed, SAM-friendly callbacks, and no Kotlin-only types in signatures that Java callers need (the `Duration` inline class is awkward from Java; provide `…Millis` overloads **only** for the facade entry points hosts call from Java, or document Kotlin-only support; decide and record it as ADR-013).
6. **Nullability and threading contracts** are documented on every public member (a KDoc lint: write a small test that parses sources for public declarations without KDoc, or use Dokka's `reportUndocumented`).
7. **Opt-in markers** are applied to any surface not yet stable (`@ExperimentalCompanyAdMobApi`).
8. **Deprecation policy** is documented in RELEASE.md: deprecate in minor N, remove in the next major, with `ReplaceWith`.
9. **The BCV baseline** is regenerated and committed as the `1.0.0-rc1` baseline.
10. **Consumer compatibility matrix** tested with `consumer-smoke`: Kotlin 2.1 consumer (if ADR-010 holds), Java 11 target consumer, minSdk 24 consumer, and an AGP 8.x consumer (the lowest supported; determine it from Next-Gen's AGP 7.3.1 minimum and our Kotlin metadata). Record the results in `docs/audits/API_STABILITY_AUDIT.md`.

## 3. Files to create / modify
- `docs/audits/API_STABILITY_AUDIT.md`
- Library sources and `.api` dumps (the fixes)
- `03_PUBLIC_API.md`, synced to reality
- `gradle.properties` version → `1.0.0-rc1`, and `CHANGELOG.md`

## 4. Constraints
Every breaking change here is allowed (pre-1.0) but must be listed in CHANGELOG under "Changed (breaking)".

## 5. Validation commands
The Prompt 22 CI set, plus `consumer-smoke` builds for each matrix row.

## 6. Acceptance criteria
- [ ] All 10 checks were evaluated with evidence.
- [ ] 03_PUBLIC_API.md equals the `.api` dumps (a review statement plus a diff summary).
- [ ] ADR-013 is recorded. The rc1 baseline is committed.
- [ ] The consumer matrix results are recorded.

## 7. Completion report
Per 00_WORKING_RULES §D, plus the audit table and the list of breaking changes made.
