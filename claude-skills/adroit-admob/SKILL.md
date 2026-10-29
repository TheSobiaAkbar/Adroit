---
name: adroit-admob
description: Integrate AdMob ads into an Android app (Kotlin, Jetpack Compose or XML) with Adroit, the in-house AdMob library (io.github.thesobiaakbar adroit-core/compose/views/firebase/update). Covers every format (banner, collapsible banner, native, interstitial, rewarded, rewarded interstitial, app open, splash ad), UMP consent and privacy options, Firebase Remote Config control of each ad, the Play in-app update, ad-free/premium users, testing and the release checklist. Use this skill whenever the user wants to add, change, debug or plan ads in an Android app, mentions AdMob, Adroit, AdSlot, AdSlots, AppAds, SplashFlow, ad units, an "ad plan", consent/GDPR for ads, or app-ads.txt — even if they don't name Adroit. For in-app purchases, paywalls or "remove ads" purchases, also use adroit-billing.
---

# Adroit: AdMob ads

Adroit wraps Google's Next-Gen Mobile Ads SDK (`ads-mobile-sdk` 1.4.0) and UMP consent (4.0.0) behind one Kotlin API.
The app lists every ad once, in an enum. Each enum entry is also a Firebase Remote Config parameter, so any ad can be
re-tuned or turned off without a new release. The Google SDKs are internal: app code never imports a Google ads type.

**Version.** This skill is verified against Adroit `1.0.0-rc6` and the master commit `b0df3c8` (2026-10). The two
have the same API, but the Maven Central `1.0.0-rc6` release lacks two fixes that are on master (collapsible banners,
ad-free shimmer): see `references/setup.md` §1. If the project pins a newer version, read the library's
`CHANGELOG.md` before you trust a signature here.

## 1. Settle the scenario first

Before writing code, answer these questions. Read the answers from the project wherever you can: `build.gradle.kts`,
`libs.versions.toml`, the manifest, and the existing `Application` and `Activity` classes. Ask the user only for what
the project can't tell you.

| Question | Why it matters |
|---|---|
| Compose, XML, or both? | Both → `adroit-compose`, which includes the XML views. |
| Does the app also sell anything (subscription, lifetime, coins, "remove ads")? | Yes → also use the **adroit-billing** skill: the ad-free bridge, and the remove-ads paywall on an ad's close button. |
| Is Firebase in the app, or wanted? | Yes → `adroit-firebase`, and every ad is controllable remotely. No → profiles still work, with local values only. |
| Single module, or multi-module (Hilt, clean architecture)? | Multi-module → read `references/architecture.md` before placing any ad code. |
| Which formats, on which screens, at which moments? Is there a splash? | This is the "ad plan". Write it down as the `AppAds` enum (section 3) before wiring any screen. |
| Are the AdMob app ID and ad unit IDs ready? | Not yet → leave `ids` empty. Debug always uses Google's test ads, so the work is never blocked. |
| Is the in-app update wanted? | Yes → `adroit-update`, which runs on the splash. |

## 2. Where to read

| You are doing | Read |
|---|---|
| Dependencies, manifest, `Application` init, environments, Firebase setup | `references/setup.md` |
| The `AppAds` enum, `AdProfile` factories, `AdSettings`, `AdSlots`, and the low-level placements API | `references/ad-profiles.md` |
| Banner, native, `AdSlot`, interstitial, rewarded, rewarded interstitial, app open, close button, lists, styling | `references/formats.md` |
| Splash screen, consent and privacy options, the in-app update, a launch interstitial after onboarding | `references/splash-consent-update.md` |
| Firebase JSON per ad, `AdSettings` JSON, format swaps, the app's own remote switches (`RemoteValue`) | `references/remote-config.md` |
| Multi-module, Hilt, MVI: where ads attach, slots, test seams | `references/architecture.md` |
| Unit and instrumented tests, the manual QA list | `references/testing.md` |
| Release checklist, R8, mediation, known traps, places where the docs disagree with the code | `references/release-troubleshooting.md` |

Read only the files the task needs. `formats.md` has one section per format.

## 3. The model, in one screen

```kotlin
// ads/AppAds.kt: every ad in the app. Each entry's NAME is its Firebase parameter: never rename one after release.
enum class AppAds(override val defaults: AdProfile) : AdDefinition {
    SplashAd(AdProfile.interstitial(splash = true)),
    AppOpenAd(AdProfile.appOpen(excludedActivities = listOf(SplashActivity::class.java.name), loadAgain = true)),
    ResultInterstitial(AdProfile.interstitial(counter = 2, loadAgain = true)),
    UnlockRewarded(AdProfile.rewarded(loadAgain = true)),
    HomeBanner(AdProfile.banner(style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM))),
    ListNative(AdProfile.native(style = NativeStyle.SMALL_BUTTON_BOTTOM)),
    ;
    companion object { val settings = AdSettings(splashSeconds = 8, minSecondsBetweenFullScreenAds = 30) }
}

// Application.onCreate: synchronous, no network.
Adroit.initialize(this, adMobConfig {
    environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
    profiles(AppAds.entries, AppAds.settings)
    remoteConfig = FirebaseRemoteConfigProvider()          // only with adroit-firebase
    logging { level = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN }
})

// The splash Activity: consent, then the SDK, the timer, and the splash ad (SplashFlow does all of it).
// Screens:
AdSlot(AppAds.HomeBanner)                                         // Compose: banner or native, whatever the profile says
AdSlots.load(AppAds.ResultInterstitial)                           // when the screen that will show it comes to the front
AdSlots.showFullScreen(activity, AppAds.ResultInterstitial) { goNext() }   // ALWAYS continue in the callback
Adroit.showRewarded(activity, AppAds.UnlockRewarded.rewardedPlacement()) { if (it is RewardResult.Earned) unlock() }
```

The import packages are listed in `references/setup.md`. Getting them wrong is the most common compile failure:
`rewardedPlacement()` lives in `io.github.thesobiaakbar.adroit.config`, and the composables in `...adroit.compose`.

## 4. Rules that are never optional

These carry the lessons of real production bugs. The reason comes with each rule, so you can apply it to cases not
listed here.

1. **Every show continues the app's flow in its callback.** The callback runs exactly once, whether an ad showed, was
   capped, wasn't due, timed out or failed offline. Code that waits for "ad closed" anywhere else gets stuck for
   premium, offline and capped users.
2. **Ads load only when asked.** Call `AdSlots.load(X)` when the screen that will show X comes to the front (in
   `onResume` or `LifecycleResumeEffect`), or just before the action that leads to it (when a scan starts, for
   example). A load that is already loaded or in flight does nothing; a failed load is retried by the next `load`.
   Banner and native slots load by themselves. A show that finds nothing loaded waits up to `loadTimeoutSeconds`
   behind "Loading ad…".
3. **Grant a reward only on `RewardResult.Earned`, once per `rewardId`, inside a ViewModel.** Also collect
   `Adroit.rewarded.unclaimedResults` there: a rotation or a killed screen must never lose a reward or grant it twice.
4. **The splash is its own Activity.** It is never a navigation destination: the app open ad excludes screens by
   Activity class name, and consent and the splash ad need an Activity with nothing drawn over it. Keep `SplashFlow` in
   a ViewModel. Navigate only from `repeatOnLifecycle(RESUMED)`: Android 15+ blocks a background app's network, and a
   paused Activity is still STARTED.
5. **No app open ad on coming back from another app the app itself opened** (share sheet, settings, mail, Play, a
   picker). Call `Adroit.appOpen.skipNextForeground()` from `onStop`, and only if the app really left. A cancelled
   share sheet never stops the Activity, and a skip requested too early swallows the next real return's ad. See
   `formats.md` → App open.
6. **Enum entry names are Firebase parameter names.** Pick names that say what the ad is, and never rename one after
   release; a rename silently disconnects the remote config.
7. **Debug always shows test ads, and release must be PRODUCTION.** Never configure real units into a debug build to
   "see real ads": clicking your own ads gets the AdMob account banned.
8. **Ad-free wins over everything, but is not persisted.** Re-apply `Adroit.setAdFree(...)` on every launch, before
   the splash asks for its ad. With billing, use the bridge line in the adroit-billing skill.
9. **Never send a file, file name, path or user content to an ad**, and show no ad during a critical operation, on a
   permission prompt, or over a purchase flow.
10. **Show a "Privacy options" entry while `Adroit.privacy.isPrivacyOptionsRequired` is true** (Google requires it),
    and publish a GDPR message in AdMob → Privacy & messaging, or no consent form ever shows.
11. **A rewarded interstitial needs your own intro screen with an opt-out first** (Google policy).
12. **The Google ads SDK isn't added by hand.** No `play-services-ads` or `ads-mobile-sdk` in the app. Mediation is
    the one exception, and it needs exclusions (`release-troubleshooting.md`).

## 5. Workflow

Do the steps in order; each ends with a check you can run.

1. **Ad plan.** Write the `AppAds` enum and `AppAds.settings` from the scenario answers: one entry per placement, not
   per format. Two screens with a banner are two entries, so each can be tuned on its own.
   *Check:* every planned ad has an entry, with the format and style it needs.
2. **Setup** (`setup.md`): the dependencies, the manifest app ID (a placeholder per build type), and
   `Adroit.initialize` in `Application.onCreate`.
   *Check:* the app builds, and logcat (tag `Adroit`) prints `Firebase parameter <Name>: {…}` once per ad in a debug
   build.
3. **Splash** (`splash-consent-update.md`): `SplashActivity` as the launcher, then the update check, consent, the
   timer and the ad.
   *Check:* a fresh install shows the consent form (debug pretends to be in the EEA), the progress, a test ad, then the
   main screen. In airplane mode the splash takes 3 s and the app still opens.
4. **Inline ads** (`formats.md`): `AdSlot` / `AdSlotView` where the plan says, and `AdSlots.load` for the next
   screen's slots ahead of time.
   *Check:* a shimmer, then "Test Ad". With no fill, the slot collapses and takes no space.
5. **Full-screen ads** (`formats.md`): load points and show calls, with the flow continuing in the callback. Rewarded
   goes through a ViewModel.
   *Check:* the flow continues with ads on, with `{ "enabled": false }`, and offline.
6. **App open**: load it once in the main Activity, exclude the splash, and add the skip-on-leave pattern.
   *Check:* Home → back shows an ad; share sheet → back doesn't.
7. **Remote config** (`remote-config.md`): generate the console values from code and give them to the user.
8. **Tests** (`testing.md`), then the **release checklist** (`release-troubleshooting.md`).

When you finish, tell the user what still needs them: real ad unit IDs, the AdMob app ID, the GDPR message,
app-ads.txt, the Firebase parameters to publish, and anything you could not run on a device.
