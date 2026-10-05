package io.github.thesobiaakbar.adroit.internal.config

import io.github.thesobiaakbar.adroit.config.remote.AdConfigProvider
import io.github.thesobiaakbar.adroit.config.remote.AdOverrides
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Applies the host's remote overrides (architecture §21): collects [provider] in the library [scope] (main thread),
 * validates each emission against the local configuration, and publishes the result to [live]. Each emission replaces
 * the previous overrides entirely, so emitting `AdOverrides()` restores the local values.
 *
 * Invalid entries are dropped with an error log and a [AdEvent.ConfigurationWarning]; the rest of the emission still
 * applies. A provider that throws (when called or while emitting) ends the collection and keeps the last good
 * overrides in force.
 */
internal class OverridesApplier(
    private val live: LiveConfig,
    private val provider: AdConfigProvider,
    private val events: EventBus,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val log: AdLog,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            try {
                // The scope's dispatcher is immediate, and start() runs inside initialize(): without this, providers
                // would be collected before initialize() returns, when Adroit has no runtime yet, and
                // AdroitInternal.configuredPlacementFormats() would be empty (FirebaseRemoteConfigProvider would then
                // read no ad's parameter for the whole session).
                yield()
                provider.overrides().collect(::apply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "The remote configuration provider $provider failed; keeping the last good overrides" }
            }
        }
    }

    /** Validates [overrides] and applies what's valid. Main thread. */
    fun apply(overrides: AdOverrides) {
        val current = live.current
        val validation = ConfigValidator.validateOverrides(overrides, current.source)
        for (issue in validation.issues) {
            log.e { issue.message }
            events.emit(AdEvent.ConfigurationWarning(issue.message, clock.currentTimeMillis()))
        }
        if (validation.accepted == current.overrides) return
        log.i { "Remote overrides applied: ${summarize(validation.accepted, current)}" }
        live.update(current.withOverrides(validation.accepted))
    }

    companion object {
        /**
         * A one-line description of [overrides] for logs and diagnostics, or "none". Ad unit IDs are shortened in
         * PRODUCTION, as everywhere else.
         */
        fun summarize(overrides: AdOverrides, config: EffectiveConfig): String {
            val parts = mutableListOf<String>()
            overrides.adsEnabled?.let { parts += "adsEnabled=$it" }
            if (overrides.disabledFormats.isNotEmpty()) parts += "disabledFormats=${overrides.disabledFormats.sorted()}"
            overrides.minIntervalBetweenFullScreenAds?.let { parts += "minIntervalBetweenFullScreenAds=$it" }
            overrides.maxInterstitialsPerSession?.let { parts += "maxInterstitialsPerSession=$it" }
            for ((key, o) in overrides.placements) {
                val fields = listOfNotNull(
                    o.enabled?.let { "enabled=$it" },
                    o.minInterval?.let { "minInterval=$it" },
                    o.maxPerSession?.let { "maxPerSession=$it" },
                    o.preload?.let { "preload=$it" },
                    o.effectiveAdUnitIds?.let { ids -> "adUnitIds=${ids.map(config::displayAdUnitId)}" },
                    o.counter?.let { "counter=$it" },
                    o.counterPlan?.let { "counterPlan=$it" },
                    o.bannerSize?.let { "bannerSize=$it" },
                    o.nativeStyle?.let { "nativeStyle=$it" },
                    o.splashTimeout?.let { "splashTimeout=$it" },
                )
                parts += "$key(${fields.joinToString(", ")})"
            }
            return if (parts.isEmpty()) "none" else parts.joinToString("; ")
        }
    }
}
