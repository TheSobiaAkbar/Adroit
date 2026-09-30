package io.github.thesobiaakbar.adroit.config

/**
 * Forces the geography UMP assumes, to test consent flows. Only honoured on test devices, and rejected by
 * validation in [AdEnvironment.PRODUCTION].
 *
 * Stability: new values may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class DebugGeography {
    /** No override: UMP uses the device's real location. */
    DISABLED,

    /** Behave as if the device is in the EEA or UK. */
    EEA,

    /** Behave as if the device is in a regulated US state. */
    REGULATED_US_STATE,

    /** Behave as if the device is in an unregulated region. */
    OTHER,
}
