# B01 — Billing modules, Gradle and version verification

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Create the five billing modules, empty but building, and wire them into the existing Adroit build: catalog,
convention plugins, publishing, API dumps and the dependency guard. Re-verify the Play Billing facts the
architecture depends on.

## Context
`docs/architecture/06_BILLING_ARCHITECTURE.md` §1–2. The repo already has these, which billing reuses:
- `build-logic/convention` plugins: `adroit.android.library`, `adroit.android.library.compose`,
  `adroit.android.firebase`, `adroit.publishing`;
- `apiDump`/`apiCheck`;
- `checkModuleDependencies`.

## Prerequisites
The ads modules build green on `master` (`./gradlew build`).

## Files / modules
- `settings.gradle.kts`: include `:adroit-billing`, `:adroit-billing-compose`, `:adroit-billing-views`,
  `:adroit-billing-firebase`, `:adroit-billing-testing`.
- `gradle/libs.versions.toml`: add `playBilling` (latest stable on Google Maven, expected 9.1.x), with libraries
  `play-billing` and `play-billing-ktx` (pick one, see Requirements). Add `androidx-core` ≥ 1.9 if it isn't there for
  libraries.
- `<module>/build.gradle.kts`, `src/main/AndroidManifest.xml` (empty), `consumer-rules.pro`, and one placeholder
  public type per module (for example `AdroitBillingVersion` in core), so the API dump isn't empty.
- The `checkModuleDependencies` rules in build-logic: allowed edges are
  - `billing-compose → billing`;
  - `billing-views → billing`;
  - `billing-firebase → billing + adroit-firebase`;
  - `billing-testing → billing`.

  The guard **fails** if `adroit-billing` depends on any `adroit-*` module, Compose, AppCompat, Fragment or Firebase.
- Publishing: each module except the samples uses `adroit.publishing`, with artifact IDs equal to the module names.
- `docs/architecture/06_BILLING_ARCHITECTURE.md` §1: update the "Verify again" bullets with what you found.

## Requirements
1. Verify on Google Maven (`dl.google.com/android/maven2/com/android/billingclient/billing/maven-metadata.xml`) and in
   the release notes:
   - the latest stable PBL;
   - its minSdk;
   - its androidx.core requirement;
   - the Kotlin it's built with.

   Then decide between `billing` and `billing-ktx`. Prefer `billing-ktx` only if its suspend extensions
   (`queryProductDetails`, `acknowledgePurchase`, `consumePurchase`, `queryPurchasesAsync`) exist for 9.x. Otherwise
   use `billing` and wrap the callbacks with `suspendCancellableCoroutine` later (B04). Record the decision in the
   doc.
2. Read the reference pages and record in §1:
   - whether `BillingFlowParams.Builder.setObfuscatedProfileId` requires `setObfuscatedAccountId`;
   - the exact `QueryPurchasesParams` builder method that includes suspended subscriptions;
   - the `PendingPurchasesParams` builder methods (`enableOneTimeProducts`, `enablePrepaidPlans`).
3. `adroit-billing` deps:
   - `api`: kotlinx-coroutines-core;
   - `implementation`: play billing, coroutines-android, lifecycle-process, lifecycle-runtime, androidx-annotation,
     androidx-core.
4. The compose/views/firebase/testing modules only declare their toolkit plus `api(project(":adroit-billing"))`.
   `adroit-billing-firebase` uses the firebase convention plugin the way `adroit-firebase` does.
5. The PBL manifest merges `com.android.vending.BILLING`. Confirm it in the merged manifest of a sample later (B21).
   Don't declare it by hand.

## Constraints
- Don't touch the ads modules' code, samples, or published coordinates.
- Keep `consumerMinCompileSdk` semantics. If PBL raises the minimum compileSdk, report it.

## Edge cases
- If PBL 9.x needs a newer AGP, Kotlin or compileSdk than the repo has, stop and report it. Don't upgrade the
  toolchain silently.
- The configuration cache must stay working (the last release fix `dd47851` depends on it).

## Tests
None new. Every module must compile, and `apiDump` must create `api/*.api` files.

## Verify
```
./gradlew build
./gradlew apiDump apiCheck checkModuleDependencies
./gradlew publishToMavenLocal --dry-run
```

## Acceptance criteria
- [ ] Five modules exist and `./gradlew build` is green.
- [ ] The catalog has a verified PBL version, with the source and date recorded in 06 §1.
- [ ] The dependency guard rejects a forbidden edge. Prove it by temporarily adding one, seeing the failure, then
      reverting.
- [ ] API dumps are committed for the five modules.
- [ ] The ads modules have an unchanged `.api` and an unchanged dependency tree.

## Report
B00 §D. Don't modify unrelated working code, and don't break earlier stages.
