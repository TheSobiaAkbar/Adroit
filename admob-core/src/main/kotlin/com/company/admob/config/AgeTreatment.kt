package com.company.admob.config

/**
 * How ad requests treat the audience's age. Passed to the Google Mobile Ads SDK as the age-restricted treatment,
 * and to UMP as the under-age tag for [CHILD]. Deciding the right value for your app is your responsibility.
 */
public enum class AgeTreatment {
    /** No age-specific treatment. */
    UNSPECIFIED,

    /** Child-directed treatment (for example COPPA). */
    CHILD,

    /** Treatment for users under the age of consent (for example GDPR teen). */
    TEEN,
}
