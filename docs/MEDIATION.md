# Mediation

## Supported model

CompanyAdMob supports **AdMob Mediation**: AdMob's own mediation, configured in the AdMob UI, with third-party
network adapters added to your app ([Google](https://developers.google.com/admob/android/next-gen/mediation)). The
library contains no mediation code and needs no configuration for it: adapters are ordinary dependencies of your app.

Third-party mediation platforms that use AdMob as a demand source have their own Next-Gen SDK compatibility. Google
lists AdMost, AppLovin MAX, Chartboost Mediation, DT FairBid, Gravite, Nimbus, TopOn, TradPlus VisiM and Unity
LevelPlay as compatible ([Next-Gen migration](https://developers.google.com/admob/android/next-gen/migration#before-you-begin)).

## Adding an adapter

1. Set up the network and a mediation group in the AdMob UI, and add the network to your Privacy & messaging
   messages ([Consent](CONSENT.md#set-up-messages-in-the-admob-ui)).
2. Add the adapter, for example `implementation("com.google.ads.mediation:applovin:13.6.4.2")`.
3. **Exclude the legacy Google Mobile Ads SDK globally.** Adapters still depend on `play-services-ads`, and the Next-Gen
   SDK already contains the classes they need ([Google](https://developers.google.com/admob/android/next-gen/migration)).
   In your app module:

<!-- snippet: sample-mixed/build.gradle.kts#mediation-exclusion -->
```kotlin
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}
```

### Without the exclusion

The build fails at `checkDebugDuplicateClasses` with dozens of lines like:

```
Duplicate class com.google.android.gms.ads.MobileAds found in modules ads-mobile-sdk-1.4.0.aar -> ads-mobile-sdk-1.4.0-runtime (com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.4.0) and play-services-ads-api-25.4.0.aar -> play-services-ads-api-25.4.0-runtime (com.google.android.gms:play-services-ads-api:25.4.0)
```

`sample-mixed/MEDIATION_DEMO.md` reproduces it, and builds the AppLovin adapter with `-PwithMediation=true`.

## Testing mediation

- Google's demo ad units only serve Google ads: with them (DEVELOPMENT, or STAGING's default), no mediated ad is
  ever requested.
- Test with STAGING, your real ad units (in a private build, never committed), `testing { testDeviceIds += … }`, and
  each network's own test mode.
- Mediated ads don't show the "Test Ad" label, so enable test mode in every network to avoid invalid traffic
  ([Google](https://developers.google.com/admob/android/next-gen/test-ads)).
- **Ad Inspector** shows each ad unit's mediation waterfall, the adapters present and their initialization status, and
  can send single-network test requests ([Ad Inspector](https://developers.google.com/admob/android/next-gen/ad-inspector)):

```kotlin
CompanyAdMob.diagnostics.openAdInspector(activity) { error -> /* null when closed normally */ }
```

It opens on test devices only.

## Age treatment and adapter versions

Adapters forward `privacy { ageTreatment }` only from certain versions:
[minimum adapter versions](https://developers.google.com/admob/android/next-gen/targeting#forward-age-treatment-with-admob-mediation).

## Network-specific extras

Some networks need extras on the ad request. The escape hatch `advanced { googleRequestCustomizer = … }`
(`@OptIn(GoogleSdkEscapeHatch::class)`) receives the Next-Gen request builder for each load. It exposes Google types,
so use it only when a network requires it.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
