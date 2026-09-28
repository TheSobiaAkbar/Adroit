package com.company.admob.config

/**
 * The maximum content rating of ads served to the app. See Google's "Ad content filtering" guide.
 *
 * Stability: these ratings (Google's four) won't change before the next major version, so an exhaustive `when` is safe.
 */
public enum class MaxAdContentRating {
    /** General audiences. */
    G,

    /** Parental guidance. */
    PG,

    /** Teen. */
    T,

    /** Mature audiences. */
    MA,
}
