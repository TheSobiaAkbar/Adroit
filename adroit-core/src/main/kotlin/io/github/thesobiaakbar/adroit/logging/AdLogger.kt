package io.github.thesobiaakbar.adroit.logging

/**
 * Destination for Adroit log messages. The default is [AndroidAdLogger] (logcat).
 *
 * Implement it to route logs to Timber, a crash reporter, or a test. Called on any thread; implementations must be
 * thread-safe and must not throw. Adroit never passes consent strings, advertising IDs, or user identifiers.
 */
public fun interface AdLogger {
    /** Logs [message] at [level]. [level] is never [LogLevel.NONE]. */
    public fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}
