# consumer-smoke

A separate Gradle build, not part of the main settings. It's a minimal host app that depends only on
`com.company:admob-compose` from `mavenLocal()`, calls `CompanyAdMob.initialize` and `start`, and shows an `AdBanner`.

```bash
./gradlew publishToMavenLocal
./gradlew -p consumer-smoke assembleDebug verifyConsumerClasspath
```

What it proves:

- The published POMs and module metadata resolve transitively: admob-core, admob-views, `ads-mobile-sdk` and UMP.
- No Google Mobile Ads or UMP artifact is on the host's compile classpath. A source file referencing
  `com.google.android.libraries.ads.mobile.sdk.MobileAds` fails to compile.
- A host on AGP 9's built-in Kotlin 2.2.10 can consume the libraries (built with Kotlin 2.4.20, metadata 2.2).

It reads the version from the main build's `gradle.properties` (`companyAdmob.version`). The Android SDK location
comes from `consumer-smoke/local.properties` (`sdk.dir=…`, not committed; copy the root one) or `ANDROID_HOME`.
