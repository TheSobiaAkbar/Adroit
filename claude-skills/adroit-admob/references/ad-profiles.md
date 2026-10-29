# Ad profiles: the `AppAds` enum, `AdSettings`, `AdSlots`

## Contents
1. The enum
2. `AdProfile` factories, every parameter
3. `AdSettings`
4. Counter, timer and loading strategies
5. `AdSlots`
6. Typed placements from an entry
7. How the limits combine
8. Without profiles: the low-level placements API

## 1. The enum

```kotlin
/** Every ad in the app. Each entry's name is also its Firebase Remote Config parameter: never rename after release. */
enum class AppAds(override val defaults: AdProfile) : AdDefinition {
    SplashAd(AdProfile.interstitial(ids = unit("1111111111"), splash = true)),
    AppOpenAd(AdProfile.appOpen(ids = unit("2222222222"),
        excludedActivities = listOf(SplashActivity::class.java.name), loadAgain = true)),
    ResultInterstitial(AdProfile.interstitial(ids = unit("3333333333"), counter = 2, loadAgain = true)),
    UnlockRewarded(AdProfile.rewarded(ids = unit("4444444444"), loadAgain = true)),
    HomeBanner(AdProfile.banner(ids = unit("5555555555"), style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM))),
    LanguageNative(AdProfile.native(ids = unit("6666666666"), style = NativeStyle.LARGE_BUTTON_BOTTOM)),
    ;
    companion object {
        /** What every ad shares: the Firebase parameter "AdSettings". */
        val settings = AdSettings(splashSeconds = 8, closeButton = AdCloseButton.TOP_END)
    }
}

private fun unit(id: String) = listOf("ca-app-pub-XXXXXXXXXXXXXXXX/$id")
```

- **One entry per placement**, not per format. Every entry can be tuned and measured on its own.
- **Ad unit IDs.**
  - Empty `ids` gives Google's test unit for the format, **in every environment, release included**. PRODUCTION only
    logs a warning, because test ads earn nothing.
  - So a release build must get real IDs from the enum or from Firebase.
  - Debug (DEVELOPMENT) uses test units whatever is set.
  - Two or more IDs are used in turn, one per load.
  - Two entries may share an ad unit. For example, PhotoRecovery's `SplashAd` and `LaunchInterstitial` share one unit.
- **The entry's format is fixed by its factory.** Firebase can swap it only between interstitial and app open, or
  between banner and native.
- **The entry is used directly** in `AdSlot(AppAds.X)`, `AdSlots.load(AppAds.X)` and
  `AdSlots.showFullScreen(activity, AppAds.X)`. In XML, write the name: `app:adroit_placement="HomeBanner"`.

## 2. `AdProfile` factories

All are `@JvmStatic @JvmOverloads`, and every parameter is optional. Each parameter is also a field of the ad's
Firebase JSON (`remote-config.md`).

```kotlin
AdProfile.interstitial(
    ids: List<String> = emptyList(), enabled: Boolean = true,
    counter: Int = 0, timerSeconds: Int = 0, maxPerSession: Int = 0,
    splash: Boolean = false, splashTimeoutSeconds: Int = 8,
    loadAgain: Boolean = false, loadTimeoutSeconds: Int? = null, showLoading: Boolean? = null, loadingSeconds: Int? = null,
    counters: List<Number> = emptyList(), counterStart: Number = 0, counterStep: Number = 1,
    counterResetOnFail: Boolean = false, loadAhead: Int = 1,
)
AdProfile.rewardedInterstitial(/* same as interstitial, without splash / splashTimeoutSeconds */)
AdProfile.rewarded(ids, enabled, maxPerSession = 0, loadAgain, loadTimeoutSeconds, showLoading, loadingSeconds)
AdProfile.appOpen(ids, enabled, timerSeconds: Int? = null, maxPerSession = 0,
    excludedActivities: List<String> = emptyList(), loadAgain, loadTimeoutSeconds, showLoading, loadingSeconds)
AdProfile.banner(ids, enabled, style: BannerSize = BannerSize.AnchoredAdaptive, showLoading: Boolean? = null,
    closeButton: AdCloseButton? = null, closeIcon: String? = null)
AdProfile.native(ids, enabled, style: NativeStyle = NativeStyle.LARGE_BUTTON_BOTTOM, poolSize: Int = 1,
    loadAgain, showLoading, closeButton, closeIcon)
AdProfile.native(layout: String, ids, enabled, poolSize, loadAgain, showLoading, closeButton, closeIcon) // a registered layout name
```
- `splash = true` marks the one ad shown by `SplashFlow` / `showOnSplash`. It is never counted in
  `maxInterstitialsPerSession`, and it has no counter.
- `excludedActivities` takes fully qualified class names, always including the splash. Use
  `SplashActivity::class.java.name`, so that a rename can't break it.
- `BannerSize`:
  - `AnchoredAdaptive` (full width, the default)
  - `Collapsible(CollapsibleBannerPosition.BOTTOM | TOP)`
  - `MediumRectangle` (300×250)
  - `Standard` (320×50)
  - `InlineAdaptive(maxHeightDp)`
- `NativeStyle`:
  - `SMALL_BUTTON_END`
  - `SMALL_BUTTON_BOTTOM`
  - `LARGE_BUTTON_BOTTOM` (the default)
  - `LARGE_COMPACT`
  - `MEDIA_START`
- `AdCloseButton`: `NONE`, `TOP_START`, `TOP_END`, `BOTTOM_START`, `BOTTOM_END`.
- `closeIcon` is a drawable name in the app, for example `"ic_close_red"`.
- `poolSize`, `splash` and `excludedActivities` are set in code only; Firebase ignores them.

## 3. `AdSettings`: what every ad shares (the Firebase parameter `AdSettings`)

```kotlin
AdSettings(
    adsEnabled = true,
    maxInterstitialsPerSession = 0,        // 0 = no limit; splash ads not counted
    minSecondsBetweenFullScreenAds = 0,    // gap between any two full-screen ads
    splashSeconds = 11,                    // splash timer with an ad
    premiumSplashSeconds = 3,              // ad-free user, ad off, no consent, failed load
    offlineSplashSeconds = 3,
    consentTimeoutSeconds = 8,
    loadTimeoutSeconds = 8,                // how long a show waits for an unloaded ad
    showLoading = true,                    // "Loading ad…" screen and shimmer
    loadingSeconds = 1,                    // minimum "Loading ad…" time when the ad is ready
    closeButton = AdCloseButton.NONE, closeIcon = null,
)
```
Since rc4 there are no hidden default timers: an app that wants spacing sets it here or per ad. Firebase also accepts
the native colours here (`ctaColors`, `ctaTextColor`, `attributionColor`, `attributionTextColor`).

## 4. Counter, timer and loading strategies

An interstitial or rewarded interstitial follows one of three strategies:

| `counter` | `timerSeconds` | Strategy |
|---|---|---|
| 0 | 0 | Every show call may show an ad (still subject to the global limits). |
| set | anything | **Counter.** Call `showFullScreen` on *every* occurrence of the event; the ad shows every Nth call. Other calls return `NotShown(CounterNotReached)` at once, and the callback continues the flow. |
| 0 | set | **Timer.** After a show, the ad isn't shown or requested for N seconds. Then the next ad is requested once and kept until shown. |

**Counting:** with the defaults (`counterStart = 0`, `counterStep = 1`), `counter = 3` shows the ad on the 3rd,
6th, 9th… call. Calls 1 and 2 return `NotShown(CounterNotReached)` at once. To show on the 1st call and then every
3rd, use `counterStart = 2`.

**Loading with a counter:**
- `loadAhead` (default 1) loads the ad by itself one call before it is due.
- `AdSlots.load` in `onResume` is still worth adding, because it keeps an ad ready on screens where the due call
  comes first.
- It is harmless: it does nothing while an ad is loaded or loading.

**Counter options:**
- A list such as `counters = listOf(3, 2)` cycles through the max values.
- `counterStart` sets the counter's value at app start. With `counterStart = 1` and `counter = 2`, the first ad
  shows on the 1st call.
- `counterStep` can be a decimal.
- `counterResetOnFail`:
  - `false`, the default: a due ad that couldn't show is tried again on the very next call.
  - `true`: counting restarts from 0.
- `loadAhead` sets how many calls before the due call the ad loads (1 by default).
  `loadAhead = 0, loadTimeoutSeconds = 0` loads on the due call and never makes the user wait.

**Loading:**
- **Ads load only when asked:** `AdSlots.load(X)`, which does nothing if the ad is already loaded or loading. The
  other triggers are: a banner or native view that asks; `SplashFlow`, for the splash ad; a due counter
  (`loadAhead`); an ended timer; and `loadAgain`.
- **A failed load isn't retried by itself.** The next `load` is the retry, so call `load` from `onResume`.
- **`loadAgain = true`** loads the next ad right after each show. Use it for ads that show repeatedly: app open, a
  rewarded ad the user can watch again, frequent interstitials. Leave it off for an ad tied to an event you load for
  explicitly (for example, "load when a scan starts, show when it ends").
- **A loaded ad** waits at most about 1 hour (app open: about 4 h). It is not replaced until the next `load`.
- **Banner and native ads can be loaded ahead** of their screen: `AdSlots.load(AppAds.NextScreenBanner)` just before
  navigating. A banner preloaded at the window width is used by a full-width view; a view of another width loads its
  own.

## 5. `AdSlots` (all `@JvmStatic`)

```kotlin
AdSlots.load(definition: AdDefinition)                 // also load(key: String)
AdSlots.showFullScreen(activity, definition) { result: FullScreenResult -> continueFlow() } // interstitial and app open only
AdSlots.showOnSplash(activity, definition) { result -> goToMain() }  // waits up to splashTimeoutSeconds
AdSlots.currentFormat(definition): AdFormat?           // the format after any Firebase swap
AdSlots.formatChanges(key: String): Flow<AdFormat?>
```
- `showFullScreen` shows the ad in its current format, so a Firebase swap from interstitial to app open needs no
  release.
- What the user sees:
  - The ad is ready: "Loading ad…" for `loadingSeconds`, then the ad.
  - The ad isn't loaded yet: "Loading ad…" for up to `loadTimeoutSeconds`, then the ad or the callback with
    `NotShown(Timeout)`.
  - The ad isn't due, is capped, the user is ad-free, or ads are disabled: no screen, and the callback runs at once.
- **Rewarded ads don't go through `showFullScreen`.** Use `Adroit.showRewarded(activity, X.rewardedPlacement())`,
  `Adroit.rewarded.show(...)`, or the Compose launcher (`formats.md`).
- `FullScreenResult` is `Dismissed(clicked)` or `NotShown(error)`, plus `wasShown`. You rarely branch on it: just
  continue.

## 6. Typed placements from an entry

These extensions are in `io.github.thesobiaakbar.adroit.config`. Each throws if the entry has another format:

```kotlin
AppAds.UnlockRewarded.rewardedPlacement()       // RewardedPlacement
AppAds.X.interstitialPlacement(); .rewardedInterstitialPlacement(); .appOpenPlacement(); .bannerPlacement(); .nativePlacement()
```
Use them with the format-specific APIs: `Adroit.rewarded.availability(p)`, `AdBanner(p)`, `AdNative(p)`. These APIs
don't follow a Firebase format swap; `AdSlot`, `AdSlotView` and `AdSlots.showFullScreen` do.

## 7. How the limits combine (interstitial show, the first "no" wins)

1. Ads can run (consent, ad-free, `adsEnabled`) and the ad is `enabled`. Otherwise `NotShown(Disabled)`.
2. The counter, when set. Otherwise `NotShown(CounterNotReached)`.
3. `maxInterstitialsPerSession`, which doesn't count splash ads. Otherwise `NotShown(FrequencyCapped)`.
4. The ad's own `maxPerSession`.
5. The ad's `timerSeconds`, when no counter is set.
6. `minSecondsBetweenFullScreenAds`.
7. A loaded ad; otherwise it waits up to `loadTimeoutSeconds`, then `NotShown(Timeout)` or `NotShown(NotReady)`.

Only real impressions count. The counts are per process and kept in memory. Rewarded ads skip the global gap unless
`frequency { includeRewardedInGlobalInterval = true }`.

## 8. Without profiles: the low-level placements API

Use this only for an existing app that doesn't want profiles or Firebase. Profiles are the recommended path.

```kotlin
object Placements {                                      // keys: [A-Za-z0-9_]{1,40}
    val HomeBanner = BannerPlacement("home_banner")
    val LevelComplete = InterstitialPlacement("level_complete")
    val ExtraLife = RewardedPlacement("extra_life")
    val FeedNative = NativePlacement("feed_native")
}
Adroit.initialize(this, adMobConfig {
    environment = ...
    placements {
        banner(Placements.HomeBanner, "ca-app-pub-…/…")
        interstitial(Placements.LevelComplete, "ca-app-pub-…/…") { frequency { minInterval = 60.seconds } }
        rewarded(Placements.ExtraLife, "ca-app-pub-…/…")
        native(Placements.FeedNative, adUnitIds = listOf("ca-app-pub-…/…")) { style = NativeStyle.LARGE_COMPACT }
        appOpen(AppOpenPlacement("app_open"), "ca-app-pub-…/…") { excludeActivities(SplashActivity::class.java) }
    }
    frequency { minIntervalBetweenFullScreenAds = 30.seconds }
})
Adroit.start(launcherActivity) { state -> /* Ready, Unavailable or Failed: continue either way */ }
Adroit.interstitial.show(activity, Placements.LevelComplete) { next() }
AdBanner(Placements.HomeBanner)                          // Compose; XML: AdBannerView app:adroit_placement="home_banner"
```
Placement options:
- Every placement takes `enabled`, `preload` (`AFTER_READY` by default, `ON_DEMAND`, `NONE`), `retry`, `expiry` and
  `frequency { minInterval; maxPerSession; counter }`.
- Interstitial: `splash`, `splashTimeout`.
- Banner: `size`.
- Native: `poolSize` (1–3), `style`.
- App open: `cooldown`, `minLaunchesBeforeFirstShow`, `showOnColdStartAllowed`, `suppressAfterFullScreenAd`,
  `excludeActivities(...)`.

Low-level placements preload once the SDK is ready (`AFTER_READY`), unlike profiles, which load only when asked.
