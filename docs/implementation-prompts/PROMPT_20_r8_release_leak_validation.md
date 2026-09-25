# PROMPT 20: R8, minified release, and leak validation

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §26 and §33, and the "PROGUARD / R8" and "MEMORY MANAGEMENT" requirements summarised in §17 and §19.

## 1. Objective
Prove the libraries work in minified R8 release builds with **minimal, justified** consumer rules, and prove no Activity, View, NativeAd, or lifecycle leaks across rotation and navigation.

## 2. Context
- The Next-Gen and UMP SDKs ship their own consumer ProGuard rules inside their AARs. Verify this by inspecting the AARs' `proguard.txt` in the Gradle cache and report it.
- The library uses no reflection, so its consumer rules should be (close to) empty.

## 3. Files to create / modify
- `admob-core/consumer-rules.pro`, `admob-views/consumer-rules.pro`, and `admob-compose/consumer-rules.pro`: keep them empty, with explanatory comments, unless a concrete R8 failure demands a rule. Every rule must have a comment naming the failure it fixes.
- `sample-*/proguard-rules.pro`: nothing library-specific should be needed. If something is, that's a library bug; fix it in consumer rules instead.
- `sample-xml/src/androidTest/.../ReleaseSmokeTest.kt`, run against a minified variant: add a `benchmark`-style `minifiedDebug` build type (`initWith(release)`, debuggable, minify on, debug signing) so instrumentation can run against R8-processed code.
- `docs/architecture/R8_REPORT.md`: the findings.

## 4. Files to modify
- Sample build files: add the `minifiedDebug` build type.

## 5. Implementation requirements
1. Build `:sample-xml:assembleMinifiedDebug`, `:sample-compose:assembleRelease`, and `:sample-mixed:assembleRelease -PwithMediation=true`, and run the smoke tests against `minifiedDebug`.
2. Inspect the R8 `missing_rules.txt` and `usage.txt`/`seeds.txt` for any library class stripping that breaks behaviour.
3. Check that `AdBannerView`/`AdNativeView` inflation from XML works under R8. Custom views are referenced from XML; AAPT generates keep rules automatically, so verify that.
4. Leak validation (instrumented, LeakCanary `DetectLeaksAfterTestSuccess`):
   - Banner screen: rotate ×5 and navigate away and back ×5
   - Native: the feed scrolling up and down in `sample-xml` and `sample-compose`
   - Interstitial and rewarded: show → dismiss ×3 (with network)
   - Fragment tab switching ×10
   - Record a heap-dump analysis summary in `R8_REPORT.md`
5. Measure the APK size delta of the libraries: `sample-xml` release without and with the library (use a throwaway branch or a flag that removes the ad usages, and report the numbers). This is informational.

## 6. Public API requirements
None.

## 7. Constraints
No broad `-keep class com.company.admob.** { *; }` rules. No `-dontwarn` without a documented reason.

## 8. Dependencies
None new (LeakCanary was added in Prompts 17/18).

## 9. Tests
The minified smoke tests and leak tests described above.

## 10. Validation commands
```
./gradlew :sample-xml:assembleMinifiedDebug :sample-xml:connectedMinifiedDebugAndroidTest
./gradlew :sample-compose:assembleRelease :sample-mixed:assembleRelease -PwithMediation=true
```

## 11. Acceptance criteria
- [ ] Minified builds pass the smoke tests (or are reported as not run for lack of an emulator, with a manual check instead).
- [ ] Consumer rules are minimal, and each one is justified.
- [ ] Zero leaks reported by LeakCanary in the scenarios, or each leak is fixed and re-tested.
- [ ] `R8_REPORT.md` is written.

## 12. Completion report
Per 00_WORKING_RULES §D, including what the SDK AARs' bundled proguard rules contain (summarised).
