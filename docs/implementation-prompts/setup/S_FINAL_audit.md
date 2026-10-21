# S-FINAL — Setups audit

Read `docs/implementation-prompts/setup/S00_WORKING_RULES.md` first and follow it. Read-only, except for the report.
Fix nothing in this prompt: list every FAIL with a proposed fix.

## Checks (PASS/FAIL with evidence for each)
1. **No duplication:**
   - one Remote Config fetch loop: grep `fetchAndActivate` and `registerDefaultNetworkCallback` in main sources;
   - no second copy of online checks between the Firebase modules;
   - one premium source: no `isPremium =` passed in the samples or guides.
2. **Dependency rules:**
   - `checkModuleDependencies` passes;
   - the IAP-only dependency set from the guide resolves no `adroit-core` and no Mobile Ads SDK: run
     `dependencies` on consumer-smoke or a throwaway config;
   - the AdMob-only set resolves no billing.
3. **API:** `apiCheck` is green, and the diff since the series started is additions plus the documented
   `RemoteConfigSession` move.
4. **Tests:** `./gradlew testDebugUnitTest` is green; report the counts per module.
5. **Release:** both samples' `assembleRelease` and the consumer-smoke release build succeed.
6. **Guides:** for each of the six, walk the steps against the real APIs. Every symbol it names exists with that
   signature, and every JSON key and value parses (feed it through the library's parsers in a test or a scratch
   check).
7. **Device (Pixel 7a):** re-run S06 checks 1–5 on one sample, and the update check's no-Play path.
8. **Firebase defaults:** with no remote values at all, the app behaves exactly as the code defaults (show mode,
   flexible update).

## Report
- A table of the checks with PASS/FAIL and evidence.
- For each FAIL, a proposed fix, and which S-prompt it belongs to.
- Open risks.
