package io.github.thesobiaakbar.adroit.config

/**
 * When the library loads ads for a placement. Ignored for banners, which load when their view is created.
 *
 * Stability: new strategies may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class PreloadStrategy {
    /** Load as soon as the library is ready, and reload after each show. The default. */
    AFTER_READY,

    /** Load on the first `preload()` or show attempt, then reload after each show. */
    ON_DEMAND,

    /** Load only on an explicit `preload()`. Never reload automatically. */
    NONE,
}
