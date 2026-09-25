package com.company.admob

/**
 * The ad formats CompanyAdMob supports.
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
