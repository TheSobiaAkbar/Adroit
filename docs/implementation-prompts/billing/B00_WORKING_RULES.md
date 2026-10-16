# Standing rules for every billing prompt (B01–B24, B-FINAL)

Every billing prompt starts by telling Claude to read this file. It adds to `../00_WORKING_RULES.md`, which still
applies. Where the two differ for billing modules, this file wins.

## A. Before changing anything
1. Read these first:
   - `docs/architecture/06_BILLING_ARCHITECTURE.md` (the source of truth for billing);
   - `docs/architecture/02_ARCHITECTURE.md` §7 (modules and dependency rules);
   - `../00_WORKING_RULES.md`.

   If the prompt and the architecture disagree, **stop and report the conflict**.
2. Inspect the real repository:
   - `settings.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/convention`;
   - every module the prompt touches;
   - the earlier billing prompts' completion reports, if pasted.

   Follow the structure that **exists**, not the one you expect. If a file the prompt names is missing or was named
   differently in an earlier stage, use the existing one and say so.
3. **Play Billing API**: check every class and method you call against
   `https://developer.android.com/reference/com/android/billingclient/api/package-summary`, the integration guide
   `https://developer.android.com/google/play/billing/integrate` and the release notes. The catalog's
   `playBilling` version is the truth. Never use removed APIs: `queryPurchaseHistory`, `querySkuDetailsAsync`, the
   no-arg `enablePendingPurchases`, `SkuDetails`, `queryPurchasesAsync(String, …)`. If the API differs from
   06_BILLING_ARCHITECTURE, use the real API, keep the public contract, and report the difference.
4. Never assume a dependency version. Read it from the catalog, or check `maven-metadata.xml` on Google Maven or
   Maven Central before adding one.

## B. While changing
5. Package `io.github.thesobiaakbar.adroit.billing[.compose|.views|.firebase|.testing]`. Resource prefix
   `adroit_billing_`. Explicit API mode. `internal` by default.
6. **No Play Billing or Firebase types in public signatures.** Play types stay behind the internal `PlayBillingPort`.
7. `adroit-billing` must not depend on Compose, AppCompat, Material, Fragment, Firebase, Room, Retrofit, any DI
   framework, or **any `adroit-*` ads module**. UI modules depend only on `adroit-billing` (+ their toolkit).
8. Never log or emit raw purchase tokens, order IDs together with account IDs, or anything that identifies a person.
   Use a token hash (`sha256(token).take(12)`) for correlation in logs.
9. Never grant an entitlement for `PENDING`, `UNSPECIFIED_STATE`, or `isSuspended` purchases, or because a query
   failed.
10. Never fabricate prices, currencies, trial lengths or savings. All of them come from `ProductDetails`.
11. Coroutines: the library scope is `SupervisorJob() + Dispatchers.Main.immediate`. Play callbacks are resumed on
    main. Inject `Clock` and dispatchers for tests. No `GlobalScope`, no `runBlocking` outside tests.
12. Any Activity passed in is used only for the call and never stored in a field, a lambda that outlives the call,
    or a static.
13. KDoc on every public declaration. Comment density otherwise matches the surrounding Adroit code.
14. Smallest coherent change. Don't refactor or rename unrelated code, and **don't change the ads modules** unless
    the prompt says so.

## C. After changing
15. Run, in this order:

    ```
    ./gradlew :adroit-billing:build
    ```

    then the touched modules' `testDebugUnitTest`, then `lint`, then `apiDump` (review the diff) and `apiCheck`, then
    `checkModuleDependencies` if it exists.

    Library modules use `allWarningsAsErrors`.
16. If you can't run a command (for example no device), say so and give the exact command for the user to run.
    **Never report a test as passing that you didn't run and observe.**
17. Review your own diff before the report. Don't commit unless asked. Commit messages use
    `feat(billing): B<NN> <title>`.

## D. Completion report (always)
Report, in this order:
1. Summary (3–6 bullets).
2. Files created, modified and deleted.
3. Public API diff (`.api`), or "none".
4. Commands run, with pass/fail counts.
5. Acceptance checklist, each item ✅/❌ with a one-line reason.
6. Play API differences found vs. the architecture doc.
7. Deviations and why.
8. Open issues and risks.
9. Versions observed: PBL, Kotlin, AGP, compileSdk, minSdk.

The stage is DONE only when every acceptance item is ✅.
