# Troubleshooting

Start with the facts: log `CompanyAdMob.diagnostics.snapshot()` (the state, consent, environment, test mode, every
placement's availability and retry state, and the last error), and watch Logcat for the `CompanyAdMob` tag at DEBUG.

## No ads at all in DEVELOPMENT

- Check `CompanyAdMob.state`: nothing loads until it's `Ready`. Did you call `start(activity)`?
- `Unavailable(CONSENT_NOT_OBTAINED)`: consent doesn't allow ads yet ([below](#canrequestads-is-false)).
- `Unavailable(AD_FREE_USER)`: `setAdFree(true)` was called.
- Offline, or a first request can simply take a few seconds; look for `LoadFailed` events and their `willRetry`.
- The placement key used in XML or code must match a configured placement.

## `Failed(InvalidConfiguration)`

The configuration failed validation. `(state as AdsState.Failed).error` holds a `reason` listing every problem, and
each is logged at ERROR. The codes are listed in [Configuration](CONFIGURATION.md#validation). In DEVELOPMENT and
STAGING, `initialize` throws instead (failFast).

## `APP_ID_MISSING`

The manifest has no `com.google.android.gms.ads.APPLICATION_ID` meta-data. Add it inside `<application>`
([Getting started](GETTING_STARTED.md#3-add-the-app-id-to-the-manifest)). The library checks it at `initialize`,
so the Google SDKs never start without it.

## A crash about the app ID from UMP or the Google SDK

Both Google SDKs read the app ID from the manifest meta-data; UMP requires it even though consent runs before the
Google Mobile Ads SDK starts ([Google](https://developers.google.com/admob/android/next-gen/privacy)). If a library
merged into your app (or a manifest placeholder) produces an empty or wrong value, fix the merged manifest
(`app/build/intermediates/merged_manifests/`). The library's `APP_ID_MISSING` and `APP_ID_MALFORMED` checks catch
this at `initialize`.

## The consent form never shows

- No message is published in the AdMob UI (Privacy & messaging): UMP only shows your published messages
  ([Consent](CONSENT.md#set-up-messages-in-the-admob-ui)).
- The device isn't in a region that needs one: test with `privacy { debugGeography = DebugGeography.EEA }`.
- Debug geography works on **test devices only**: add the hashed ID UMP logs to `testDeviceIds`.
- Consent was already given: `resetConsentForTesting()`, then restart the app.

## `canRequestAds` is false

The user declined, or the form couldn't be shown (offline on first launch). Ads stay off
(`Unavailable(CONSENT_NOT_OBTAINED)`); `start()` tries again on the next launch. Make sure your settings screen offers
the privacy options entry point when required, so users can change their choice
([Consent](CONSENT.md#the-privacy-options-entry-point)).

## Duplicate class `com.google.android.gms.ads…` from `play-services-ads`

Something (usually a mediation adapter) brings the legacy `play-services-ads`. Exclude it globally, as Google requires
for the Next-Gen SDK ([Mediation](MEDIATION.md#adding-an-adapter)).

## The app open ad never shows

In order of likelihood:
- It never shows on the first launches: the first show is after `minLaunchesBeforeFirstShow` launches (default 2).
- It never shows automatically on a cold start: call `showOnColdStart` from your loading screen.
- Cooldown (4 min by default), the 30 s global interval, or the 5 s after another full-screen ad.
- The resumed Activity is excluded, `skipNextForeground()` was called, or `suppressWhile` is active.
- No ad was cached when the app came back: it loads one for next time and never shows late.

See [App open](APP_OPEN.md#how-the-library-decides).

## `NotShown(FrequencyCapped(retryAfter))`

A frequency limit applies: the placement's `minInterval` or `maxPerSession`, the global
`minIntervalBetweenFullScreenAds`, or app open's cooldown. `retryAfter` says when the next show is possible
(infinite for a session cap). Tune them in [Configuration](CONFIGURATION.md) or with
[remote overrides](REMOTE_CONFIG.md).

## Video ads don't play

Video in banner and native ads needs hardware acceleration
([Google](https://developers.google.com/admob/android/next-gen/banner#hardware_acceleration_for_video_ads)). It's on by
default; look for `android:hardwareAccelerated="false"` on the `<application>` or `<activity>`. The library logs
"isn't hardware accelerated: video ads won't play" when it finds that.

## Runtime errors with OkHttp 5

The Next-Gen SDK depends on OkHttp 4. Google's fix for apps on OkHttp 5 is the `force_use_cronet` extra on
`InitializationConfig.Builder` ([release notes](https://developers.google.com/admob/android/next-gen/rel-notes)). The
library builds that configuration itself and doesn't expose this extra yet: stay on OkHttp 4 for now, or contact the
library owners.

## R8 or ProGuard problems

The libraries ship their own consumer rules; you need no rules for them. One rule is included on purpose: without
it, the Room 2.2.5 that the Google SDK brings (with WorkManager) crashes minified apps at launch. If you still see a
`ClassNotFoundException` or `NoSuchMethodException` in a minified build, check `build/outputs/mapping/release/usage.txt`
for the class and report it. See `docs/architecture/R8_REPORT.md`.

## `NO_FILL` on new ad units

New ad units can take some time before they serve ads, and some requests simply get no ad. The library retries a
no-fill after at least 30 seconds. Check the ad unit in the AdMob UI, and test with demo ad units or test devices
meanwhile ([Google test ads](https://developers.google.com/admob/android/next-gen/test-ads)).

## Memory warnings with native ads

Native ads hold images and views until destroyed.
- `NativeApi.load` gives you a handle you own: `destroy()` it (or `close()`) when it's no longer shown.
- Prefer `AdNative`, `NativeAdHolder` (with `clear()` in `onCleared`) or `attachNativeAd`, which destroy for you.
- In a Fragment, use `viewLifecycleOwner`, not the Fragment.
- Keep `poolSize` small: 1 is enough for most placements.

## The same interstitial shows `NotShown(NotReady)` every time

The placement is `preload = PreloadStrategy.NONE` or `ON_DEMAND` and nothing preloaded it, or every load fails:
check `LoadFailed` events and `diagnostics.snapshot()` for the placement's `retryAttempt` and `nextRetryIn`.

## Test ads in the release build

The environment isn't PRODUCTION in the release build: the library logs an error and emits a
`ConfigurationWarning` (`NON_PRODUCTION_IN_RELEASE_BUILD`). Choose the environment per build type
([Configuration](CONFIGURATION.md#environments)).

## `initialize` was called twice with different configurations

The first configuration is kept; the second is logged as an error with a `ConfigurationWarning`. Call `initialize`
once, in `Application.onCreate`, and use [remote overrides](REMOTE_CONFIG.md) for runtime changes.

## Ads came back for a premium user after a restart

`setAdFree(true)` isn't persisted, on purpose: your app owns the entitlement (refunds, expired subscriptions, account
switches). Call it on every launch from your own record of the purchase, before or right after `initialize`.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
