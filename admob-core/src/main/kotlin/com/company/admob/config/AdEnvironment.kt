package com.company.admob.config

/**
 * The environment the app runs in. It decides whether Google's demo ad units are used and which configuration
 * mistakes are fatal. See docs/architecture/02_ARCHITECTURE.md §20.3.
 *
 * Stability: the three environments won't change before the next major version, so an exhaustive `when` is safe.
 */
public enum class AdEnvironment {
    /** Development builds. Google's demo ad units are always used, whatever IDs are configured. */
    DEVELOPMENT,

    /** QA/staging builds. Demo ad units by default; real ad units only with registered test devices. */
    STAGING,

    /** Released builds. Real ad units only; demo IDs are rejected. */
    PRODUCTION,
}
