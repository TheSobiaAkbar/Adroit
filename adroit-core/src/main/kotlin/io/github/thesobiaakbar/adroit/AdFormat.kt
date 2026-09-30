package io.github.thesobiaakbar.adroit

/**
 * The ad formats Adroit supports.
 *
 * Stability: new formats may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class AdFormat {
    BANNER,
    INTERSTITIAL,
    REWARDED,
    REWARDED_INTERSTITIAL,
    APP_OPEN,
    NATIVE,
}
