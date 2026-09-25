# PROMPT 03: Configuration system, environments, and test-ID safety

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §20 (all), §21 (the `AdOverrides` shape only), §23, §25, and §30, `03_PUBLIC_API.md` §3.6, and `01_SDK_RESEARCH.md` §1.5.

## 1. Objective
Implement the strongly typed, immutable `AdMobConfig` with a Kotlin DSL (and a Java-usable builder), per-placement configuration, environment handling, and a `ConfigValidator` that makes shipping test IDs to production, or production IDs in development, hard to do.

## 2. Context
- The domain model (Prompt 02) exists.
- Google's demo IDs are listed verbatim in `01_SDK_RESEARCH.md` §1.5. **Use exactly those strings. Don't invent any.**
- The app ID is read from the manifest meta-data `com.google.android.gms.ads.APPLICATION_ID`, which UMP requires. A config-provided app ID must match it.

## 3. Files to create (`admob-core/src/main/kotlin/com/company/admob/config/`)
- `AdMobConfig.kt`: an immutable class (private constructor), `adMobConfig {}` DSL, and a nested `Builder` with Java-friendly setters. Nested option groups: `TestingOptions`, `PrivacyOptions`, `FrequencyOptions`, `LoggingOptions`, `AdvancedOptions` (holds the `@GoogleSdkEscapeHatch GoogleRequestCustomizer?`, `showTimeout`, `failFast` override, `disableSdkCrashReporting`).
- `AgeTreatment.kt` (`AdEnvironment.kt` already exists in `config/` since PROMPT 02; reuse it), `MaxAdContentRating.kt`, `DebugGeography.kt`, `PreloadStrategy.kt`
- `AdUnitId.kt`: a `@JvmInline value class` with an `isWellFormed` check (`^ca-app-pub-\d{16}/\d{10}$`) and `AdMobAppId` (`^ca-app-pub-\d{16}~\d{10}$`)
- `PlacementConfig.kt`: a sealed family per format (`BannerPlacementConfig(size default)`, `InterstitialPlacementConfig`, `RewardedPlacementConfig`, `RewardedInterstitialPlacementConfig`, `AppOpenPlacementConfig(cooldown, minLaunchesBeforeFirstShow, excludedActivityClassNames, showOnColdStartAllowed, suppressAfterFullScreenAd)`, `NativePlacementConfig(poolSize 1..3)`). The common fields are `adUnitId`, `enabled`, `frequency: FrequencyRule`, `preload: PreloadStrategy`, `retry: RetryPolicy?` (null means the format default), and `expiry: Duration?`.
- `PlacementsBuilder.kt`: `banner(placement, adUnitId) {}`, `interstitial(...)`, `rewarded(...)`, `rewardedInterstitial(...)`, `appOpen(...)`, `native(...)`. A duplicate placement key is a validation error.
- `RetryPolicy.kt`, `FrequencyRule.kt`, `ExpiryDefaults.kt`, `RetryDefaults.kt`: the default values from architecture §12 and §25. Put them in `internal object FormatDefaults`.
- `remote/AdConfigProvider.kt`, `remote/AdOverrides.kt` (+ `PlacementOverride`), `remote/StaticConfigProvider.kt`. These are the types only; they're applied in Prompt 15.
- `internal/config/GoogleDemoAdUnits.kt`: the demo IDs per `AdFormat` + `SAMPLE_APP_ID` + `DEMO_PUBLISHER_PREFIX = "ca-app-pub-3940256099942544"`
- `internal/config/ConfigValidator.kt`: returns `ValidationResult(errors: List<ConfigIssue>, warnings: List<ConfigIssue>)`
- `internal/config/EffectiveConfig.kt`: the resolved view used by the engines. It resolves the **effective ad unit ID per placement** after environment substitution, the effective retry/expiry/frequency, and later (Prompt 15) the remote overrides.
- `internal/config/ManifestAppIdReader.kt`: reads the meta-data through `PackageManager.getApplicationInfo(pkg, GET_META_DATA)` (use the API 33+ `ApplicationInfoFlags` overload when available, and handle `NameNotFoundException`).

## 4. Files to modify
- `admob-core/api/admob-core.api` (apiDump)
- `api/AdsClient.kt`: nothing yet (`initialize` takes the config; it's wired in Prompt 06)

## 5. Implementation requirements
1. **Environment matrix** (architecture §20.3), implemented in `EffectiveConfig`:
   - `DEVELOPMENT`: always substitute the demo ID for the placement's format (banner: anchored adaptive demo ID, or the fixed demo ID for `Standard`/`MediumRectangle`; native: the native demo ID). Test device IDs are applied.
   - `STAGING`: `testing.useDemoAdUnits` defaults to true. If it's false, real IDs are used and `testDeviceIds` must be non-empty (validation error otherwise).
   - `PRODUCTION`: real IDs only. Test device IDs are ignored, with a warning.
2. **Validation errors**: a missing/malformed app ID (from config or manifest), a config app ID ≠ the manifest app ID, a malformed ad unit ID, duplicate placement keys, placement format ≠ config function, `poolSize` out of 1..3, invalid durations (negative), and `maxAttempts < 0`. In PRODUCTION only: any ad unit ID or app ID with the demo publisher prefix, `useDemoAdUnits = true`, `debugGeography != DISABLED`. In STAGING with real IDs: empty `testDeviceIds`.
3. **Validation warnings**: PRODUCTION with non-empty `testDeviceIds`, a non-PRODUCTION config when the app isn't debuggable (the check takes `isDebuggable: Boolean` as input, so it's testable), PRODUCTION when the app is debuggable, and zero placements configured.
4. `failFast` default: true for DEVELOPMENT and STAGING, false for PRODUCTION. The validator never throws; the caller (Prompt 06) decides.
5. Config is immutable, and `equals`/`hashCode` cover all fields (needed for the idempotent `initialize`).
6. The Java builder must be usable without Kotlin lambdas for common cases (`Builder().environment(...).addInterstitial(placement, "id").build()`). Keep the DSL as the primary API.
7. `excludeActivities(vararg Class<out Activity>)` stores class **names** (strings), not `Class` refs, to avoid retaining class loaders.
8. `GoogleSdkEscapeHatch` customizer storage only; applied in Prompt 04/08.

## 6. Public API requirements
All config types in 03 §3.6, plus `AdConfigProvider`, `AdOverrides`, `PlacementOverride`, and `StaticConfigProvider`. Nothing in `internal/` is public.

## 7. Constraints
- There must be exactly one definition of the demo IDs in the codebase (`GoogleDemoAdUnits`).
- No Google SDK imports in `config/` (the demo IDs are plain strings).

## 8. Dependencies
None new.

## 9. Tests
- `GoogleDemoAdUnitsTest`: pins **every** value to the strings in `01_SDK_RESEARCH.md` §1.5 (copy them into the test as literals, with a comment that links the Google page).
- `ConfigValidatorTest`: one test per error and warning in §5.2–5.3 (at least 20 cases), including "production config containing a demo ID fails", "development config with production IDs substitutes demo IDs", and "staging with real IDs and no test devices fails".
- `EffectiveConfigTest`: substitution per format and banner size, and the default resolution for retry, expiry, and frequency.
- `AdMobConfigDslTest`: DSL and Java builder equivalence (the same config → `equals`), immutability, and duplicate key rejection.
- `ManifestAppIdReaderTest` (Robolectric): present, missing, and malformed.

## 10. Validation commands
```
./gradlew :admob-core:testDebugUnitTest --tests "com.company.admob.config.*"
./gradlew :admob-core:lint apiDump build
```

## 11. Acceptance criteria
- [ ] Unit tests prove that in DEVELOPMENT **no production ad unit ID can reach the effective config**, and that in PRODUCTION **no demo ID can pass validation**.
- [ ] Every default value matches architecture §12/§25 (asserted in tests).
- [ ] The DSL and the Java builder produce equal configs.
- [ ] The API dump was reviewed, and only the config types were added.
- [ ] Build, lint, and tests are green.

## 12. Completion report
Per 00_WORKING_RULES §D, plus a table of the implemented validation rules → test names.
