package io.github.thesobiaakbar.adroit.logging

/**
 * Log verbosity, from most verbose to [NONE] (logging off).
 *
 * Stability: the levels won't change before the next major version, so an exhaustive `when` is safe.
 */
public enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE,
}
