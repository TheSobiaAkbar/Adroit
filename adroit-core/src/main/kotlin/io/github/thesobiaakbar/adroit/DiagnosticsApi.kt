package io.github.thesobiaakbar.adroit

import android.app.Activity
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.events.redactAdUnitIdsIn
import kotlin.time.Duration

/** Debugging aids. Obtain it from [AdsClient.diagnostics]. */
public interface DiagnosticsApi {
    /** A snapshot of the library's state. Its `toString()` is a readable report, redacted in production. */
    public fun snapshot(): DiagnosticsReport

    /**
     * Opens Google's Ad Inspector. Works only on registered test devices; Google ignores the call on other devices,
     * including in production. [onClosed] is called once, on the main thread, with null, or with the error that
     * closed it (or kept it from opening).
     */
    public fun openAdInspector(activity: Activity, onClosed: (AdError?) -> Unit = {})

    /** The most recent events (up to 100), oldest first. Always empty in production. */
    public val recentEvents: List<AdEvent>
}

/**
 * A point-in-time report of the library's state. Immutable. New properties may be added in minor versions.
 *
 * [toString] is a readable multi-line report for bug reports and logs. It never contains consent strings or
 * advertising IDs, and in PRODUCTION every ad unit ID in it is shortened to its last 4 digits.
 *
 * @property libraryVersion Adroit version.
 * @property googleSdkVersion Google Mobile Ads SDK version, when known.
 * @property environment configured environment, or null before `initialize()`.
 * @property state library-wide state.
 * @property consent current consent information.
 * @property testMode how test ads are configured, or null before `initialize()`.
 * @property placements per-placement details.
 * @property activeFullScreen placement key of the full-screen ad showing now, if any.
 * @property currentActivity simple class name of the resumed Activity, if any.
 * @property lastError the most recent error reported by the library, if any.
 * @property activeOverrides a readable summary of the remote overrides in force (ad unit IDs shortened in
 *   PRODUCTION), or "none".
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
    public val activeOverrides: String,
) {
    override fun toString(): String = redact(buildString {
        appendLine("Adroit diagnostics")
        appendLine("  library version:   $libraryVersion")
        appendLine("  Google SDK:        ${googleSdkVersion ?: "unknown"}")
        appendLine("  environment:       ${environment ?: "not initialized"}")
        appendLine("  state:             $state")
        appendLine("  consent:           $consent")
        appendLine("  test mode:         ${testMode ?: "n/a"}")
        appendLine("  active full-screen: ${activeFullScreen ?: "none"}")
        appendLine("  current activity:  ${currentActivity ?: "none"}")
        appendLine("  last error:        ${lastError?.let { "${it.code}: ${it.message}" } ?: "none"}")
        appendLine("  remote overrides:  $activeOverrides")
        appendLine("  placements (${placements.size}):")
        placements.forEach { appendLine("    $it") }
    })

    /** In PRODUCTION, shortens any full ad unit ID (for example inside an error message) to its last 4 digits. */
    private fun redact(report: String): String =
        if (environment != AdEnvironment.PRODUCTION) report else redactAdUnitIdsIn(report)
}

/**
 * Diagnostics for one placement. Immutable.
 *
 * @property adUnitId the effective ad unit ID (remote override and demo substitution applied), shortened to its
 *   last 4 characters in production.
 * @property enabled false when the placement is turned off by the local or remote configuration.
 * @property cachedAdAge age of the cached (for native: oldest pooled) ad, or null if none is cached.
 * @property retryAttempt number of consecutive failed loads.
 * @property nextRetryIn time until the next scheduled retry, or null if none.
 * @property showsThisSession full-screen ads shown for this placement in this process, as counted by the frequency
 *   limits; 0 for banners and native ads.
 */
public class PlacementDiagnostics(
    public val key: String,
    public val format: AdFormat,
    public val adUnitId: String,
    public val availability: AdAvailability,
    public val cachedAdAge: Duration?,
    public val retryAttempt: Int,
    public val nextRetryIn: Duration?,
    public val enabled: Boolean,
    public val showsThisSession: Int,
) {
    override fun toString(): String =
        "$key [$format] unit=$adUnitId enabled=$enabled availability=$availability " +
            "cachedAdAge=${cachedAdAge ?: "-"} retryAttempt=$retryAttempt nextRetryIn=${nextRetryIn ?: "-"} " +
            "showsThisSession=$showsThisSession"
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
