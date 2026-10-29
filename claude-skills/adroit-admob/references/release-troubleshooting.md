# Release checklist, troubleshooting, known traps

## Release checklist

**AdMob**
- [ ] Every `AppAds` entry has its real unit IDs, of the right format, either in code or in Firebase. A release build
      logs a warning for each ad still on test units.
- [ ] The manifest's app ID is the real one (release placeholder), not Google's sample `~3347511713`.
- [ ] Release runs `AdEnvironment.PRODUCTION`. Watch logcat for `NON_PRODUCTION_IN_RELEASE_BUILD`.
- [ ] The GDPR (and US states) message is published in AdMob → Privacy & messaging.
- [ ] `app-ads.txt` is on the developer website listed in Play.
- [ ] Play Console data safety declares advertising ID / ads.
- [ ] Nobody on the team clicks real ads. Use test devices (`testing { testDeviceIds }`) in STAGING if real units must
      be checked.

**Firebase**
- [ ] The release `google-services.json` is in `app/`; the debug one is in `src/debug/`, if separate.
- [ ] Every parameter is published, ideally from the generated phase file (`remote-config.md` §6).
- [ ] `AdSettings.adsEnabled` is `true`, or not set.

**Build**
- [ ] Release is minified (`isMinifyEnabled = true`), and the release APK/AAB **starts and shows ads on a device**.
      Adroit ships its own consumer R8 rules; the app adds none for Adroit.
- [ ] **`versionCode` is raised** for every release, because `AppUpdate.forceBelowVersionCode` compares it.

## The R8 `packageScope` crash (AGP 9 optimization DSL)

**Symptom:** the release crashes at launch with `IllegalAccessError` inside androidx.startup. Debug works.

**Cause:**
- The app used AGP's new `optimization { packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**") }`.
- R8 moved a Kotlin stdlib class (`kotlin.text.StringsKt__StringBuilderKt`) to the root package, but left its
  package-private parent class behind.

**Fix:** PhotoRecovery commit `27049cb`. It needs all three:
```kotlin
// app/build.gradle.kts, release
optimization { enable = true; packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**") }
proguardFiles("proguard-rules.pro")
```
```
# app/proguard-rules.pro
-keeppackagenames androidx.**,kotlin.**,kotlinx.**
```
```properties
# gradle.properties: Google lists this as required for packageScope
android.r8.gradual.support=true
```
This is not an Adroit bug, but it shows up as "the app with ads crashes in release". Always run the minified release
build once before shipping.

## Mediation

Mediation adapters bring the legacy `play-services-ads`, which clashes with the Next-Gen SDK
(`Duplicate class com.google.android.gms.ads…`). Exclude it globally:
```kotlin
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}
```
Use only adapters that support the Next-Gen SDK, and check AdMob's mediation docs for each network.

## Troubleshooting

| Symptom | Look at |
|---|---|
| No ads at all | `Adroit.state` must reach `Ready`; check that `start()` ran (SplashFlow does it). `Unavailable(CONSENT_NOT_OBTAINED)`: consent. `Unavailable(AD_FREE_USER)`: `setAdFree(true)`. Offline? |
| A full-screen ad is always `NotShown(NotReady)` | Nobody called `AdSlots.load` for it. Profiles load only when asked; add `load` in `onResume`, or `loadAgain = true`. |
| An interstitial never shows | The counter isn't due (`CounterNotReached`), a cap or timer applies (`FrequencyCapped(retryAfter)`), `minSecondsBetweenFullScreenAds` is set, or it is turned off in Firebase. |
| Consent form never shows | No message is published in AdMob; consent was already given (clear data); or the region doesn't need one (debug builds act as the EEA unless `debugBuildGeography = DISABLED`). |
| `APP_ID_MISSING` / UMP crash about the app ID | The manifest `meta-data` is missing or misnamed. |
| `Failed(InvalidConfiguration)` in release | PRODUCTION with demo IDs, the sample app ID, or debug geography. |
| The app open ad never shows | Only on returns, never on the first start. The resumed Activity is excluded; `skipNextForeground` is pending; a cooldown or the 5 s after another full-screen ad; or no ad was cached (call `AdSlots.load(AppAds.AppOpenAd)`). |
| The app open ad shows after share, settings or a purchase | Add the skip-on-leave pattern (`formats.md` §9). |
| An expanded collapsible banner covers the next screen | Adroit older than `af73065` (rc6 included); take a JitPack commit at `b0df3c8` or later. |
| A banner shimmer flashes for premium users | Adroit older than `b0df3c8` (rc6 included); take that commit or later. Also check that `setAdFree` runs synchronously before the UI. |
| Ads come back for a premium user after a restart | `setAdFree` isn't persisted. Re-apply it every launch, from the saved entitlements, synchronously (adroit-billing bridge). |
| A native ad reloads on scroll or rotation | Missing `slotKey` in a `LazyColumn`; a container without an `android:id` for `attachNativeAd`; or a RecyclerView without `NativeAdHolder`. |
| A native CTA has the wrong colour | A plain `<Button>` was inflated as a `MaterialButton`; write `<android.widget.Button>`. |
| Video ads don't play | Hardware acceleration was turned off for the Activity or the window. |
| Runtime errors from OkHttp | OkHttp 5 isn't supported by the ads SDK; keep OkHttp 4. |
| `NO_FILL` on new ad units | Normal for the first hours or days of a new unit or app; test units always fill. |
| Firebase changes don't apply | The parameter name doesn't match the enum entry exactly; the value isn't JSON (a logged warning); debug needs `minimumFetchIntervalInSeconds = 0`; values apply on the next launch. |
| `remoteConfig` set but ignored | A local variable named `remoteConfig` shadows the builder property inside `adMobConfig { }`. |

## API traps

- **Sealed types gain values between releases:** `AdError`, `AdEvent`, `AdsState`, `BannerSize`, `NativeStyle`,
  `AdFormat`, `FakeCall`. Give every `when` over them an `else` branch.
- **Recompile after upgrading.** rc5 added counter parameters to `AdProfile.interstitial`; rc6 added
  `closeButton`/`closeIcon` to `banner`, `native` and `AdSettings`, and added `AdEvent.CloseClicked`.
- **Banner and native have no `onClose`.** Use `AdEvent.CloseClicked`.
- **`AdSlots.showFullScreen` doesn't show rewarded ads.** Use `Adroit.showRewarded`, `Adroit.rewarded.show` or the
  launcher.

## Where the docs disagree with the code (trust the code)

- `docs/XML.md` mentions `NativeTemplates.Small/Medium/Large`. These were removed in rc2. The real names are
  `SmallButtonEnd`, `SmallButtonBottom`, `LargeButtonBottom`, `LargeCompact` and `MediaStart`.
- `docs/CONFIGURATION.md` says the native default is `MEDIUM`. It is `NativeStyle.LARGE_BUTTON_BOTTOM`, and there is
  no `MEDIUM`.
- `docs/TESTING.md` lists `setInitialBannerState()` / `setNativeAssets()`. In Kotlin these are the properties
  `initialBannerState` and `nativeAssets`.
- The `docs/XML.md` banner size table leaves out `collapsibleBottom` and `collapsibleTop`, which exist.
- A Firebase `"style": "small" | "medium" | "large"` is read as an unregistered layout name, and the ad falls back to
  its own style.
