package io.github.thesobiaakbar.adroit.billing.internal.config

import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONException
import org.json.JSONObject

/**
 * The resolved billing configuration: the `AdroitBilling` key and one key per paywall, each field taken from the
 * first layer that sets it validly: [BillingSettings.overrides], then [BillingSettings.config], then the in-code
 * defaults. Resolved on creation and on each [reload]; every invalid field is a WARN log and a
 * [BillingEvent.ConfigInvalid], never an exception. Thread-safe.
 */
internal class BillingConfig(
    private val settings: BillingSettings,
    private val events: EventBus,
    private val log: BillingLog,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Snapshot(val global: GlobalConfig, val paywalls: Map<String, ResolvedPaywall>)

    private val warnedHard = mutableSetOf<String>()

    @Volatile
    private var current: Snapshot = resolve()

    val global: GlobalConfig
        get() = current.global

    /** Changes on each [reload], so derived state can recompute. */
    val revision: StateFlow<Int>
        get() = revisions

    private val revisions = MutableStateFlow(0)

    /** [definition]'s settings now; its defaults when it isn't registered in [BillingSettings.paywalls]. */
    fun paywall(definition: PaywallDefinition): ResolvedPaywall =
        current.paywalls[definition.name] ?: ResolvedPaywall(definition.name, definition.defaults)

    /** Re-reads every key, for example after the source's `updates` emitted. */
    fun reload() {
        current = resolve()
        revisions.value++
    }

    @Synchronized
    private fun resolve(): Snapshot {
        var global = GlobalConfig.defaults(settings)
        layers(BillingGlobalJson.KEY) { json, issue -> global = BillingGlobalJson.merge(global, json, issue) }

        val products = settings.products.associateBy { it.name }
        val paywallNames = settings.paywalls.map { it.name }.toSet()
        val paywalls = settings.paywalls.associate { definition ->
            var spec = definition.defaults
            val names = PaywallJson.Names(products, paywallNames, definition.name)
            layers(definition.name) { json, issue -> spec = PaywallJson.merge(spec, json, names, issue) }
            definition.name to ResolvedPaywall(definition.name, spec)
        }
        paywalls.values.filter { it.hard && warnedHard.add(it.name) }.forEach {
            log.log(BillingLogLevel.WARN, "Paywall ${it.name} is a hard paywall: no close button and no back")
            events.emit(BillingEvent.HardPaywall(it.name, now()))
        }
        events.emit(BillingEvent.ConfigLoaded(global.version, now()))
        return Snapshot(global, paywalls)
    }

    /** Hands each layer of [key] that holds a JSON object to [apply], lowest first (remote, then the app's override). */
    private fun layers(key: String, apply: (JSONObject, (String) -> Unit) -> Unit) {
        val remote = try {
            settings.config.raw(key)
        } catch (e: Exception) {
            invalid(key, "remote", "the source threw $e")
            null
        }
        for ((layer, raw) in listOf("remote" to remote, "override" to settings.overrides[key])) {
            if (raw.isNullOrBlank()) continue
            if (raw.length > MAX_VALUE_CHARS) {
                invalid(key, layer, "the value is over ${MAX_VALUE_CHARS / 1024} KB; ignored")
                continue
            }
            val json = try {
                JSONObject(raw)
            } catch (e: JSONException) {
                invalid(key, layer, "not a JSON object; ignored")
                continue
            }
            apply(json) { invalid(key, layer, it) }
        }
    }

    private fun invalid(key: String, layer: String, reason: String) {
        log.log(BillingLogLevel.WARN, "Config $key ($layer): $reason")
        events.emit(BillingEvent.ConfigInvalid(key, "$layer: $reason", now()))
    }

    private companion object {
        // ponytail: counts characters, not UTF-8 bytes; close enough for a guard against runaway values.
        const val MAX_VALUE_CHARS = 64 * 1024
    }
}
