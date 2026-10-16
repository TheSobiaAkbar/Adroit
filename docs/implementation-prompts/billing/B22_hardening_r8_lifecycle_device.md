# B22 — Hardening: lifecycle, process death, R8, leaks and device tests

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Prove the billing stack survives real-world conditions, and that minified release builds work.

## Context
PROMPT 20 did this for ads (R8 report `docs/architecture/R8_REPORT.md`, leak tests, device tests). Follow the same
approach, and extend the report rather than starting a new one.

## Prerequisites
B20, B21.

## Work items
1. **Consumer R8 rules.** PBL ships its own rules. Add `consumer-rules.pro` entries only if needed: enum `valueOf`
   by name for `ProductDefinition` / `PaywallDefinition` lookups from JSON (prefer `entries.firstOrNull { it.name ==
   … }`, which needs no rule). Build both samples with `minifyEnabled` release and smoke-test them in demo mode.
   **Note:** demo mode is off in release, so smoke-test with the release build type marked debuggable in a local-only
   variant, or with real Play testing.
2. **Process death.** Robolectric plus device tests:
   - kill during the Play sheet (`adb shell am kill` after backgrounding);
   - kill between grant and acknowledge;
   - kill with an unconfirmed consumable.

   Each must recover correctly (B06/B07 rules).
3. **Configuration changes.** Rotation, dark mode, font scale 2.0 and RTL (`ar`) on both paywall UIs. Fix any
   clipping.
4. **Connection chaos.** The fake port injects a disconnect mid-query, during launch, and during acknowledge.
   Assert no duplicates and no hangs.
5. **Leaks.** Add a LeakCanary instrumentation test that opens and closes each paywall 5 times in both samples.
6. **Strict mode.** No disk reads on main except the documented initialize prefs read. Measure `initialize` time
   (target under 15 ms on the Pixel 7a) and add it to `docs/audits/PERFORMANCE_AUDIT.md`.
7. **Lint.** Add `@MainThread` and `@CheckResult` where appropriate. The library lint must be clean.

## Verify
```
./gradlew :sample-xml:assembleRelease :sample-compose:assembleRelease
./gradlew testDebugUnitTest
./gradlew :sample-xml:connectedDebugAndroidTest :sample-compose:connectedDebugAndroidTest
```

The last line runs only if the device is unlocked: check the keyguard first and ask the user.

## Acceptance criteria
- [ ] Release builds of both samples start and show a paywall.
- [ ] The process-death matrix passes, with real results reported.
- [ ] No leaks.
- [ ] The R8 and performance docs are updated.

## Report
B00 §D.
