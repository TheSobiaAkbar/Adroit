# PROMPT 29: Load timeouts, loading screens, shimmer and refresh

Requested by the user on 2026-09-28, after PROMPT 28 and the `loadAgain` change (`bfb023d`). Builds on ad profiles
(docs/AD_PROFILES.md): every new setting is a field of the ad's JSON, so Firebase can change it per ad.

## Requirements

1. Complete example Firebase values for every ad format and for `AdSettings` (docs).
2. **Load timeout for full-screen ads.** `loadTimeoutSeconds` per ad (default 8). When the app shows an ad that isn't
   ready, the show waits for it at most that long, then gives up (`NotShown(Timeout)`), so the app moves on and is
   never stuck on a slow network. The splash keeps its own timer (`splashSeconds`, from `AdSettings`).
3. **Shimmer for banners and native ads**, shaped like the ad that will appear: the banner's size (adaptive,
   collapsible, rectangle, standard, inline adaptive) and the native template (small, medium, large), as currently
   configured, Firebase included.
4. **`showLoading` per ad.**
   - Full-screen ads: a "Loading ad" screen before the ad, shown briefly even when the ad is ready, then dismissed
     when the ad opens. Hidden if the ad fails to load or the timeout passes.
   - Banners and native ads: the shimmer while the ad loads; hidden when it shows, or when the load fails (the slot
     then collapses as today).
5. **One request per ad view.** A banner or native view (or holder, or composable) that has an ad, or a request in
   progress, never requests another one. `refresh = true` on an explicit load requests a new ad while the current one
   stays on screen, with no shimmer; the new ad replaces it when it arrives. If it fails, the current ad stays.

## Phases

- **29A. Load timeout.** `loadTimeoutSeconds` in `AdProfile` and the JSON (full-screen formats). `AdSlots.showFullScreen`
  (and the rewarded shows) on an ad that isn't ready: request it (an explicit ask, like `AdSlots.load`), wait up to
  the timeout, show it if it arrives in time, otherwise `NotShown(Timeout)`. Engine-level, on virtual time.
- **29B. Loading screen.** `showLoading` (full-screen). A library dialog ("Loading ad…", replaceable layout
  `company_admob_loading_ad.xml`) shown before the ad for at least a short moment, and during the 29A wait; dismissed
  when the ad opens, fails, or times out. Never left on screen (Activity destroyed, show error).
- **29C. Shimmer.** A dependency-free shimmer drawable; skeletons per banner size and per native template, sized as
  the real ad (adaptive banner height from the width). In `AdBannerView`, `AdNativeView`, `AdSlotView`,
  `NativeAdHolder` and the Compose `AdBanner`, `AdNative` and `AdSlot`; follows format swaps and style changes from
  Firebase. `showLoading` (banner/native) turns it on.
- **29D. One request per view, and refresh.** A public `load(refresh: Boolean = false)` on the views, the holder and
  the composables' state: a no-op while an ad is shown or a request runs; with `refresh`, a background request that
  swaps in on success and keeps the old ad on failure.
- **29E. Docs, template and device pass.** AD_PROFILES.md fields, the integration guide, the Firebase template, the
  sample (shimmer on every inline ad, a refresh button), and a Pixel 7a pass.

## Decisions

The user's answers (2026-09-28):

- **Late ad:** an ad that arrives after its load timeout is kept for the next show; it's already loaded.
- **`showLoading` defaults to `true`**: every full-screen ad gets the loading screen and every banner or native ad
  the shimmer, unless the ad (or `AdSettings`) turns it off.
- **Loading screen:** at least **1 second** when the ad is already loaded (`loadingSeconds`, changeable).
- **Scope:** `loadTimeoutSeconds`, `showLoading` and `loadingSeconds` are per ad, with defaults in `AdSettings`; an
  ad's own value wins.
