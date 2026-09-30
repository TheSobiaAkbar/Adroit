package io.github.thesobiaakbar.adroit.logging

import android.util.Log

/** [AdLogger] that writes to logcat. The only place Adroit uses `android.util.Log`. Thread-safe. */
public class AndroidAdLogger : AdLogger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message, throwable)
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
            LogLevel.NONE -> Unit
        }
    }
}
