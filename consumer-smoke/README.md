# consumer-smoke

A separate Gradle build, not part of the main settings. It's a minimal host app that depends only on
`io.github.thesobiaakbar:adroit-compose` and `adroit-billing-compose` from `mavenLocal()`, calls `Adroit.initialize`
and `start`, shows an `AdBanner`, initializes `AdroitBilling` in demo mode and compiles a `Paywall` call.

```bash
./gradlew publishToMavenLocal
./gradlew -p consumer-smoke assembleDebug verifyConsumerClasspath
./gradlew -p consumer-smoke assembleDebug verifyConsumerClasspath -Psmoke.compose=false   # the XML host
```

What it proves:

- The published POMs and module metadata resolve transitively: adroit-core, adroit-views, `ads-mobile-sdk` and UMP.
- adroit-billing and Play Billing resolve transitively at runtime, and Play Billing isn't on the compile classpath.
- No Google Mobile Ads or UMP artifact is on the host's compile classpath. A source file referencing
  `com.google.android.libraries.ads.mobile.sdk.MobileAds` fails to compile.
- A host on AGP 9's built-in Kotlin 2.2.10 can consume the libraries (built with Kotlin 2.4.20, metadata 2.2).
- A Java 11 host: `JavaSmoke.java` calls the Java surface (ADR-013), and both compilers emit Java 11 bytecode.

## Compatibility matrix

Gradle properties pick the row: `smoke.agp`, `smoke.kotlin` (the Kotlin Gradle plugin, applied on AGP 8),
`smoke.compose` (`false` builds a View-based host on adroit-views and adroit-billing-views, with no Compose),
`smoke.billing` (`false` leaves billing out: it needs Kotlin 2.2), `smoke.compileSdk` and
`smoke.activity` (the host's own `androidx.activity`). Results for 1.0.0-rc1 are in
[docs/audits/API_STABILITY_AUDIT.md](../docs/audits/API_STABILITY_AUDIT.md) (check 10).

```bash
# 1. The default: AGP 9.1.1, Kotlin 2.2.10, Compose host, compileSdk 37 (also run by CI).
./gradlew -p consumer-smoke assembleDebug verifyConsumerClasspath
# 2. Kotlin 2.1 on AGP 8.13 (Gradle 8.13), View host.
gradle -p consumer-smoke assembleDebug verifyConsumerClasspath -Psmoke.agp=8.13.2 -Psmoke.kotlin=2.1.20   -Psmoke.compose=false -Psmoke.billing=false -Pandroid.suppressUnsupportedCompileSdk=37
# 3. The lowest AGP, 8.1.4 (Gradle 8.11.1 on JDK 17: AGP 8.1 can't use JDK 21 or newer), compileSdk 34.
gradle -p consumer-smoke assembleDebug verifyConsumerClasspath -Psmoke.agp=8.1.4 -Psmoke.kotlin=2.1.20   -Psmoke.compose=false -Psmoke.billing=false -Psmoke.compileSdk=34 -Psmoke.activity=1.8.2
```

Rows 2 and 3 need a local Gradle 8 (`gradle`), because AGP 8 doesn't run on the repository's Gradle 9 wrapper.

It reads the version from the main build's `gradle.properties` (`adroit.version`). The Android SDK location
comes from `consumer-smoke/local.properties` (`sdk.dir=…`, not committed; copy the root one) or `ANDROID_HOME`.
