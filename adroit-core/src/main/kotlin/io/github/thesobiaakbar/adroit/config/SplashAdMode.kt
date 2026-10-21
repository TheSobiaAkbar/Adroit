package io.github.thesobiaakbar.adroit.config

/**
 * Whether the splash shows its ad (Firebase `AdSettings.splashAdMode`, docs/SPLASH.md). With [PRELOAD] and [LATER]
 * the splash only does consent, the start and routing, and reports `SplashState.Finished.showAdLater`: the app shows
 * the same ad with `AdSlots.showFullScreen` on the next screen, for example on the paywall's close button.
 */
public enum class SplashAdMode {
    /** The splash loads and shows its ad, on the `splashSeconds` timer. The default. */
    SHOW,

    /** The splash loads its ad and keeps it for later, on the short `premiumSplashSeconds` timer. */
    PRELOAD,

    /** The splash doesn't request its ad, on the short `premiumSplashSeconds` timer; it loads when shown later. */
    LATER,
}
