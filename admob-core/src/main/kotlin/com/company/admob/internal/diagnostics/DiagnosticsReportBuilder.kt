package com.company.admob.internal.diagnostics

import com.company.admob.AdAvailability
import com.company.admob.AdFormat
import com.company.admob.AdsState
import com.company.admob.ConsentState
import com.company.admob.DiagnosticsReport
import com.company.admob.NativePlacement
import com.company.admob.PlacementDiagnostics
import com.company.admob.TestModeInfo
import com.company.admob.internal.config.EffectivePlacement
import com.company.admob.internal.config.LiveConfig
import com.company.admob.internal.config.OverridesApplier
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.fullscreen.FullScreenAdEngine
import com.company.admob.internal.native.NativeAdLoaderEngine
import com.company.admob.internal.port.ActivityTracker
import com.company.admob.internal.reliability.FrequencyGate
import com.company.admob.internal.reliability.FullScreenCoordinator

/**
 * Builds [DiagnosticsReport]s from the engines' current state. Reads main-thread-confined state, so [build] runs on
 * the main thread. Ad unit IDs are shortened in PRODUCTION; the current Activity is reported by simple class name
 * only; consent is reported as its status, never as consent strings.
 */
internal class DiagnosticsReportBuilder(
    private val libraryVersion: String,
    private val googleSdkVersion: () -> String?,
    private val config: LiveConfig,
    private val state: () -> AdsState,
    private val consent: () -> ConsentState,
    private val fullScreenEngines: Map<AdFormat, FullScreenAdEngine>,
    private val nativeEngine: NativeAdLoaderEngine,
    private val frequency: FrequencyGate,
    private val coordinator: FullScreenCoordinator,
    private val activityTracker: ActivityTracker,
    private val events: EventBus,
) {
    fun build(): DiagnosticsReport {
        val current = config.current
        val state = state()
        val consent = consent()
        return DiagnosticsReport(
            libraryVersion = libraryVersion,
            googleSdkVersion = googleSdkVersion(),
            environment = current.environment,
            state = state,
            consent = consent,
            testMode = TestModeInfo(current.usesDemoAdUnits, current.testDeviceIds.size),
            // A swap alternate is listed only while it serves its key.
            placements = current.allPlacements.filter { !it.isAlternate || it.enabled }.map(::placement),
            activeFullScreen = coordinator.currentOwner,
            currentActivity = activityTracker.resumedActivity?.javaClass?.simpleName,
            lastError = events.lastError ?: (state as? AdsState.Failed)?.error ?: consent.lastError,
            activeOverrides = OverridesApplier.summarize(current.overrides, current),
        )
    }

    private fun placement(p: EffectivePlacement): PlacementDiagnostics {
        val adUnitId = config.current.displayAdUnitId(p.adUnitId)
        val shows = frequency.showsThisSession(p.placement)
        val engine = fullScreenEngines[p.format]
        if (engine != null) {
            val slot = engine.slotDiagnostics(p.placement)
            return PlacementDiagnostics(
                key = p.placement.key,
                format = p.format,
                adUnitId = adUnitId,
                availability = slot?.availability ?: engine.availability(p.placement).value,
                cachedAdAge = slot?.cachedAdAge,
                retryAttempt = slot?.retryAttempt ?: 0,
                nextRetryIn = slot?.nextRetryIn,
                enabled = p.enabled,
                showsThisSession = shows,
            )
        }
        val pool = (p.placement as? NativePlacement)?.let(nativeEngine::poolDiagnostics)
        val availability = when {
            !p.enabled -> AdAvailability.Disabled
            pool == null -> AdAvailability.Unavailable
            pool.pooled > 0 -> AdAvailability.Ready
            pool.loading -> AdAvailability.Loading
            else -> AdAvailability.Unavailable
        }
        return PlacementDiagnostics(
            key = p.placement.key,
            format = p.format,
            adUnitId = adUnitId,
            availability = availability,
            cachedAdAge = pool?.oldestAge,
            retryAttempt = pool?.retryAttempt ?: 0,
            nextRetryIn = pool?.nextRetryIn,
            enabled = p.enabled,
            showsThisSession = shows,
        )
    }
}
