package com.company.admob

import android.app.Activity
import com.company.admob.config.AdEnvironment
import com.company.admob.events.AdEvent
import kotlin.time.Duration

/** Debugging aids. Obtain it from [AdsClient.diagnostics]. */
public interface DiagnosticsApi {
    /** A snapshot of the library's state. Its `toString()` is a readable report, redacted in production. */
    public fun snapshot(): DiagnosticsReport

    /**
     * Opens Google's Ad Inspector. Works only on registered test devices. [onClosed] is called on the main thread
     * with null, or with the error that closed it.
     */
    public fun openAdInspector(activity: Activity, onClosed: (AdError?) -> Unit = {})

    /** The most recent events (up to 100), oldest first. Always empty in production. */
    public val recentEvents: List<AdEvent>
}

/**
 * A point-in-time report of the library's state. Immutable. New properties may be added in minor versions.
 *
 * @property libraryVersion CompanyAdMob version.
 * @property googleSdkVersion Google Mobile Ads SDK version, when known.
 * @property environment configured environment, or null before `initialize()`.
 * @property state library-wide state.
 * @property consent current consent information.
 * @property testMode how test ads are configured, or null before `initialize()`.
 * @property placements per-placement details.
 * @property activeFullScreen placement key of the full-screen ad showing now, if any.
 * @property currentActivity simple class name of the resumed Activity, if any.
 * @property lastError the most recent error reported by the library, if any.
 */
public class DiagnosticsReport(
    public val libraryVersion: String,
    public val googleSdkVersion: String?,
    public val environment: AdEnvironment?,
    public val state: AdsState,
    public val consent: ConsentState,
    public val testMode: TestModeInfo?,
    public val placements: List<PlacementDiagnostics>,
    public val activeFullScreen: String?,
    public val currentActivity: String?,
    public val lastError: AdError?,
) {
    override fun toString(): String = buildString {
        appendLine("CompanyAdMob diagnostics")
        appendLine("  library version:   $libraryVersion")
        appendLine("  Google SDK:        ${googleSdkVersion ?: "unknown"}")
        appendLine("  environment:       ${environment ?: "not initialized"}")
        appendLine("  state:             $state")
        appendLine("  consent:           $consent")
        appendLine("  test mode:         ${testMode ?: "n/a"}")
        appendLine("  active full-screen: ${activeFullScreen ?: "none"}")
        appendLine("  current activity:  ${currentActivity ?: "none"}")
        appendLine("  last error:        ${lastError?.let { "${it.code}: ${it.message}" } ?: "none"}")
        appendLine("  placements (${placements.size}):")
        placements.forEach { appendLine("    $it") }
    }
}

/**
 * Diagnostics for one placement. Immutable.
 *
 * @property adUnitId the effective ad unit ID, shortened to its last 4 characters in production.
 * @property cachedAdAge age of the cached ad, or null if none is cached.
 * @property retryAttempt number of consecutive failed loads.
 * @property nextRetryIn time until the next scheduled retry, or null if none.
 */
public class PlacementDiagnostics(
    public val key: String,
    public val format: AdFormat,
    public val adUnitId: String,
    public val availability: AdAvailability,
    public val cachedAdAge: Duration?,
    public val retryAttempt: Int,
    public val nextRetryIn: Duration?,
) {
    override fun toString(): String =
        "$key [$format] unit=$adUnitId availability=$availability cachedAdAge=${cachedAdAge ?: "-"} " +
            "retryAttempt=$retryAttempt nextRetryIn=${nextRetryIn ?: "-"}"
}

/**
 * How test ads are configured.
 *
 * @property demoAdUnitsSubstituted true when Google's demo ad units replace the configured IDs.
 * @property testDeviceCount number of registered test device IDs.
 */
public class TestModeInfo(
    public val demoAdUnitsSubstituted: Boolean,
    public val testDeviceCount: Int,
) {
    override fun toString(): String =
        "TestModeInfo(demoAdUnitsSubstituted=$demoAdUnitsSubstituted, testDeviceCount=$testDeviceCount)"
}
