# R8, minified release and leak validation (PROMPT 20)

Toolchain: AGP 9.1.1 (R8 full mode, the default), Kotlin 2.4.20, Next-Gen GMA SDK 1.4.0, UMP 4.0.0.
Device: Pixel 7a, Android 17 (API 37), over adb.

## 1. Consumer rules

| Module | Rules | Why |
|---|---|---|
| `adroit-core` | 1 rule: `-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }` | Fixes a launch crash in every minified app (§3.1). Not needed by the library's own code. |
| `adroit-views` | none | Its custom views are kept by AAPT (§4). |
| `adroit-compose` | none | No reflection. |
| `adroit-testing` | none | Test-only artifact. |

No broad `-keep class io.github.thesobiaakbar.adroit.** { *; }`, and no `-dontwarn` in any library.

The samples' `proguard-rules.pro` files contain no library-specific rules. sample-xml has two rule files that apply
only to its instrumented-test setup, never to release (§5).

## 2. What the Google SDK AARs bundle

- **`ads-mobile-sdk-1.4.0.aar` `proguard.txt`** (5.6 KB):
  - Field keeps for its shaded protobuf messages, and the Gson rules for its internal `@GsonClass` models.
  - A keep (names only; shrinking still allowed) for its already-obfuscated `ads_mobile_sdk.**` code.
  - Keeps for mediation adapter entry points: `MediationAdapter`, `Adapter`, `CustomEvent` and similar, which it
    instantiates by reflection.
  - Keeps for `AppMeasurementSdk` (the Firebase Analytics link) and for the `androidx.webkit` methods it calls
    reflectively.
  - `-dontwarn` for API-level-guarded platform classes (`TelephonyDisplayInfo`, `Surface`, `android.adservices.**`),
    Tink, Amazon and Guava annotations.
- **`user-messaging-platform-4.0.0.aar` `proguard.txt`** (306 B): a field keep for its shaded protobuf messages.

Neither covers the Room issue below, which comes from a transitive dependency.

## 3. R8 findings

### 3.1 Launch crash from Room 2.2.5's rules (fixed in adroit-core)

Every minified sample crashed in `Application` startup, before any library code ran:

```
java.lang.RuntimeException: Unable to get provider androidx.startup.InitializationProvider
Caused by: androidx.startup.StartupException: java.lang.RuntimeException: Failed to create an instance of androidx.work.impl.WorkDatabase
Caused by: java.lang.RuntimeException: Failed to create an instance of androidx.work.impl.WorkDatabase
    at androidx.room.Room.getGeneratedImplementation(Room.java:100)
    at androidx.work.impl.WorkDatabase.create(WorkDatabase.java:155)
    at androidx.work.WorkManagerInitializer.create(WorkManagerInitializer.java:39)
```

- **Chain:** `ads-mobile-sdk:1.4.0` → `androidx.work:work-runtime:2.7.0` → `androidx.room:room-runtime:2.2.5`.
- **Why it crashes:** Room creates `WorkDatabase_Impl` through its no-arg constructor by reflection. Room 2.2.5 ships
  `-keep class * extends androidx.room.RoomDatabase`, which in R8 full mode keeps the class but not its constructor.
  `usage.txt` listed `androidx.work.impl.WorkDatabase_Impl: public void <init>()` as removed.
- **Fix:** the consumer rule in §1. Later Room versions ship it themselves, so it's redundant when a host already has
  a newer Room, and harmless.
- **Every host is affected:** any app that minifies with the library would have crashed this way. Upgrading
  WorkManager for hosts was rejected, because library dependency versions become consumer minimums.
- **Verified** on the device: the minified sample-xml starts and reaches `Ready` and `MainActivity`.

### 3.2 Class names in `toString` (fixed)

`AdPlacementBase.toString` and `PlacementConfig.toString` used `this::class.simpleName`, which R8 renames: a
placement would log as something like `a(home_banner)`. Both now spell out the public type name, with unit tests.
The remaining class-name uses are the app open Activity exclusion (it compares two runtime names, so renaming is
consistent) and log text.

### 3.3 Missing rules, seeds and usage

- **`missing_rules.txt`:** none for any app variant (sample-xml release and minifiedDebug, sample-compose release,
  sample-mixed release with and without mediation).
- **`seeds.txt` (library entries):**
  - `AdBannerView` and `AdNativeView`, by AAPT.
  - `BannerContainer`, by the default file's rule for `View` constructors.
  - The library enums, by the default enum rules.
- **`usage.txt`:** about 515 library entries. Everything fully removed is one of:
  - API the sample doesn't call (diagnostics types, `NativeAdRenderer`, the DSL marker annotations);
  - internal interfaces merged into their single implementation (the `internal.port` ports, `Clock`);
  - inlined constant holders (`GoogleDemoAdUnits`, `R` classes);
  - compiler-generated lambdas, `WhenMappings` and companions.

  Nothing removed is reached by reflection, and the device run confirms the behaviour.

## 4. XML inflation under R8

AAPT generates `-keep` rules for every class referenced from a layout, so `AdBannerView` and `AdNativeView` keep
their names (`mapping.txt`: `io.github.thesobiaakbar.adroit.views.AdBannerView -> io.github.thesobiaakbar.adroit.views.AdBannerView`).
`ReleaseSmokeTest` checks the inflated views' class names on the device.

## 5. Instrumented tests against R8 code

Every sample has a `minifiedDebug` build type: `initWith(release)`, debuggable, debug-signed, and falling back to
the libraries' release variant. sample-xml uses it as its `testBuildType`, so all its instrumented tests run
against R8-processed code.

Testing a minified app needs two sample-only rule files, neither of which touches the libraries or release:

- **`proguard-test-rules.pro` (test APK):** `-dontwarn` for LeakCanary's optional integrations with the pre-AndroidX
  support library and WorkManager multiprocess, which R8 reported as missing classes.
- **`proguard-minified-debug-rules.pro` (minifiedDebug app):** keeps the Kotlin standard library and the app's
  `R` fields. The test APK uses the app's copy of the stdlib, and reads resource IDs as `R` fields; R8 had removed
  both (`NoClassDefFoundError: kotlin/LazyKt`, `NoSuchFieldError: nav_home`).

Two more test-setup fixes were needed on the device:

- sample-compose pins Espresso 3.7.0, because Compose `ui-test` brings 3.5.0. On Android 17, 3.5.0 fails with
  `NoSuchMethodException: android.hardware.input.InputManager.getInstance`.
- The tests close ads by tapping the ad's "Close" button. Back presses sent every 250 ms queued up while the ad
  ignored them, then closed the app's own screens once it closed. The helper also keeps the screen on while a
  sample screen is showing.

## 6. Device results (Pixel 7a, Android 17)

### Instrumented tests

| Module (build) | Test | Result |
|---|---|---|
| sample-xml (minifiedDebug, R8) | `ReleaseSmokeTest`: starts, and XML-inflated `AdBannerView`/`AdNativeView` keep their class names | pass |
| | `StartupSmokeTest`, `InterstitialFlowTest` | pass |
| | `BannerLeakTest`: Home banner rotated ×5, Details (banner from code, native) opened and left ×5 | pass, no leaks |
| | `FeedScrollLeakTest`: native feed swiped down and up ×3, then the tab left | pass, no leaks |
| | `FragmentLeakTest`: tab switching ×10, rotation ×3 | pass, no leaks |
| | `FullScreenLeakTest`: interstitial ×3 and rewarded ×3, each shown and dismissed | pass, no leaks; all 6 ads showed |
| sample-compose (debug) | `PlaygroundNavigationTest`: all 12 screens, each rotated | pass, no leaks |
| | `NativeNoReloadTest`: rotation ×3 on the Native screen | pass, no new native request |
| | `NativeFeedLeakTest`: the LazyColumn feed scrolled through ×3, then the screen left | pass, no leaks |
| sample-mixed (debug) | `InteropSmokeTest` (PROMPT 19) | pass |

**Heap analysis:** LeakCanary's `DetectLeaksAfterTestSuccess` checks for retained objects after each passing test,
and dumps and analyzes the heap only if any remain after garbage collection. No test triggered a heap dump: no
Activity, Fragment, View, `ViewModel`, banner or native ad was retained in any scenario above.

### Minified release launches

Installed, launched, then uninstalled:

- sample-xml release (3,751,196 B): reaches `MainActivity`.
- sample-compose release (3,438,757 B): reaches `Ready` and loads demo interstitial and native ads.
- sample-mixed release with `-PwithMediation=true` (8,271,444 B, including the AppLovin SDK): reaches its main screen.

None logged a crash.

### Library fix found on the device

`HardwareAccelerationCheck` warned "isn't hardware accelerated: video ads won't play" for every banner or native
ad created in `onCreate`, even with acceleration on in the manifest. It read the window's
`FLAG_HARDWARE_ACCELERATED`, which Android sets only when the window is added, after `onCreate`. It now reads the
manifest value from the Activity's `ActivityInfo`; the window flag still counts for runtime opt-ins. Robolectric
tests cover it.

## 7. APK size

Two throwaway release apps (R8 and resource shrinking on, same AppCompat and Material dependencies) built in a
temporary worktree, never committed:

| App | Release APK |
|---|---|
| Baseline: one Activity, no ads | 1,311,572 B |
| The same plus Adroit: `initialize`, `start`, an `AdBannerView`, an interstitial | 3,441,045 B |
| **Delta** | **+2,129,473 B (about 2.03 MiB)** |

Almost all of the delta is the Google Mobile Ads Next-Gen and UMP SDKs and their dependencies (WorkManager, Room,
webkit, Play Services), which any AdMob integration carries. For comparison, the full sample-xml release APK is
3,723,416 B.

## 8. Billing (B22)

Toolchain: as above, plus Play Billing Library 9.1.0 (its AAR ships its own consumer rules). Device: the same Pixel 7a.

### 8.1 Consumer rules

| Module | Rules | Why |
|---|---|---|
| `adroit-billing` | none | No reflection. Play Billing keeps what it needs itself. |
| `adroit-billing-compose` | none | No reflection. |
| `adroit-billing-views` | none | `AdroitPaywallActivity` is kept by its manifest entry, `PaywallView` by AAPT when a layout uses it (`mapping.txt`: both keep their names). |
| `adroit-billing-firebase` | none | No reflection. |
| `adroit-billing-testing` | none | Test-only artifact. |
| `adroit-remote-config` | none | No reflection; Firebase ships its own rules (setup S05). |
| `adroit-update` | none | No reflection; Play In-App Updates ships its own rules. consumer-smoke's release build (R8) calls `AdroitUpdate.check` (setup S05). |

**Paywall and product names.** The library finds a definition by its name with `entries.firstOrNull { it.name == … }`
(Remote Config keys, the paywall Activity's Intent extra, `exitPaywall`), never `valueOf` by reflection, so no rule is
needed. R8 renames the enum classes (`SamplePaywalls -> q23`) but keeps each constant's name: the release `classes.dex`
of both samples still holds `SettingsUpgrade`, `HomeUpgrade`, `CreditsStore`, `PremiumLifetime` and `Coins100`, so the
Firebase keys match in a minified app. sample-compose's own `SamplePaywalls.valueOf(name)` (its navigation route) is
covered by the default enum rules.

**`missing_rules.txt`:** none for sample-xml release and minifiedDebug, or sample-compose release.

### 8.2 Minified builds on the device

- **Release (R8 full mode, obfuscated):** sample-xml release (5,002,390 B) started 10 times in a row for the timing in
  `PERFORMANCE_AUDIT.md` (billing section), with no crash. sample-compose release (4,387,688 B) started, connected to
  Play and reached its splash ad, with no crash. Demo mode is off in release, and the sample's products
  don't exist in Play Console yet, so no paywall can show there: Play reports every product as not found.
- **minifiedDebug (R8, debuggable, demo mode on):** sample-compose minifiedDebug (8,627,435 B) started, closed its
  splash ad and showed the Onboarding paywall with the demo prices; Continue opened the demo purchase sheet. sample-xml
  minifiedDebug runs `PaywallLeakTest` (every paywall opened and closed 5 times, §8.3).
- **Caveat found:** AGP 9 warns that a debuggable build type gets no R8 optimization or obfuscation ("All code
  optimizations and obfuscation are disabled for debuggable builds"). minifiedDebug is shrunk only, so §5's instrumented
  tests run against shrunk, not obfuscated, code. Only the release builds above prove obfuscated code.

### 8.3 Leak tests

| Module (build) | Test | Result |
|---|---|---|
| sample-xml (minifiedDebug) | `PaywallLeakTest`: each of the 5 paywalls opened in `AdroitPaywallActivity` and finished, 5 times (25 openings, every template) | pass, no leaks |
| sample-compose (debug) | `PaywallLeakTest`: each of the 5 paywalls composed and removed, 5 times; logcat shows `PaywallShown` 5 times for each | pass, no leaks |

`ActivityScenario.close()` didn't work for the paywall Activity on Android 17: it moves the Activity behind a
trampoline and waits for `onStop`, which never came (stuck in PAUSED for 45 s). The test finishes the Activity as the
paywall does itself, then waits for DESTROYED.
