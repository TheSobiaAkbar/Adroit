# B-FINAL — Billing production-readiness audit

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
An independent audit of the finished billing stack against the Definition of Done. It produces a PASS/WARN/FAIL
scorecard in `docs/audits/BILLING_READINESS_AUDIT.md`, and fixes every FAIL.

## Prerequisites
B01–B24 (B16 optional).

## Checks (each scored with evidence: file:line, test name, command output)
1. **Play API currency**: the PBL version is the latest stable or within one minor version; there are no removed or
   deprecated APIs (grep for the B00 §A.3 list and `setSubscriptionReplacementMode`); the Play Console minimum
   version deadline is met.
2. **Purchases**: consumable, non-consumable and subscription; pending; suspended; acknowledge within the window;
   consume after confirm; idempotent under concurrency (cite the tests).
3. **Subscriptions**: multiple products and base plans; durations are data-driven (grep for hardcoded day counts);
   upgrade and downgrade; manage link; in-app messages.
4. **Trials and offers**: no path can advertise an unavailable trial (cite B12 tests); fallbacks are documented.
5. **Firebase**: every paywall field is remote-controllable, including the close delay (0/N/−1); malformed config is
   safe; there's one fetch per session; Firebase can't grant entitlements or change prices.
6. **Attribution**: placements are separate from product/plan/offer/campaign; they survive rotation and process
   death; UNKNOWN is used when unrecoverable.
7. **Security and privacy**:
   - no tokens or raw IDs in logs, events or prefs;
   - no secrets in the repo (`git grep` for `private_key`, `service_account`, API keys);
   - HTTPS-only verifier;
   - client-only limits are documented;
   - no grant on failure or pending.

   Update `docs/audits/SECURITY_PRIVACY_AUDIT.md`.
8. **API stability**: explicit API, `.api` dumps reviewed, no Play or Firebase types public, experimental items
   annotated. Update `docs/audits/API_STABILITY_AUDIT.md`.
9. **Modularity**: the dependency guard passes; an ads-only app pulls no billing; a billing-only app pulls no ads;
   the XML app pulls no Compose.
10. **UX and accessibility**: the disclosure is always present; the close or escape hatch is always reachable on
    error; TalkBack order; 48dp targets; RTL; font scale.
11. **Samples and docs**: both samples are at parity; the guides were followed end to end; the snippets compile.
12. **Tests**: list the counts per module from a real run. Any skipped test is listed with the reason.

## Verify
```
./gradlew clean build testDebugUnitTest lint apiCheck checkModuleDependencies
./gradlew -PincludeDocsSnippets :docs-snippets:build
```

Plus the device matrix from B20/B21, if a device is available.

## Acceptance criteria
- [ ] No FAIL remains. Each WARN has an owner and a follow-up.
- [ ] The scorecard cites real command output, never assumed results.

## Report
B00 §D, plus the scorecard summary.
