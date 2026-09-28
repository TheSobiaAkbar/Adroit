# API stability audit and 1.0.0-rc1 freeze

PROMPT 27, run on 2026-09-28 against branch `feat/companyadmob-foundation` (base commit `b18353d`). Scope: the public
API of the five library modules, as recorded in their `api/*.api` dumps (1,577 + 49 + 27 + 265 + 37 lines, read line
by line), their sources, and what a host needs to consume them. Architecture references: `02_ARCHITECTURE.md` §31 and
§32, `03_PUBLIC_API.md`.

The outcome: the API is frozen as `1.0.0-rc1`. The rules that now apply to it are in `docs/RELEASE.md` "API
stability" (ADR-019), and Java support is defined in ADR-013.

## Result

| | Count |
|---|---|
| PASS | 3 (checks 1, 7, 9) |
| PASS after a fix in this audit | 6 (checks 2, 3, 4, 5, 6, 8) |
| WARN (owner decision below) | 1 (check 10) |
| Open FAIL | 0 |

## Checks

"C" is `admob-core/src/main/kotlin/com/company/admob`, and "T" is `admob-core/src/test/kotlin/com/company/admob`.

| # | Check | Status | Evidence | Fix |
|---|---|---|---|---|
| 1 | No Google types in any `.api` | PASS | `NoGoogleTypesInPublicApiTest` now reads all five dumps (it read only admob-core's) and finds no `com/google/android/libraries/ads`, `…/ump` or `…/gms/ads`. The only escape hatch, `GoogleRequestCustomizer` (`@GoogleSdkEscapeHatch`, plus `AdvancedOptions.googleRequestCustomizer`), takes its builder as `Any`, so it adds no Google type to the dump. admob-firebase's `FirebaseRemoteConfig`/`FirebaseAnalytics` constructor parameters are the allowed exception (ADR-017). | Test widened to every module. |
| 2 | Accidental public declarations | PASS after fix | Every dump entry compared with `03_PUBLIC_API.md`. **Found:** `AdPlacementBase`, the placement classes' shared superclass, was public API (a `sealed class` with a synthetic constructor): Kotlin requires a public class's superclass to be public. Intended extras, now listed in 03: `CompanyAdMobVersion`, `AndroidAdLogger` (the default logger, for hosts that wrap it), `AdMobAppId` (the typed `AdMobConfig.appId`), the `PlacementConfig` subclasses and placement builders (configuration read-back), `CompanyAdMobInternal` (behind `@InternalCompanyAdMobApi`, level ERROR, ADR-012/016), and admob-testing's handle fakes. | `AdPlacementBase` removed: each placement class implements `AdPlacement` with private shared helpers (`C/AdPlacement.kt`). Also removed from `compose_stability.conf`. |
| 3 | Binary-evolution hazards | PASS after fix | **Data classes:** the remaining ones are frozen value types per ADR-011 (`Reward`, `SdkErrorDetails`, `AdRevenue`, `RetryPolicy`, the one-field states, results and errors, `BannerSize.InlineAdaptive`/`Collapsible`, `FakeCall`s). **Found:** `FrequencyRule` was a data class and had already grown once (`counter`, 25A), which broke its `copy`. **Sealed types and enums:** `AdError`, `AdEvent`, `AdsState` and `AdAvailability` already said "may grow; include an else branch"; 18 others said nothing or only half of it. **Interfaces hosts implement** (`AdLogger`, `AdEventListener`, `AdConfigProvider`, `NativeAdRenderer`, `GoogleRequestCustomizer`) are all `fun interface`s. **Interfaces the library implements** (`AdsClient`, the format APIs, handles) could gain members that break hand-written fakes. **Default arguments:** common in the format APIs, options classes and view helpers (each has a `$default` bridge in the dumps); interface members can't take `@JvmOverloads`, and seven public constructors and one function with defaults lacked it. | `FrequencyRule` is a regular class with `equals`/`hashCode`/`toString`. Every sealed type and enum has one uniform "Stability:" line, closed or open (list in RELEASE.md). Policy (ADR-019): new interface members always get a default body, which `jvmDefault = NO_COMPATIBILITY` makes a Java default method, so existing implementations still link (chosen over `@SubclassOptInRequired`, which would break hosts' own fakes today); no parameter is ever added to an existing function; `@JvmOverloads` added to the seven constructors (and one function) that lacked it. |
| 4 | Naming consistency | PASS after fix | `show`/`preload`/`isReady`/`availability`/`clear` are uniform across the full-screen formats (`FullScreenAdApi<P>`); app open has one ad per app, so its `availability` is a property. Every parameter holding a placement is `placement`. `Ad` prefix: composables and views are `AdBanner`, `AdNative`, `AdBannerView`, `AdNativeView`; handles are `BannerAdHandle`, `NativeAdHandle`. **Found:** `showOnColdStart` called its result callback `onComplete`, every other show `onResult`. Kept on purpose: `start(onComplete: (AdsState))` reports a state, not a show result; `showPrivacyOptions(onDismissed)` and `openAdInspector(onClosed)` name their event; `isPrivacyOptionsRequired: StateFlow<Boolean>` follows the common `isX: StateFlow<Boolean>` idiom. Durations are `kotlin.time.Duration` everywhere; event payloads use unit-suffixed primitives (`timestampMillis`, `latencyMillis`, `valueMicros`), which analytics sinks take as they are. | `onComplete` renamed `onResult` (`C/AppOpenApi.kt`, the implementations, admob-testing, `docs/APP_OPEN.md`). |
| 5 | Java interop | PASS after fix | `CompanyAdMob.initialize` and `client` are `@JvmStatic`; the `AdsClient` members can't be (they override the interface), so Java uses `CompanyAdMob.getClient()`. Callbacks are Kotlin function types: Java lambdas return `Unit.INSTANCE`. **Found:** `showOnColdStart` had a `Duration` parameter, so its JVM name was mangled (`showOnColdStart-8Mi8wO0`) and Java couldn't call it; it's a facade entry point that a Java splash screen calls. Config `Duration` setters and `awaitReady` are mangled too. | ADR-013: Kotlin-first with a defined Java surface. Added `showOnColdStart(activity, maxWaitMillis, onResult)` (a default method); the other `Duration` members stay Kotlin-only, and Java apps set timings in the ads file. `T/../JavaFacadeCalls.java` (test sources) compiles the whole Java surface, and consumer-smoke's `JavaSmoke.java` compiles it against the published artifacts. |
| 6 | Nullability and threading documented on every public member | PASS after fix | New `T/PublicKDocTest`: explicit API mode puts `public` on every public declaration, so it scans the five modules' sources and fails on any without KDoc (annotations and multi-line annotations in between are allowed; class-body properties may be documented by the class's `@property` tag). First run: 19 hits, 5 real (two `companion object`s, `PlacementEvent.adUnitId`, and the primary constructors of `FirebaseAnalyticsEventListener` and `FirebaseRemoteConfigProvider`); the rest were `@property`-documented properties and multi-line annotations. Threading is stated on each API interface (`AdsClient`, `FullScreenAdApi`, handles: "any thread; callbacks on main") and in 03 §3.12. | KDoc added to the five; the test now runs in `testDebugUnitTest`. |
| 7 | Opt-in markers on unsettled surfaces | PASS | Reviewed every surface added since 0.1.0 (ad profiles, ID rotation, counter, splash, collapsible banners, native styles, admob-firebase). They carry the product requirement's one-file integration and are documented, tested and exercised on a device (PROMPT 25A, 26), so none is marked experimental: a warning on the main integration path would be noise. `@ExperimentalCompanyAdMobApi` stays available for future surfaces, `@InternalCompanyAdMobApi` guards `CompanyAdMobInternal`, and `@GoogleSdkEscapeHatch` guards the customizer. | None. |
| 8 | Deprecation policy in RELEASE.md | PASS after fix | RELEASE.md had versioning rules but no deprecation policy. | "API stability" section: deprecate in minor N with `ReplaceWith`, keep until the next major, never change in a minor or patch; plus the default-argument, interface, sealed-type, data-class, Java and consumer-requirement rules. |
| 9 | BCV baseline regenerated as `1.0.0-rc1` | PASS | `companyAdmob.version=1.0.0-rc1`; `./gradlew apiDump`, then the diff reviewed line by line (below); `apiCheck` passes in the CI run. The five dumps committed with this change are the rc1 baseline; RELEASE.md says every later change is a reviewed `apiDump` diff. | None. |
| 10 | Consumer compatibility matrix | WARN | Results below. **Found and fixed:** the AARs published `minCompileSdk=37` (AGP 9 defaults it to the library's compileSdk), which forced every app onto Android 17's SDK; now 34 (`consumerMinCompileSdk`, what lifecycle 2.11 needs). **Open:** admob-compose imports Compose BOM 2026.09.00, and Compose 1.12 and lifecycle-*-compose 2.11 require AGP 9.1.0 and compileSdk 37, so Compose hosts need both. | `aarMetadata.minCompileSdk` in the library convention plugin. consumer-smoke gained the matrix switches. The Compose floor is an owner decision (follow-ups). |

## Consumer matrix (check 10)

`consumer-smoke` against `1.0.0-rc1` from mavenLocal, `assembleDebug verifyConsumerClasspath`, minSdk 24, Java 11
source and target for both javac and kotlinc (class files checked: major version 55). Each row also compiles
`JavaSmoke.java`. Commands are in `consumer-smoke/README.md`.

| Row | AGP | Gradle | Kotlin | compileSdk | Host | Result |
|---|---|---|---|---|---|---|
| Default (CI) | 9.1.1 | 9.3.1 | 2.2.10 (built-in) | 37 | Compose (admob-compose) | PASS |
| Kotlin 2.1 | 8.13.2 | 8.13 | 2.1.20 | 37 | Views (admob-views) | PASS |
| AGP 8.7 | 8.7.2 | 8.11.1 | 2.1.20 | 35 | Views | PASS |
| Lowest AGP | 8.1.4 | 8.11.1 (JDK 17) | 2.1.20 | 34 | Views | PASS |
| Compose on AGP 8 | 8.13.2 | 8.13 | 2.1.20 | 37 | Compose | FAIL: 10 AAR-metadata errors, every one "requires Android Gradle plugin 9.1.0" (Compose 1.12.1, lifecycle-*-compose 2.11.0) |
| Before the minCompileSdk fix | 8.7.2 | 8.11.1 | 2.1.20 | 36 | Views | FAIL: admob-core and admob-views "require … version 37 or later" |

- **Lowest AGP:** 8.1.1, from lifecycle 2.11's AAR metadata (`minAndroidGradlePluginVersion=8.1.1`; Next-Gen's own is
  7.3.1). 8.1.4 is the oldest 8.1 patch, and it builds. AGP 8.1 can't run on JDK 21 or newer (its `androidJdkImage`
  transform fails on JDK 24), so that row runs on JDK 17, as AGP 8.1 requires.
- **Kotlin 2.1:** a 2.1.20 compiler reads the libraries' 2.2 metadata, as ADR-010 expects.
- **Java 11 target:** every row. **minSdk 24:** every row.
- A host's own dependencies can raise its floor: consumer-smoke's `androidx.activity` 1.13 needs AGP 8.9.1, so the two
  oldest rows use activity 1.8.2 (`-Psmoke.activity`).

## API dump diff (1.0.0-rc1 against b18353d)

- admob-core: `AdPlacementBase` removed; the six placement classes now declare `equals`, `getFormat`, `getKey`,
  `hashCode`, `toString`; `AppOpenApi.showOnColdStart(Activity, long, Function1)` added (a default method);
  `BannerOptions(BannerSize)` and `AdError.Internal(String)` constructors added (`@JvmOverloads`); `FrequencyRule`'s
  `component1-3` and `copy` removed.
- admob-firebase: `FirebaseAnalyticsEventListener(Context)`, `(FirebaseAnalytics)`, `FirebaseAnalyticsOptions(boolean)`,
  `FirebaseRemoteConfigProvider(FirebaseRemoteConfig)` and `(FirebaseRemoteConfig, boolean)` added.
- admob-testing: `FakeNativeAdHandle(NativePlacement)` and `block()` added.
- admob-views, admob-compose: unchanged.

Everything else in the dumps matched the review. `03_PUBLIC_API.md` was brought in line with them: the added
surfaces since the original proposal, the nullable size and layout parameters, the regular classes, the removed base
class, and a new admob-firebase section (§3.13) and stability section (§3.14).

## Breaking changes made

Listed in CHANGELOG `[1.0.0-rc1]` "Changed (breaking)":
1. `AdPlacementBase` removed (binary: the placement classes' superclass changed; source: only code naming it).
2. `FrequencyRule` is a regular class: no `copy` or `componentN`.
3. `showOnColdStart`'s callback parameter renamed `onResult` (source: only calls naming the argument).

## Follow-ups

- **Compose floor (owner decision):** admob-compose could import an older Compose BOM, which is a minimum and not a
  pin, so Compose hosts on AGP 8 and compileSdk 34 to 36 could use it too. That changes a published dependency and
  needs the Compose tests and the device runs again; until then, the README states AGP 9.1 and compileSdk 37 for it.
- **Matrix in CI:** only the default row runs in CI. The AGP 8 rows need a Gradle 8 distribution, and the 8.1 row a
  JDK 17. They would fit the nightly workflow.
