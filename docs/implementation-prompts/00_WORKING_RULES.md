# Standing rules for every implementation prompt

Every prompt in this folder begins by telling Claude to read this file. These rules apply to **every** phase.

## A. Before changing anything
1. Read `docs/architecture/02_ARCHITECTURE.md`, `docs/architecture/03_PUBLIC_API.md`, and the sections the prompt names. They are the source of truth. If the prompt and the architecture disagree, **stop and report the conflict** instead of guessing.
2. Inspect the repository: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `build-logic/`, and every module the prompt touches. Understand the existing files before you edit.
3. Record the actual versions of Gradle, AGP, Kotlin (built-in or explicit), compileSdk, minSdk, JVM target, `ads-mobile-sdk`, and UMP. **Never assume a dependency version.** Read it from the catalog. If you add a dependency, check the latest stable version in Google's Maven repo or Maven Central (`maven-metadata.xml`) and justify it.
4. Check whether a class, file, or resource already exists before creating it. **Never create duplicates.** Never invent files that the prompt says exist; if one is missing, report it.
5. Verify any Google SDK API you call against the official reference at `https://developers.google.com/admob/android/next-gen/reference/kotlin/com/google/android/libraries/ads/mobile/sdk/...` or the matching guide. If the API differs from the architecture doc, use the real API, keep the public contract, and report the difference.

## B. While changing
6. Make the smallest coherent change that satisfies the prompt. Don't refactor unrelated code or overwrite working code without a reason stated in the report.
7. Library modules: Kotlin explicit API mode, `internal` by default, **no Google SDK types in public signatures** (except `@GoogleSdkEscapeHatch`), and resource names prefixed with `company_admob_`.
8. Engine state is main-thread confined (ADR-006). Google callbacks are posted to main inside the adapter. Host callbacks are delivered on main.
9. No `println`, no `android.util.Log` outside `AndroidAdLogger`, no reflection, no new DI framework, no Firebase/Compose/AppCompat/Material/Fragment in `admob-core`.
10. Ads never block the host. Every `show` path ends in exactly one result callback.
11. Keep KDoc on every public declaration. Comment density otherwise matches the surrounding code.

## C. After changing
12. Build: `./gradlew build` (or the narrower tasks the prompt lists). Fix all compile errors and warnings. Library modules use `allWarningsAsErrors`.
13. Test: run the unit tests the prompt lists, then `./gradlew testDebugUnitTest` for the touched modules. Everything must pass.
14. Lint: `./gradlew lint` for the touched modules, and fix any new issues.
15. API: after public API changes run `./gradlew apiDump`, review the `.api` diff line by line, and make sure it contains only the intended changes. Once `apiCheck` exists, it must pass.
16. Review your own diff (`git diff --stat` and `git diff`) for accidental changes, debug code, TODOs without an issue reference, and secrets.
17. Don't commit unless the user asks. If asked, use a Conventional Commit message (`feat(core): …`).

## D. Completion report (always, at the end)
Report, in this order:
1. **Summary**: what was implemented, in 3–6 bullets.
2. **Files created / modified / deleted**: full paths.
3. **Public API changes**: the `.api` diff, or "none".
4. **Commands run and their results**: build, test, lint, and apiCheck, with pass/fail counts.
5. **Acceptance criteria checklist**: each item marked ✅ or ❌ with a one-line justification.
6. **Deviations from the architecture**, and why.
7. **Unresolved issues / follow-ups / risks.**
8. **Environment facts observed**: versions actually in use.

The phase is **DONE** only when every acceptance criterion is ✅. If any item is ❌, say so plainly and don't claim completion.

## Toolchain facts at design time (2026-09-25). Re-verify each time.
- Gradle 9.3.1, AGP 9.1.1 (latest 9.4.1) with built-in Kotlin. The repo's `:app` compiles Kotlin with no `kotlin-android` plugin.
- compileSdk 37 (from Prompt 01; API 37 is installed), minSdk 24, targetSdk 36 (samples), Java 11 bytecode. Kotlin Gradle plugin 2.4.20 pinned via build-logic; library languageVersion/apiVersion 2.2, coreLibrariesVersion 2.2.0.
- Kotlin latest stable 2.4.20. Compose BOM 2026.09.00. lifecycle 2.11.0. coroutines 1.10.2 in libraries (1.11.0 is built with Kotlin 2.2.20 and would raise the consumer minimum).
- `ads-mobile-sdk` 1.4.0 documented (1.5.0 on Maven since 2026-09-24). UMP 4.0.0.
- API dumps: `apiDump`/`apiCheck` tasks from build-logic (BCV 0.18.2 dump engine; the BCV plugin itself does not work with AGP built-in Kotlin). Dependency guard: `checkModuleDependencies`. Robolectric 4.17 (check the latest). LeakCanary 2.x stable (don't use the 3.0 alpha).
