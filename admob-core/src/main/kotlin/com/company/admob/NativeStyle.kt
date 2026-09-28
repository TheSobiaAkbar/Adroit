package com.company.admob

/**
 * The look of a native placement's ads, chosen in the configuration (or an ad profile) so it can change without a
 * release. The `admob-views` and `admob-compose` helpers map it to their Small, Medium and Large templates when the
 * code doesn't pass a layout of its own.
 *
 * Stability: new styles may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class NativeStyle {
    /** Icon, headline and call to action in one row. */
    SMALL,

    /** Header, body, media and call to action. The default. */
    MEDIUM,

    /** Full-width media first, then every asset. */
    LARGE,
}
