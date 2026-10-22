# Changelog

All notable changes to Adroit are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/)
with the rules in [docs/RELEASE.md](docs/RELEASE.md). Google SDK upgrades get their own "Google SDK" section.

## [Unreleased]

### Changed
- The splash timer pauses while the splash is in the background, and continues when the user comes back: before, it
  kept counting, so a user who left during the splash came back to the next screen. A splash ad that loaded meanwhile
  shows when they're back. A user who leaves before the timer starts holds back the timer and the splash ad request
  until they return: since Android 15 a background app's network is blocked, and the request failed.

### Fixed
- The splash examples in the docs, the guides and the samples collect `SplashFlow.state` while the screen is
  `RESUMED`, not `STARTED`. After Home, Android can take seconds to stop the Activity; while it was paused but still
  started, the timer could end and open the main screen in the background. Make the same one-word change in your
  splash Activity.

## [1.0.0-rc6] - 2026-10-22

### Added
- **Adroit Billing**, in-app purchases and paywalls on Google Play Billing Library 9.1.0, in five new modules:
  `adroit-billing` (products, purchases, entitlements, restore, attribution, paywall rules and `PaywallController`),
  `adroit-billing-views` (`AdroitPaywall`, `PaywallView`), `adroit-billing-compose` (`Paywall`,
  `rememberPaywallController`), `adroit-billing-firebase` (paywalls from Remote Config, events in Analytics) and
  `adroit-billing-testing` (`FakeBilling`). They don't depend on the ads modules; one line in the app makes premium
  users ad-free. Start with docs/BILLING.md.
- Every paywall setting can be changed from Firebase without a release, including the close button's delay:
  docs/PAYWALL_REMOTE_CONFIG.md (for non-developers), checked against the parser by a unit test.
- Guides: docs/PLAY_CONSOLE_BILLING_SETUP.md, docs/BILLING_TROUBLESHOOTING.md and docs/BILLING_BACKEND.md (the optional server contract, with
  `HttpPurchaseVerifier`). Their code is compiled in `docs-snippets`.
- Both samples sell a subscription, a lifetime product and coins, in demo mode on any device.
- The five billing modules are published with the others (Maven Central, `centralBundle`), and `consumer-smoke`
  builds a Compose and an XML host that initialize billing and call a paywall (`-Psmoke.billing=false` leaves it out
  for the Kotlin 2.1 rows of the compatibility matrix).
- An ad frame around every banner and native ad: a margin on each side (`adroit_ad_margin`, 8dp; override the dimen,
  `0dp` turns it off), applied to the loading shimmer too. A slot without an ad still takes no space. Banners load at
  the width the frame leaves them.
- A close button for banners and native ads (docs/AD_PROFILES.md, "Close button"): `closeButton` (`none`, `top_start`,
  `top_end`, `bottom_start`, `bottom_end`) and `closeIcon` (a drawable name in the app), per ad or in `AdSettings`,
  from the code (`AdProfile.banner`/`native`, `AdSettings`) or Firebase. It sits in its own row above or below the ad;
  a tap emits the new `AdEvent.CloseClicked` and leaves the ad in place. Add a branch for it if you match `AdEvent`
  exhaustively.
- New module `adroit-update`: Google Play in-app updates (Play In-App Updates 2.1.0) controlled by the Firebase
  `AppUpdate` parameter, `flexible` (default), `force` or `off`, with an optional `forceBelowVersionCode`.
  `AdroitUpdate.check` at app start, `readyToInstall` and `install` for a flexible download. docs/APP_UPDATE.md.
- Six step-by-step guides in docs/guides/, one per app: AdMob only, in-app purchases only (a hard paywall), and AdMob +
  in-app purchases, each for Jetpack Compose and for XML. docs/GETTING_STARTED.md picks one in three questions.

### Removed
- docs/NEW_APP_XML.md and docs/NEW_APP_COMPOSE.md: their content is in the six guides and the reference docs they
  link to.

### Changed
- One Remote Config fetch for the whole app: `RemoteConfigSession` moved to the new `adroit-remote-config` module
  (same package and API), which `adroit-firebase` and `adroit-billing-firebase` both use. Billing's own copy of the
  fetch is gone. Apps no longer call `RemoteConfigSession.start`: the first Adroit Firebase module used starts it
  (`RemoteConfigSession.startDefault`). `FirebaseRemoteConfigProvider`'s `autoFetch` is now ignored.
- One premium source: the new `Adroit.isAdFree` (the last `setAdFree` value) is `SplashFlow.create`'s default
  `isPremium`, so apps with the billing bridge no longer pass premium to the splash.
- A splash without an ad (`SplashFlow.create(application, null)`, or the splash ad turned off in Firebase with
  `"enabled": false`) still runs consent, the library's start and the app's routing, now on the short
  `premiumSplashSeconds` timer instead of `splashSeconds` with nothing to wait for. So does a splash whose ads can't
  run this launch (no consent), or whose ad failed to load with no retry left. A splash ad shown later from another screen with `AdSlots.showFullScreen` now gets the
  loading screen. docs/SPLASH.md.
- Native ads on Google's test ad units (no IDs yet, or demo IDs outside PRODUCTION) keep one pool per placement
  instead of sharing one per format, so debug builds load, refill and log each native as release will. Placements
  on the same real ad unit still share a pool.
- `AdProfile.banner`, both `AdProfile.native` functions and the `AdSettings` constructor take the new close button
  parameters (`closeButton`, `closeIcon`) last. Recompile code that calls them.

### Fixed
- A banner whose load failed kept the height reserved for it (`reserveSpace`, the default), leaving an empty gap
  the size of the ad. A failed load now gives the space back and hides the empty ad view; each retry reserves it again.
- `SplashFlow`: when this session's Firebase fetch arrived during the splash timer and swapped the splash ad's format
  (interstitial ↔ app open), the ad requested in the old format was released and none was requested in the new one,
  so the splash waited out its whole timer without an ad. It now requests the ad in its new format.

## [1.0.0-rc5] - 2026-10-15

### Added
- Counter strategies for interstitials and rewarded interstitials, all settable from Firebase
  (docs/AD_PROFILES.md, "Counter and timer"): `counter` also takes a list of max counters used in a loop
  (`"counter": [3, 2]`), `counterStart` (the counter's first value), `counterStep` (what each call adds, decimals
  allowed), `counterResetOnFail` (a due ad that wasn't shown is tried on the next call, or counted again from 0) and
  `loadAhead` (how many calls before it's due the ad loads; 0 = only when due). `AdProfile.interstitial` and
  `AdProfile.rewardedInterstitial` take them as `counters`, `counterStart`, `counterStep`, `counterResetOnFail` and
  `loadAhead`. Recompile code that calls these two functions.

### Changed
- **Public, on Maven Central, under the Apache License 2.0.** Apps need only `mavenCentral()`: no GitHub account,
  invitation or token. The coordinates are unchanged (`io.github.thesobiaakbar:adroit-*`). docs/ACCESS.md is gone,
  and the guides' repository step is now a check that `mavenCentral()` is there.
- The release workflow uploads a signed bundle to the Maven Central Portal (`./gradlew centralBundle`), after
  GitHub Packages. The POM carries the license URL.
- An ad with a counter loads by itself one call before it's due (`loadAhead`, default 1), and on the call it's due
  when it still isn't loaded. Set `"loadAhead": 0` to load only when due.
- The timer strategy: an ad profile with `timerSeconds` and no counter requests its next ad when the timer ends
  after a show, once, and keeps it until it's shown; `AdSlots.load` does nothing while the timer runs. With
  `loadAgain`, the next ad still loads right after the show.
- With a counter set, the timer is also ignored when a show had to wait for its ad.
- Both samples: `LevelCompleteAd` uses the timer strategy (`timerSeconds = 60`, no `loadAgain`), and sample-compose's
  app open ad has no cooldown, like sample-xml's.
- Both samples: the Home screen has a "Counter ad" (`CounterDemoAd`: start 1, max counters 3 then 2) and a "Timer ad"
  (`TimerDemoAd`: 30 s) button, with a line saying what each tap did. Their `AdSettings` no longer set a 30 s gap
  between full-screen ads, which would hide what the counter and the timer decide.

## [1.0.0-rc4] - 2026-10-02

### Added
- `ViewGroup.attachAd(definition, refresh = false)` (adroit-views): shows a banner or a native ad in a container,
  whichever format the ad has (its definition, or Firebase), so code no longer has to match `attachBanner` or
  `attachNativeAd` to the ad's format. It keeps its ad for the screen like `AdSlotView`. sample-xml's Details screen
  uses it.

### Changed
- No timer unless you set one: the app open cooldown (`timerSeconds` / `cooldown`) and the gap between full-screen
  ads (`minSecondsBetweenFullScreenAds` / `frequency.minIntervalBetweenFullScreenAds`) default to 0 instead of
  4 minutes and 30 seconds, and `minLaunchesBeforeFirstShow` to 0 instead of 2, so app open can show from the first
  launch. Set them to keep the old behaviour. The 5 s with no app open after another full-screen ad stays.
- Both samples log each ad's requests, loads, failures, refused shows and impressions to logcat (tag `AdEvents`)
  through an `AdEventListener`.
- sample-compose shows every banner and native ad with `AdSlot(definition)` (Details, Feed), and the Compose guide
  and docs/COMPOSE.md recommend it over `AdBanner`/`AdNative`, which only take an ad of their own format.

## [1.0.0-rc3] - 2026-10-02

### Changed
- `AdBannerView`, `AdNativeView` and `AdSlotView` keep their ad for their screen, like Compose's `AdBanner` and
  `AdNative`: in a ViewModel of the view tree's `ViewModelStoreOwner` (the Fragment, or the Activity), one per
  placement and view id. A Fragment's views recreated (coming back to a tab) and rotation show the same ad with no new
  request; only `load(refresh = true)` asks for another. Previously each new view loaded a new ad.
- `app:adroit_refresh` (and `refreshOnShow`) on `AdBannerView`, `AdNativeView` and `AdSlotView`: ask for a new ad
  each time the view is created again; the shown one stays until the new one replaces it. Default false.
- `slotKey` on the three views: names a view's ad on its screen (default: its `android:id`), for views made in code.
- `attachBanner` and `attachNativeAd` into a container with an `android:id` keep their ad for the screen too:
  rotation shows the same ad. Both take `refresh` (default false): a new ad loads while the shown one stays, replaces
  it once loaded, and the old one is destroyed then. Java callers of `attachNativeAd` with `onResult` now pass
  `refresh` before it.
- `sample-xml`'s tabs keep their Fragments (`detach`/`attach` instead of `replace`), as Navigation does.

### Fixed
- Per-ad Firebase values (`"style"`, `"showLoading"`, `"enabled"`, ad unit IDs, …) were ignored for the whole session:
  the remote provider was collected inside `initialize()`, before the configured placements were known, so
  `FirebaseRemoteConfigProvider` read no ad's parameter. Only `AdSettings` applied. Providers are now collected
  right after `initialize()` returns.

## [1.0.0-rc2] - 2026-09-30

### Changed
- **Renamed to Adroit** (breaking). Maven group `io.github.thesobiaakbar`, artifacts `adroit-core`, `adroit-views`,
  `adroit-compose`, `adroit-testing` and `adroit-firebase`, package `io.github.thesobiaakbar.adroit`, facade `Adroit`,
  resource and XML attribute prefix `adroit_`. See [Migration](docs/MIGRATION.md#from-companyadmob-to-adroit).
- Distributed privately through GitHub Packages, under a proprietary license (`LICENSE`); access by invitation
  ([Getting access](https://github.com/TheSobiaAkbar/Adroit/blob/v1.0.0-rc4/docs/ACCESS.md)). The in-house Maven repository publishing is removed.
- The mixed XML and Compose sample and its guide are removed; `sample-xml` and `sample-compose` remain. The
  new-app guides install Adroit from GitHub Packages.
- `AdBanner` (and `AdSlot` showing a banner) keeps its banner for the whole screen, like `AdNative`: in a ViewModel
  of the current `LocalViewModelStoreOwner`, one per (placement, options, new `slotKey`). Leaving the composition and
  coming back (another tab, a lazy list scrolled away and back) and configuration changes show the same ad with no
  new request; it's destroyed when the screen's ViewModel store is cleared. Off screen it keeps no View or Activity:
  the loaded ad is taken out of its view (`AdView.unregisterBannerAd`) and put into a new one for the Activity it
  comes back in. Banners now work in a `LazyColumn`. Previously a banner was destroyed whenever it left the
  composition, so switching tabs or rotating loaded a new one.
- `AdNative` and `rememberNativeAd` keep every slot's ad until the screen is cleared; the limit of 3 ads per screen,
  which destroyed the least recently used one, is gone.
- `AdSlot` takes a `slotKey`, for banners and native ads in lists.

### Fixed
- The splash timer no longer starts while the consent form is on screen: the consent step's 2-second guard counted the
  form's time, so after 10 seconds the timer, the progress bar and the splash ad request started behind the form. Now
  they start after the user's answer (or when no form appeared in `consentTimeoutSeconds`).
- A consent form whose screen closed before an answer (the splash finished or was recreated) is shown again on the
  next splash, even in the same process, instead of that splash waiting on the lost form. A `start()` that joins a
  consent gather with the form on screen waits for the answer, even when the SDK already started from the previous
  session's consent.

### Added
- Debug builds ask for consent as in Europe: in a debuggable build that isn't PRODUCTION, UMP assumes the EEA unless
  `privacy { debugGeography }` says otherwise, and the device is made a UMP test device automatically (the MD5 of
  its Android ID), so it works on a phone too. `privacy { debugBuildGeography = DebugGeography.DISABLED }` uses the
  real location. `PrivacyOptions.debugBuildGeography`.
- Banners load ahead of their view: `AdSlots.load` of a banner loads it (at the window's width) without a view, and
  the next banner view for it shows it at once, or waits for it while it's loading; one of another width loads its
  own. Native ads already could; a native view asking while its ad loads ahead now takes that ad without a second
  request.
- The native templates' sizes use sdp and text sizes ssp (com.intuit.sdp and com.intuit.ssp, new `adroit-views`
  dependencies), scaling with the screen width. New dimens `adroit_native_spacing_tiny`,
  `adroit_shimmer_corner_radius` and `adroit_shimmer_padding`.
- Native styles `SMALL_BUTTON_END`, `SMALL_BUTTON_BOTTOM`, `LARGE_BUTTON_BOTTOM` (the default), `LARGE_COMPACT`
  and `MEDIA_START`, each with its template (`NativeTemplates.SmallButtonEnd`, ...; `"style": "media_start"` in
  Firebase; `adroit_template` in XML).
- Native colors from Firebase: `ctaColors` (one color, or two for a gradient), `ctaTextColor`, `attributionColor`
  and `attributionTextColor`, in an ad's value or in `AdSettings`. The template button is a gradient from
  `adroit_native_cta` to the new `adroit_native_cta_end` (the same color by default).
- The app's own native layouts by name: `NativeTemplates.register(name, layout)`, then `AdProfile.native(layout =
  name)` or `{"style": "name"}` in Firebase. `NativeTemplates.forPlacement(placement)` returns the layout an ad shows
  in now.
- Native template styling by resources: colors (`adroit_native_headline`, `_body`, `_cta`, `_cta_text`,
  `_attribution`, `_attribution_text`, `_stroke`, `_background`), dimens (corners, text sizes, icon and media
  sizes) and drawables (`adroit_native_card`, `_cta_background`, `_attribution_background`), each replaced by
  an app resource of the same name.
- `docs/NEW_APP_XML.md`: adding the plugin to a new Kotlin + XML app, step by step, with a sample Firebase value
  for every kind of ad.
- `docs/NEW_APP_COMPOSE.md`: the same for a new Jetpack Compose app (splash Activity, Navigation Compose, `AdSlot`,
  `AdBanner`, `AdNative` in a `LazyColumn`, the full-screen launcher).
- `sample-compose` is now the XML sample's app in Compose (replacing the AdMob Playground): splash with consent and
  the splash ad, Home, Feed, Settings, Details and Native styles destinations, the same `SampleAds` and Firebase
  parameters. Firebase turns on once `google-services.json` is added for `io.github.thesobiaakbar.adroit.sample.compose`.
- `docs/NEW_APP_MIXED.md`: the same for an app with both XML and Compose screens: one setup for both, which API
  where, Compose in a Fragment or an XML layout, XML in a Compose screen, and mediation.
- `sample-mixed` is now the same app with both toolkits (replacing its two interop screens): XML splash, Home and
  Settings, a Compose feed in a Fragment, a Compose Details screen embedding an XML native ad; Firebase, the same
  `SampleAds` and parameters, and the `-PwithMediation=true` build as before.
- `RemoteValue` (adroit-firebase): the app's own Remote Config parameters, with in-app defaults:
  `RemoteValue.boolean`, `long`, `double`, `string`, `json` and `custom`. `.value` never waits and reads as the
  default when Firebase doesn't set a valid value; `updates()` follows activations.
- `NetworkMonitor` (adroit-core): the connection state as a `StateFlow<NetworkState>`: `Unavailable`,
  `ConnectedNoInternet` (for example Wi-Fi behind a sign-in page) or `Connected` over Wi-Fi, mobile data or ethernet.
- `RemoteConfigSession` (adroit-firebase): fetches Remote Config once per app session without blocking. After a
  failure it tries again on each new Activity and when the device comes back online. `FirebaseRemoteConfigProvider`
  re-reads its values as soon as the session activates them.
- Ad definitions (`docs/AD_PROFILES.md`): an enum implementing `AdDefinition`, each entry holding a typed
  `AdProfile` default (`AdProfile.interstitial(...)`, `banner(...)`, …), registered with
  `adMobConfig { profiles(AppAds.entries, AdSettings(...)) }`. The entry name is the ad's Firebase parameter, which
  overrides the defaults as with an ads file. `AdDefinitions.firebaseTemplate(...)` writes a Remote Config template
  for the console's "Publish from a file"; outside PRODUCTION, `initialize` logs every ad's JSON.
- Format swapping (`docs/AD_PROFILES.md`, "Swapping formats"): a Firebase value with `"format"` turns an interstitial
  into an app open ad or back, and a banner into a native ad or back, with ad unit IDs of the new format (PRODUCTION
  ignores a swap without them). The ad keeps its counter, timer and caps. `AdSlots.showFullScreen`,
  `AdSlots.showOnSplash`, `AdSlotView` (adroit-views) and `AdSlot` (adroit-compose) show an ad in its current format;
  a swapped app open ad shows its interstitial on returns to the app. `PlacementOverride.format`, through a new
  constructor; the 1.0.0-rc1 constructor is unchanged.
- One ad per banner or native slot, and refresh: `load(refresh = false)` on `AdBannerView`, `AdNativeView`,
  `AdSlotView` and `NativeAdHolder`, and `refreshKey` on the `AdSlot`, `AdBanner` and `AdNative` composables. A slot
  with an ad, or with one loading, never requests another; a refresh loads a new ad in the background while the
  shown one stays (no shimmer), swaps it in once loaded, and keeps the shown one if it fails. `attachBanner` and
  `attachNativeAd` reuse the ad already in their container for the same placement.
- Shimmer while banner and native ad profiles load (`showLoading`): shaped like the banner's size or the native
  template, as currently configured (Firebase included), in every view helper and composable; removed when the ad
  appears or fails. Colours `adroit_shimmer_base` and `adroit_shimmer_highlight` can be overridden.
- "Loading ad…" screen before full-screen ad profiles (`showLoading`, default true; `loadingSeconds`, default 1): shown
  from the show call for at least `loadingSeconds`, or during the load wait, and removed when the ad opens, fails,
  times out or the user leaves. Its layout (`adroit_loading_ad`) can be replaced by the app. Not for splash ads.
- Load timeout for full-screen ad profiles (`loadTimeoutSeconds`, default 8, per ad or in `AdSettings`): a show on an
  ad that isn't loaded requests it and waits at most that long, then answers `NotShown(Timeout)` (a failed load ends
  the wait at once); a late ad is kept for the next show. `showLoading` and `loadingSeconds` are read already, for
  the loading screen and shimmer that follow (PROMPT 29B, 29C).
- Ad profiles (the enum, the ads file and Firebase) load only when the app asks (`AdSlots.load`, docs/AD_PROFILES.md#loading):
  no load at start, no retry after a failed load, no replacement after an expiry. `"loadAgain": true` (false by
  default) loads the next ad unit right after each show; Firebase can set it per ad. A splash ad's load still in
  progress is dropped when the splash ends without it. **Changed:** ads files from 1.0.0-rc1 loaded at start and
  after each show; call `AdSlots.load` and set `loadAgain` for the same behaviour.
- Load retries pause without a network, instead of failing again on their backoff, and run as soon as a network
  returns (after up to 5 seconds for Android to validate it).
- Integration guide (`docs/INTEGRATION_GUIDE.md`): a step-by-step checklist for a new app, from the library to the
  Firebase parameters and release.
- Splash screen (`docs/SPLASH.md`): `SplashFlow` runs consent (the form gets `consentTimeoutSeconds`, 8, to appear,
  and is never shown late), the library's start, a splash timer with 0–100 progress (`splashSeconds` 11,
  `premiumSplashSeconds` 3, `offlineSplashSeconds` 3, all from the Firebase `AdSettings` parameter via
  `SplashTimings` and `AdOverrides.splash`) and the splash ad, and never gets stuck. A splash ad isn't reloaded
  after its show unless another placement shares its ad unit.

### Removed
- Before 1.0.0, so in 1.0.0-rc2 (the rc1 API changes here): the native styles `SMALL`, `MEDIUM` and `LARGE` and
  their templates `NativeTemplates.Small`, `Medium` and `Large`. `LARGE_BUTTON_BOTTOM` is the default style now. A
  Firebase `"style"` of `small`, `medium` or `large` is read as an unregistered layout name, and shows the ad's style.

### Changed
- A missing optional view in a native layout (advertiser, stars, price, store) is logged at debug level, no longer
  as a warning.
- The template call-to-action and "Ad" badge default to the theme's `colorPrimary` (they used `colorAccent`, which a
  Material 3 theme maps to its muted secondary color).
- The loading shimmer of a native ad is built at runtime from the layout it will show in, any template or an app's
  own layout, with a block over each of its views; the whole card shimmers too, and a banner's whole area. New color
  `adroit_shimmer_card`.
- The native templates draw a card with a thin border by default (`adroit_native_card`); their call-to-action
  buttons are framework `Button`s, so a Material theme no longer repaints them.
- An ad without ad unit IDs (in a definition or an ads file) uses Google's test ad unit for its format instead of
  being a configuration error. PRODUCTION logs a warning for it.

## [1.0.0-rc1] - 2026-09-28

The public API is frozen for 1.0.0 (`docs/audits/API_STABILITY_AUDIT.md`): from here on, the rules in
`docs/RELEASE.md` "API stability" apply.

### Added
- `AppOpenApi.showOnColdStart(activity, maxWaitMillis, onResult)`, for Java callers (ADR-013).
- `@JvmOverloads` on the public constructors with default arguments: `BannerOptions`, `AdError.Internal`,
  `FirebaseAnalyticsOptions`, `FirebaseAnalyticsEventListener`, `FirebaseRemoteConfigProvider`, and adroit-testing's
  `FakeNativeAdHandle` (and its `block`).
- Ad profiles (`docs/AD_PROFILES.md`): `adMobConfig { profiles(context, R.raw.ads_config) }` defines every ad in one
  JSON file, and each ad can be changed from a Firebase Remote Config parameter of the same name.
- Several ad unit IDs per placement (`adUnitIds`, and a list overload for every format in `placements { }`), used in
  rotation: each load request takes the next one.
- `FrequencyRule.counter`: an interstitial or rewarded interstitial shows at most on every Nth `show` call, and new
  `AdError.CounterNotReached`.
- `FrequencyOptions.maxInterstitialsPerSession`: an overall interstitial limit per session, splash ones not counted.
- `InterstitialApi.showOnSplash`, and `splash`/`splashTimeout` for interstitial placements.
- `BannerSize.Collapsible` (Google's collapsible banners) and `CollapsibleBannerPosition`; the XML sizes
  `collapsibleBottom` and `collapsibleTop`.
- `NativeStyle`, a native placement's `style`, `NativeApi.style(placement)` and `NativeTemplates.forStyle(style)`.
- `adroit-firebase` (optional): `FirebaseRemoteConfigProvider`, which reads one Remote Config parameter per ad (and an
  `AdSettings` parameter) as remote overrides, and `FirebaseAnalyticsEventListener`, which logs paid events as Firebase
  `ad_impression` events (and optional custom ad events). Firebase BoM 34.19.0.

### Changed (breaking)
- The placement classes implement `AdPlacement` directly: the public `AdPlacementBase` superclass is gone. Code
  that named `AdPlacementBase` must use `AdPlacement`; recompile against this version.
- `FrequencyRule` is a regular class, no longer a data class (ADR-011): `copy` and `componentN` are gone, so construct
  a new rule instead. It also gained `counter` (see Added), which changed its constructor.
- `AppOpenApi.showOnColdStart`'s callback parameter is `onResult` (was `onComplete`), like every other show call.
  Only calls that name the argument change.

### Changed
- A banner's size comes from its placement (or ad profile) unless the code passes one: `BannerOptions.size` and
  `AdBannerView`'s `adroit_bannerSize` are now optional. The placement-level `size` had no effect before.
- A native ad's template comes from its placement's `style` unless the code passes a layout: the `layout` parameters
  of `AdNative`, `attachNativeAd` and `NativeAdHolder.bindTo`, and `AdNativeView`'s `adroit_template`, are
  now optional (the default style is MEDIUM, as the default template was).
- Placement keys may contain capital letters (`[A-Za-z0-9_]{1,40}`), so they can match Firebase parameter names.
- Remote overrides drop an invalid ad unit ID on its own, instead of the placement's whole override.

### Fixed
- The AARs asked apps to compile against API 37 (AGP 9 publishes the library's compileSdk as `minCompileSdk`). They
  now ask for 34, what their dependencies need, so apps on compileSdk 34 to 36 and AGP 8.1.1 or newer can use
  adroit-core and adroit-views. adroit-compose still needs compileSdk 37 and AGP 9.1, from Compose 1.12.
- Sealed types and enums state in their KDoc whether they may gain values (include an `else` branch) or are closed.
- No disk I/O from the library on the main thread (`docs/audits/PERFORMANCE_AUDIT.md`): `initialize()` reads and
  updates the launch count and loads UMP's stored consent state on a background thread, and `start()` writes
  `gad_rdp` on the SDK's initialization thread, still before the SDK starts.
- `initialize()` no longer triggers the `ServiceLoader` lookup of `Dispatchers.Main`, which opened the APK on the
  main thread (60 to 80 ms per cold start in debuggable builds).
- The views and Compose modules log through the host's configured logger and level, like core.
- `docs/CONSENT.md`: the Consent Mode section had the order wrong. Firebase must be initialized before UMP starts,
  and UMP 3.2.0 or newer applies the consent choices itself.

### Security
- In PRODUCTION, every log line shortens full ad unit IDs, including retry messages that printed them in full when
  the log level was lowered.
- A `GoogleRequestCustomizer` that fails with a linkage error (for example `NoSuchMethodError` after a Google SDK
  upgrade) is contained like an exception, instead of crashing the load.

## [0.1.0] - 2026-09-26

First internal release.

### Added
- `adroit-core`:
  - One configuration DSL (`adMobConfig { }`, plus a Java builder) with DEVELOPMENT, STAGING and PRODUCTION
    environments, test-ad safety rules and full validation.
  - Consent with UMP before any ad request: the privacy options entry point, debug geography, age treatment, max ad
    content rating, restricted data processing and consent sync.
  - Initialization state (`AdsState`) and a `start()` that's safe to call from any launcher.
  - Interstitial, rewarded, rewarded interstitial and app open ads: preloading, retries with backoff, expiry, frequency
    limits, one full-screen ad at a time, and a result callback (or `showAndAwait`) that's always called exactly once.
  - Rewards identified by `rewardId` for exactly-once granting, with `unclaimedResults` for rewards earned after the
    screen is gone.
  - App open ads on foreground and on cold start, with cooldown, launch-count rule, Activity exclusions,
    `skipNextForeground` and `suppressWhile`.
  - Banners (anchored adaptive, inline adaptive, medium rectangle, standard) and native ads with pools and
    lifecycle-bound handles.
  - The ad-free switch, remote overrides (`AdConfigProvider`), the `AdEvent` stream and listeners (including paid
    events), pluggable logging, diagnostics and Ad Inspector.
- `adroit-views`: `AdBannerView`, `AdNativeView`, `attachBanner`, `attachNativeAd`, `NativeAdHolder` and three native
  templates.
- `adroit-compose`: `AdBanner`, `AdNative`, `rememberNativeAd`, `rememberAdAvailability` and
  `rememberFullScreenAdLauncher`.
- `adroit-testing`: `FakeAdsClient`, fake handles and `AdroitTestRule`.
- Consumer R8 rules: one rule, for WorkManager's database, which the Google SDK brings in with Room 2.2.5. Without it,
  every minified app crashed at launch (see `docs/architecture/R8_REPORT.md`).

### Google SDK
- Built against Google Mobile Ads Next-Gen SDK `ads-mobile-sdk` 1.4.0 and UMP 4.0.0.

[Unreleased]: https://github.com/TheSobiaAkbar/Adroit/compare/v1.0.0-rc6...HEAD
[1.0.0-rc6]: https://github.com/TheSobiaAkbar/Adroit/compare/v1.0.0-rc5...v1.0.0-rc6
[1.0.0-rc5]: https://github.com/TheSobiaAkbar/Adroit/compare/v1.0.0-rc4...v1.0.0-rc5
[1.0.0-rc4]: https://github.com/TheSobiaAkbar/Adroit/compare/v1.0.0-rc3...v1.0.0-rc4
[1.0.0-rc3]: https://github.com/TheSobiaAkbar/Adroit/compare/v1.0.0-rc2...v1.0.0-rc3
[1.0.0-rc2]: https://github.com/TheSobiaAkbar/Adroit/compare/v1.0.0-rc1...v1.0.0-rc2
[1.0.0-rc1]: https://github.com/TheSobiaAkbar/Adroit/compare/v0.1.0...v1.0.0-rc1
[0.1.0]: https://github.com/TheSobiaAkbar/Adroit/releases/tag/v0.1.0
