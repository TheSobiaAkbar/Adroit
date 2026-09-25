# PROMPT 25: Security and privacy audit

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §23, §29, §30, and `01_SDK_RESEARCH.md` §1.6.

## 1. Objective
Audit the complete repository for security and privacy defects, fix every confirmed issue, and produce `docs/audits/SECURITY_PRIVACY_AUDIT.md`. **This is an audit plus fixes, not a rubber stamp.** Every check needs evidence: a file:line reference, a command output, or a test.

## 2. Checks (each gets PASS / WARN / FAIL with evidence)
Privacy and consent:
1. No ad request path is reachable before `canRequestAds()` is true and the SDK is initialized. Trace every call site of `AdsSdk.load*` and `createBannerView().load`, and show the readiness guard for each.
2. `requestConsentInfoUpdate` runs on every `start()`, and no consent value is cached by the library (grep for SharedPreferences usage; only `company_admob_state/launch_count` and `gad_rdp` are allowed).
3. Revoking consent via privacy options stops ads (test exists and passes).
4. The privacy-options entry-point state is exposed and documented.
5. Debug geography and test hashed IDs are impossible in PRODUCTION (tests).
6. Age treatment uses `setAgeRestrictedTreatment`. The deprecated TFCD/TFUA are unused (grep).
7. RDP key handling (write before init, removal).
8. No TC string, GPP string, advertising ID, or user identifier is read or logged (grep for `IABTCF_`, `IABGPP_`, `AdvertisingIdClient`, `getString(` on default prefs).
9. The documentation covers Play data safety, the `AD_ID` permission for child-directed apps, and Consent Mode ordering.

Security:
10. No secrets in the repo or history: `git log -p | grep -Ei "(password|secret|token|api[_-]?key)"`, reviewed; `git grep 2838681778929171` is empty.
11. CI secrets are used only via `secrets.*`, least-privilege workflow permissions, and actions pinned by SHA.
12. Logging: PRODUCTION defaults to WARN, ad unit IDs are redacted in PRODUCTION, and there's no `println` and no stray `Log.`.
13. The escape hatch (`@GoogleSdkEscapeHatch`) is opt-in at ERROR level, and the customizer failures are contained.
14. Exported components: the libraries' merged manifests add no exported Activities or Services of their own (inspect `build/intermediates/merged_manifest`).
15. Dependency vulnerabilities: run the OWASP dependency-check, or `./gradlew dependencies` plus the GitHub advisory review for the resolved versions (at least OkHttp and Guava from the Next-Gen POM). Report the findings.
16. `rewardId` is random and not persisted. There are no persistent identifiers.

## 3. Files to create / modify
- `docs/audits/SECURITY_PRIVACY_AUDIT.md`: a table of check → status → evidence → fix commit/file.
- Fix code, tests, or docs for every FAIL, and add regression tests where possible.

## 4. Constraints
Don't weaken a test to make it pass. Don't remove the policy disclaimer.

## 5. Validation commands
The full CI command set from Prompt 22, plus the greps listed above.

## 6. Acceptance criteria
- [ ] All 16 checks were evaluated with evidence.
- [ ] Every FAIL is fixed and re-verified. Each WARN has an owner and a follow-up note.
- [ ] The CI command set is green after the fixes.

## 7. Completion report
Per 00_WORKING_RULES §D, plus the audit table.
