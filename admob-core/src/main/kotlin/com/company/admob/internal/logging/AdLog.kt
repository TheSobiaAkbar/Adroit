package com.company.admob.internal.logging

import com.company.admob.logging.AdLogger
import com.company.admob.logging.LogLevel

/**
 * Internal logging front end: filters by [minLevel] before building messages, so disabled levels cost nothing.
 * Never throws, even if the host's [AdLogger] does. Thread-safe.
 */
internal class AdLog(
    private val logger: AdLogger,
    private val minLevel: LogLevel,
    private val tag: String = TAG,
) {
    fun isEnabled(level: LogLevel): Boolean =
        level != LogLevel.NONE && minLevel != LogLevel.NONE && level.ordinal >= minLevel.ordinal

    inline fun v(message: () -> String) {
        if (isEnabled(LogLevel.VERBOSE)) write(LogLevel.VERBOSE, message(), null)
    }

    inline fun d(message: () -> String) {
        if (isEnabled(LogLevel.DEBUG)) write(LogLevel.DEBUG, message(), null)
    }

    inline fun i(message: () -> String) {
        if (isEnabled(LogLevel.INFO)) write(LogLevel.INFO, message(), null)
    }

    inline fun w(throwable: Throwable? = null, message: () -> String) {
        if (isEnabled(LogLevel.WARN)) write(LogLevel.WARN, message(), throwable)
    }

    inline fun e(throwable: Throwable? = null, message: () -> String) {
        if (isEnabled(LogLevel.ERROR)) write(LogLevel.ERROR, message(), throwable)
    }

    fun write(level: LogLevel, message: String, throwable: Throwable?) {
        try {
            logger.log(level, tag, message, throwable)
        } catch (_: Throwable) {
            // A faulty host logger must never break ads.
        }
    }

    companion object {
        const val TAG: String = "CompanyAdMob"
    }
}
