# Mediation demo: an AdMob Mediation adapter with the Next-Gen SDK

CompanyAdMob contains no mediation code. A host app adds adapters as ordinary dependencies. This sample shows the one
extra step the Next-Gen SDK requires, behind a Gradle flag that's off by default.

## What the flag adds

`./gradlew :sample-mixed:assembleDebug -PwithMediation=true` adds, in `sample-mixed/build.gradle.kts`:

1. One adapter: `com.google.ads.mediation:applovin:13.6.4.2` (the latest on Google's Maven repository when this was
   written; it brings `com.applovin:applovin-sdk:13.6.4`).
2. The global exclusion of the legacy Google Mobile Ads SDK:

   ```kotlin
   configurations.configureEach {
       exclude(group = "com.google.android.gms", module = "play-services-ads")
       exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
   }
   ```

Without the flag, neither the adapter nor the exclusion is present.

### Why the exclusion is needed

Mediation adapters are still published against the legacy SDK: the AppLovin adapter's POM depends on
`com.google.android.gms:play-services-ads:25.4.0`. The Next-Gen SDK (`ads-mobile-sdk`) already contains the classes
adapters use, under the same names, so both on one classpath can't be packaged. Google's
[Next-Gen migration guide](https://developers.google.com/admob/android/next-gen/migration) requires the exclusion for
every app that uses mediation. Put it in the **app** module, applied to every configuration as above.

## The failure without the exclusion

Reproduced once with `-PwithMediation=true -PmediationSkipExclusion=true` (a switch that exists only for this
reproduction). The build stops at `checkDebugDuplicateClasses`, listing 73 duplicate classes. The key lines:

```
* What went wrong:
Execution failed for task ':sample-mixed:checkDebugDuplicateClasses'.
> A failure occurred while executing com.android.build.gradle.internal.tasks.CheckDuplicatesRunnable
   > Duplicate class com.google.android.gms.ads.AdError found in modules ads-mobile-sdk-1.4.0.aar -> ads-mobile-sdk-1.4.0-runtime (com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.4.0) and play-services-ads-api-25.4.0.aar -> play-services-ads-api-25.4.0-runtime (com.google.android.gms:play-services-ads-api:25.4.0)
     Duplicate class com.google.android.gms.ads.MobileAds found in modules ads-mobile-sdk-1.4.0.aar -> ads-mobile-sdk-1.4.0-runtime (com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.4.0) and play-services-ads-api-25.4.0.aar -> play-services-ads-api-25.4.0-runtime (com.google.android.gms:play-services-ads-api:25.4.0)
     Duplicate class com.google.android.gms.ads.RequestConfiguration found in modules ads-mobile-sdk-1.4.0.aar -> ads-mobile-sdk-1.4.0-runtime (com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.4.0) and play-services-ads-api-25.4.0.aar -> play-services-ads-api-25.4.0-runtime (com.google.android.gms:play-services-ads-api:25.4.0)
     …
```

The clashing classes come from `play-services-ads-api`, which `play-services-ads` pulls in. Excluding
`play-services-ads` removes it too.

The repository's dependency guard catches the same mistake before packaging:

```
Execution failed for task ':sample-mixed:checkModuleDependencies'.
> :sample-mixed resolves forbidden dependencies (docs/architecture/02_ARCHITECTURE.md §8):
    - com.google.android.gms:play-services-ads:25.4.0 (on debugRuntimeClasspath, debugUnitTestRuntimeClasspath, releaseRuntimeClasspath)
```

## Verifying the build

```bash
./gradlew :sample-mixed:assembleDebug -PwithMediation=true
./gradlew checkModuleDependencies -PwithMediation=true
./gradlew :sample-mixed:dependencies -PwithMediation=true --configuration debugRuntimeClasspath
```

In the `dependencies` output, `play-services-ads` and `play-services-ads-api` don't appear at all: Gradle drops
excluded modules from the tree rather than marking them. `play-services-ads-identifier` (the advertising ID, used by
the AppLovin SDK) is a different artifact and is expected.

`checkModuleDependencies` checks every runtime classpath (debug, release, unit test and instrumented test), not only
release, so the exclusion must apply to all of them. `configurations.configureEach` does that.

## Verifying the adapter on a device with Ad Inspector

1. Install the mediation build and register the device as a test device (its hashed ID is printed in Logcat the
   first time it requests an ad).
2. Open the app, go to the **Settings** tab (it also says whether the build has the adapter) and tap
   **Open Ad Inspector** (`CompanyAdMob.diagnostics.openAdInspector`).
3. Under the ad units, open a mediation group: AppLovin should be listed with its adapter version and initialization
   status. Ad Inspector can also send single-network test requests.

## Testing real mediation

This sample never serves mediated ads, even with the flag:

- It uses Google's **demo ad units** in DEVELOPMENT (and in STAGING with `useDemoAdUnits = true`), and demo units only
  serve Google ads.
- Mediated ads don't carry a "Test Ad" label.

To test mediation for real, use:

1. The **STAGING** environment with your **real ad unit IDs** (in a private build, never committed),
2. **Registered test devices** (`testing { testDeviceIds += … }`, which STAGING with real IDs requires),
3. A mediation group in the AdMob UI that includes the network, and
4. **The network's own test mode** (for AppLovin, its test device settings in the AppLovin dashboard).

Never commit real ad unit IDs, app IDs or network credentials to this repository.
