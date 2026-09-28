# PROMPT 28: Real Firebase, enum ad definitions, format swapping, and the splash flow

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `docs/AD_PROFILES.md`,
`docs/architecture/05_AD_PROFILES.md` and `docs/CONSENT.md`.

The public API was frozen for 1.0.0-rc1 (PROMPT 27). Everything here is **additive**: no existing public signature
changes, and the `res/raw/ads_config.json` path keeps working. Update the `.api` dumps and the CHANGELOG
(`[Unreleased]`, Added) as each phase lands.

Each phase is one commit. Run `./gradlew check` (and the relevant device tests on the attached Pixel 7a) before
committing.

## Decisions (the product owner's answers, 2026-09-28)
- `sample-xml` uses the real `google-services.json` of Firebase project `sample-ads-plugin`, registered for package
  `com.company.admob.sample.xml`. The file is committed; the `staging` build type loses its `.staging`
  applicationId suffix so every build type matches the registered app.
- Format swapping from Firebase: `interstitial` ↔ `app_open` anywhere in the app, `native` ↔ `banner` anywhere.
  Rewarded and rewarded interstitial are never swapped. (App open ads shown at an in-app moment are outside
  AdMob's intended use; the docs say so, and the choice is the app owner's.)
- Premium (ad-free) users: no consent form, no ad, a 3-second splash.
- Consent gets at most 8 seconds. After that we cancel the form ourselves and continue: it is never shown late.

## 28A. Real Firebase in sample-xml
- Version catalog: the `com.google.gms.google-services` plugin. `sample-xml` applies it and depends on
  `:admob-firebase` (Remote Config and Analytics through the BoM).
- `SampleAdsConfig` registers `FirebaseRemoteConfigProvider`. Debug builds use a 0-second minimum fetch interval.
- **Accept:** `assembleDebug`, `assembleStaging`, `assembleRelease` succeed; on device, logcat shows Firebase
  initializing and a Remote Config fetch.

## 28B. Network monitor and fetch-once remote config
- `NetworkMonitor` (core, public): a `StateFlow<NetworkState>`: `Unavailable`, `ConnectedNoInternet`
  (connected but not `NET_CAPABILITY_VALIDATED`, for example Wi-Fi behind a captive portal), and `Connected` with the
  transport (`WIFI`, `CELLULAR`, `ETHERNET`, `OTHER`). Plus `isOnline` and `isWifi`/`isCellular` helpers.
- `RemoteConfigSession` (admob-firebase, public): `fetchAndActivate` at app start. On success it sets `isFetched`
  and never fetches again in this process. On failure it tries again when an Activity is created and when the network
  becomes available, until one succeeds. It never blocks the caller. It exposes `isFetched: StateFlow<Boolean>`.
- Activated values reach ads through the existing `FirebaseRemoteConfigProvider` flow: new values apply to the
  next ad request.
- **Accept:** unit tests with fakes: the fetch runs once after a success, retries on Activity or network after a
  failure, and doesn't run concurrently.

## 28C. Enum ad definitions
- `AdDefinition` (core, public interface): `val key: String` (an enum supplies `name`) and `val defaults: AdProfile`.
- `AdProfile`: a typed default per format: `interstitial(...)`, `appOpen(...)`, `rewarded(...)`,
  `rewardedInterstitial(...)`, `banner(...)` and `native(...)`, with the same fields as the JSON. Plus `AdSettings`
  defaults, including the splash timers (28E).
- `adMobConfig { profiles(AppAds.entries, settings = ...) }` builds the placements from the enum. Firebase JSON
  overrides each entry by `key`, exactly as for the file.
- A debug-only helper logs the Firebase JSON for every entry, and `docs/firebase/remote_config_template.json` is
  generated for the console's "Publish from a file".
- **Accept:** an enum works as a drop-in for the file (the same parser tests pass against both); an invalid default
  fails fast outside PRODUCTION.

## 28D. Format swapping
- A remote `format` may change within its group: full screen `{interstitial, app_open}`, inline `{banner,
  native}`. Any other change (including anything involving rewarded formats) is ignored with a warning, as today.
- Format-neutral entry points:
  - `CompanyAdMob.fullScreen.show(activity, definition) { FullScreenResult }` shows whichever format is in effect,
    with the counter, timer and caps of the effective profile.
  - `CompanyAdMob.fullScreen.showOnSplash(...)` does the same for the splash.
  - An inline slot (`AdSlotView` for Views, `AdSlot` for Compose) renders a banner or a native ad in the
    effective format and style.
- When a remote update changes an ad's format or turns it off, the preloaded ad for that key is discarded.
- **Accept:** tests for each allowed swap, rejected swaps, discarding a preloaded ad, and a slot re-rendering in the
  new format on its next load.

## 28E. Splash controller and splash UI
- `SplashController` (core, public, lifecycle-aware, survives rotation through a ViewModel in the sample). It
  emits a `SplashState`: `GatheringConsent`, `Loading(progress 0..100)`, `ShowingAd`, `Done`.
  1. At the start, `RemoteConfigSession` is already fetching (from `Application.onCreate`); nothing waits for it.
  2. Consent (skipped for premium or offline): a UMP update and form, capped at `consentTimeoutSeconds` (8). At the
     limit, the form is cancelled and never shown afterwards.
  3. The plugin initializes whatever the consent outcome.
  4. The splash timer starts: `splashSeconds` from `AdSettings` (default 11), `premiumSplashSeconds` (3) for
     premium users, and `offlineSplashSeconds` (3) when offline. It uses the value active at that moment. The
     splash ad request starts at the same time (not for premium or offline).
  5. When the ad loads: progress to 100, cancel the timer, show the ad, and `Done` when it's dismissed or if showing
     fails. When the timer ends first: `Done`, and the late ad is dropped. No navigation while an ad is on screen.
  6. Safety: an absolute cap (consent limit + splash timer + 5 s) finishes the splash whatever happens, except
     while an ad is on screen. If the app is in the background, the ad isn't shown and `Done` is delivered on
     resume.
- Sample UI: a centered animated vector drawable, the app name below it, and a horizontal progress bar with a
  percentage label above it at the bottom, hidden until consent settles.
- **Accept:** fake-clock tests for every path: consent granted, denied, already given, timed out, premium,
  offline, ad loaded, ad failed, timer won, backgrounded, rotation. Plus device runs online, in airplane mode, and on
  Wi-Fi without internet.

## 28F. Documentation and verification
- `docs/AD_PROFILES.md` (enum, swapping), a new `docs/SPLASH.md`, `docs/REMOTE_CONFIG.md` (fetch session),
  a Firebase console guide with the template, and CHANGELOG.
- Full device pass on the Pixel 7a with real Firebase values changed from the console.
