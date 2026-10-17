package io.github.thesobiaakbar.adroit.billing.internal.paywall

import android.os.SystemClock
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.EntitlementSnapshot
import io.github.thesobiaakbar.adroit.billing.PaywallCloseReason
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.PaywallModelState
import io.github.thesobiaakbar.adroit.billing.ShowDecision
import io.github.thesobiaakbar.adroit.billing.SkipReason
import io.github.thesobiaakbar.adroit.billing.internal.config.BillingConfig
import io.github.thesobiaakbar.adroit.billing.internal.config.ResolvedPaywall
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The paywall show rules (06 §5.2 `show`), with the ad profiles' counter and timer semantics (docs/AD_PROFILES.md):
 * every [shouldShow] call counts; with a counter N the paywall is due once N calls were counted since it last showed
 * (so on the Nth, 2Nth… call when it shows each time it's due) and the timer is ignored; with no counter, the timer is
 * the minimum time between two showings.
 *
 * The timer uses [elapsed] (`SystemClock.elapsedRealtime`, immune to clock changes) between showings in this process,
 * and the wall clock [now] across processes; a last showing in the future (the clock moved back) doesn't block. The
 * campaign window and the day of `maxPerDay` (local date in [zone]) use the wall clock, which the user can change:
 * fine for presentation, never for access. Counters per session live in memory; the day's showings, the last showing
 * and the launch count are saved. Main thread.
 */
internal class PaywallEngine(
    private val settings: BillingSettings,
    private val config: BillingConfig,
    private val resolver: PaywallResolver,
    private val store: ShowCounterStore,
    private val catalog: StateFlow<CatalogState>,
    private val entitlements: StateFlow<EntitlementSnapshot>,
    private val events: EventBus,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val zone: TimeZone = TimeZone.getDefault(),
) {
    private val calls = HashMap<String, Int>()
    private val shownThisSession = HashMap<String, Int>()
    private val lastShownElapsed = HashMap<String, Long>()
    private val models = HashMap<String, StateFlow<PaywallModelState>>()

    fun shouldShow(definition: PaywallDefinition, placement: String): ShowDecision {
        val name = definition.name
        if (settings.paywalls.none { it.name == name }) return ShowDecision.Skip(SkipReason.NOT_REGISTERED)
        val count = (calls[name] ?: 0) + 1
        calls[name] = count
        val paywall = config.paywall(definition)
        val reason = skipReason(paywall, count, placement)
        if (reason == null) return ShowDecision.Show
        events.emit(BillingEvent.PaywallSuppressed(name, placement, paywall.spec.campaign?.id, reason.name.lowercase(), now()))
        return ShowDecision.Skip(reason)
    }

    private fun skipReason(paywall: ResolvedPaywall, count: Int, placement: String): SkipReason? {
        val name = paywall.name
        val spec = paywall.spec
        val show = spec.show
        val nowMillis = now()
        val campaign = spec.campaign
        val hide = show.hideIfEntitled
            ?: spec.plans.flatMap { config.global.productEntitlements[it.product.name] ?: it.product.defaults.entitlements }.toSet()
        return when {
            !config.global.enabled -> SkipReason.GLOBAL_DISABLED
            !spec.enabled -> SkipReason.PAYWALL_DISABLED
            campaign != null && (campaign.startMillis?.let { nowMillis < it } == true || campaign.endMillis?.let { nowMillis > it } == true) ->
                SkipReason.OUTSIDE_CAMPAIGN
            entitlements.value.active.any { it in hide } -> SkipReason.ENTITLED
            store.launches < show.firstAfterLaunches -> SkipReason.LAUNCHES_NOT_REACHED
            show.maxPerSession > 0 && (shownThisSession[name] ?: 0) >= show.maxPerSession -> SkipReason.SESSION_CAP
            show.maxPerDay > 0 && store.shownOn(name, day(nowMillis)) >= show.maxPerDay -> SkipReason.DAILY_CAP
            show.counter > 0 && count < show.counter -> SkipReason.COUNTER_NOT_REACHED
            show.counter == 0 && show.timerSeconds > 0 && sinceLastShown(name) < show.timerSeconds * 1000L -> SkipReason.TIMER
            else -> plansReason(paywall, placement)
        }
    }

    /** [SkipReason.NO_PLANS] or [SkipReason.OFFER_UNAVAILABLE] once the catalog is ready; reports each unavailable offer. */
    private fun plansReason(paywall: ResolvedPaywall, placement: String): SkipReason? {
        val result = resolver.resolve(paywall, catalog.value)
        result.unavailable.forEach {
            events.emit(BillingEvent.OfferUnavailable(it.productId, it.basePlanId, it.requested, it.fallbackOfferId, paywall.name, placement, now()))
        }
        return (result.state as? PaywallModelState.Unavailable)?.reason
    }

    fun model(definition: PaywallDefinition): StateFlow<PaywallModelState> = models.getOrPut(definition.name) {
        combine(catalog, config.revision) { catalog, _ -> resolver.resolve(config.paywall(definition), catalog).state }
            .stateIn(scope, SharingStarted.Eagerly, resolver.resolve(config.paywall(definition), catalog.value).state)
    }

    fun recordShown(definition: PaywallDefinition) {
        val name = definition.name
        calls[name] = 0
        shownThisSession[name] = (shownThisSession[name] ?: 0) + 1
        lastShownElapsed[name] = elapsed()
        val nowMillis = now()
        store.recordShown(name, day(nowMillis), nowMillis)
    }

    fun recordClosed(definition: PaywallDefinition, placement: String, reason: PaywallCloseReason): PaywallDefinition? {
        if (reason != PaywallCloseReason.CLOSE_BUTTON && reason != PaywallCloseReason.BACK) return null
        val exitName = config.paywall(definition).spec.exitPaywall ?: return null
        val exit = settings.paywalls.firstOrNull { it.name == exitName } ?: return null
        if ((shownThisSession[exitName] ?: 0) > 0) return null
        return exit.takeIf { shouldShow(it, placement) == ShowDecision.Show }
    }

    private fun sinceLastShown(name: String): Long {
        lastShownElapsed[name]?.let { return elapsed() - it }
        val last = store.lastShownAt(name) ?: return Long.MAX_VALUE
        return (now() - last).takeIf { it >= 0 } ?: Long.MAX_VALUE
    }

    private fun day(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(millis)
}
