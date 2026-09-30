package io.github.thesobiaakbar.adroit

/**
 * The single, final outcome of a full-screen ad show request (interstitial, app open).
 *
 * Delivered exactly once, on the main thread. Continue the app flow in both branches: ads never block the app.
 *
 * Stability: the two outcomes won't change before the next major version, so an exhaustive `when` is safe.
 */
public sealed interface FullScreenResult {
    /** True only when the ad was actually shown ([Dismissed]). */
    public val wasShown: Boolean

    /** The ad was shown and then dismissed by the user. [clicked] is true if the user tapped the ad. */
    public data class Dismissed(public val clicked: Boolean) : FullScreenResult {
        override val wasShown: Boolean get() = true
    }

    /** The ad was not shown, because of [error]. */
    public data class NotShown(public val error: AdError) : FullScreenResult {
        override val wasShown: Boolean get() = false
    }
}
