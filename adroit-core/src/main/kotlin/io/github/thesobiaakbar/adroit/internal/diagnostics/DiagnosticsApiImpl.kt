package io.github.thesobiaakbar.adroit.internal.diagnostics

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.DiagnosticsApi
import io.github.thesobiaakbar.adroit.DiagnosticsReport
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.internal.callbacks.OnceCallback
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.port.AdsSdk
import io.github.thesobiaakbar.adroit.internal.threading.isMainThread
import io.github.thesobiaakbar.adroit.internal.threading.postToMain
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [DiagnosticsApi]. [snapshot] reads main-thread state, so a call from another thread waits (up to
 * [OFF_MAIN_TIMEOUT_MS]) for the main thread to build it, then builds a best-effort one itself. [recentEvents] is empty in PRODUCTION, where the event bus
 * doesn't record them.
 */
internal class DiagnosticsApiImpl(
    private val builder: DiagnosticsReportBuilder,
    private val adsSdk: AdsSdk,
    private val events: EventBus,
    private val environment: AdEnvironment,
    private val log: AdLog,
) : DiagnosticsApi {
    override fun snapshot(): DiagnosticsReport {
        if (isMainThread()) return builder.build()
        var report: DiagnosticsReport? = null
        val done = CountDownLatch(1)
        postToMain {
            report = builder.build()
            done.countDown()
        }
        if (done.await(OFF_MAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return checkNotNull(report)
        // Never throw for a debugging aid: a busy main thread gets a best-effort report built here instead.
        log.w { "diagnostics.snapshot(): the main thread is busy; the report may be slightly inconsistent" }
        return builder.build()
    }

    /**
     * Delegates to the SDK. In PRODUCTION the call still goes through (Google opens the inspector only on
     * registered test devices), logged at INFO so a stray call is visible.
     */
    override fun openAdInspector(activity: Activity, onClosed: (AdError?) -> Unit) {
        val closed = OnceCallback(onClosed)
        if (environment == AdEnvironment.PRODUCTION) {
            log.i { "Opening Ad Inspector in PRODUCTION: it opens only on registered test devices" }
        } else {
            log.d { "Opening Ad Inspector" }
        }
        adsSdk.openAdInspector { error ->
            if (error != null) log.w { "Ad Inspector closed with ${error.code}: ${error.message}" }
            closed(error)
        }
    }

    override val recentEvents: List<AdEvent>
        get() = if (environment == AdEnvironment.PRODUCTION) emptyList() else events.recent()

    private companion object {
        const val OFF_MAIN_TIMEOUT_MS = 2_000L
    }
}
