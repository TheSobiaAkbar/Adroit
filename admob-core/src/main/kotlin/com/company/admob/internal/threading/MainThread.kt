package com.company.admob.internal.threading

import android.os.Handler
import android.os.Looper
import com.company.admob.internal.logging.AdLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Engine state is confined to the main thread (ADR-006). These helpers are the only way work gets there.

private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

internal fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

/** Always posts [block] to the main thread, even when already on it, so callers never see a synchronous callback. */
internal fun postToMain(block: () -> Unit) {
    mainHandler.post(block)
}

/** Runs [block] immediately when on the main thread, otherwise posts it. */
internal fun runOnMain(block: () -> Unit) {
    if (isMainThread()) block() else postToMain(block)
}

internal val MainDispatcher: CoroutineDispatcher
    get() = Dispatchers.Main.immediate

/**
 * The library's coroutine scope: main-thread confined, and a failing child never cancels its siblings or crashes
 * the app. Unexpected exceptions are logged.
 */
internal fun libraryScope(log: AdLog): CoroutineScope = CoroutineScope(
    SupervisorJob() + MainDispatcher + CoroutineExceptionHandler { _, throwable ->
        log.e(throwable) { "Unexpected error inside CompanyAdMob" }
    },
)
