package io.github.thesobiaakbar.adroit.billing

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.thesobiaakbar.adroit.billing.internal.config.ResolvedPaywall
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import io.github.thesobiaakbar.adroit.billing.internal.paywall.PaywallEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether a paywall should show now ([Paywalls.shouldShow]). */
public sealed interface ShowDecision {
    /** Show it. */
    public data object Show : ShowDecision

    /** Don't show it, because of [reason]. */
    public data class Skip(public val reason: SkipReason) : ShowDecision
}

/**
 * Why a paywall doesn't show, in the order the rules are checked (06 §5.2 `show`).
 *
 * Stability: new reasons may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class SkipReason {
    /** `AdroitBilling.initialize` hasn't run. */
    NOT_INITIALIZED,

    /** The paywall isn't in `BillingSettings.paywalls`. */
    NOT_REGISTERED,

    /** The `AdroitBilling` key has `"enabled": false`. */
    GLOBAL_DISABLED,

    /** The paywall has `"enabled": false`. */
    PAYWALL_DISABLED,

    /** Before `campaign.start` or after `campaign.end`, by the device clock. */
    OUTSIDE_CAMPAIGN,

    /** The user has one of `show.hideIfEntitled` (by default, an entitlement of the paywall's products). */
    ENTITLED,

    /** The app hasn't been launched `show.firstAfterLaunches` times yet. */
    LAUNCHES_NOT_REACHED,

    /** Shown `show.maxPerSession` times in this session already. */
    SESSION_CAP,

    /** Shown `show.maxPerDay` times today (local date) already. */
    DAILY_CAP,

    /** `show.counter` isn't reached yet. */
    COUNTER_NOT_REACHED,

    /** Less than `show.timerSeconds` since it last showed. */
    TIMER,

    /** No plan can be bought: every product is missing from Play, or every plan was hidden. */
    NO_PLANS,

    /** A plan's offer isn't available and `onOfferUnavailable` is `HIDE_PAYWALL`. */
    OFFER_UNAVAILABLE,
}

/** A paywall's content as Play allows it for this user ([Paywalls.model]). */
public sealed interface PaywallModelState {
    /** The products haven't loaded yet. */
    public data object Loading : PaywallModelState

    /** Ready to show. */
    public data class Ready(public val model: PaywallModel) : PaywallModelState

    /** Nothing can be shown, because of [reason] ([SkipReason.NO_PLANS] or [SkipReason.OFFER_UNAVAILABLE]). */
    public data class Unavailable(public val reason: SkipReason) : PaywallModelState

    /** The products couldn't be loaded. */
    public data class Failed(public val error: BillingError) : PaywallModelState
}

/**
 * A paywall resolved against Play's catalog: its settings, and the plans that can be bought, each with its chosen
 * offer. Every price, trial and period in it comes from Play.
 *
 * @property name the [PaywallDefinition.name].
 * @property spec the settings in force (app overrides > Firebase > defaults).
 * @property plans the plans to show, in order: plans of missing products and hidden plans are left out.
 * @property selected the index in [plans] selected when the paywall opens.
 */
public data class PaywallModel(
    public val name: String,
    public val spec: PaywallSpec,
    public val plans: List<PaywallPlan>,
    public val selected: Int,
)

/**
 * One plan of a [PaywallModel].
 *
 * @property spec the configured plan.
 * @property product Play's product.
 * @property basePlanId the subscription's base plan; null for a one-time product.
 * @property offer the subscription offer to buy, eligible for this user; null for a one-time product.
 * @property option the one-time purchase option to buy; null for a subscription.
 * @property offerUnavailable true when the configured offer isn't available and the base plan is shown instead.
 * @property savingsPercent how much cheaper per day than [PlanSpec.savingsVs] it renews, when Play's prices say so.
 * @property texts the paywall's texts for this plan, placeholders filled from [offer]. A null text uses the UI's
 *   default. [PaywallTexts.cta] is already `ctaTrial` when [offer] has a free trial; [PaywallTexts.ctaTrial] is null.
 */
public data class PaywallPlan(
    public val spec: PlanSpec,
    public val product: ProductInfo,
    public val basePlanId: String?,
    public val offer: OfferInfo?,
    public val option: OneTimeOption?,
    public val offerUnavailable: Boolean,
    public val savingsPercent: Int?,
    public val texts: PaywallTexts,
) {
    /** The offer ID to purchase: [OfferInfo.offerId] or [OneTimeOption.offerId]; null for the regular price. */
    public val offerId: String?
        get() = offer?.offerId ?: option?.offerId

    /** The [PurchaseRequest.offer] that buys exactly this plan. */
    public val offerSelection: OfferSelection
        get() = offerId?.let(OfferSelection::Id) ?: OfferSelection.BaseOnly
}

/**
 * Paywall show rules and content (`AdroitBilling.paywalls`). The UI modules use it; a custom paywall can too:
 *
 * ```
 * if (AdroitBilling.paywalls.shouldShow(AppPaywalls.Onboarding, "onboarding") == ShowDecision.Show) { … }
 * ```
 */
public class Paywalls internal constructor(
    private val engine: () -> PaywallEngine?,
    private val billing: BillingClientApi,
    private val events: EventBus,
    private val foreground: () -> Flow<Boolean> = {
        ProcessLifecycleOwner.get().lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
    },
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val definitions: () -> List<PaywallDefinition> = { emptyList() },
) {
    private val loading = MutableStateFlow<PaywallModelState>(PaywallModelState.Loading).asStateFlow()

    /**
     * Whether [paywall] should show now at [placement]. Checks, in order: the global and paywall `enabled` flags, the
     * campaign window, `hideIfEntitled`, `firstAfterLaunches`, `maxPerSession`, `maxPerDay`, the counter (every call
     * counts, as with the ads), the timer, and whether any plan can be bought. While the products are loading it
     * answers [ShowDecision.Show] and the paywall shows its loading state. A skip is reported as
     * [BillingEvent.PaywallSuppressed]. Main thread.
     */
    public fun shouldShow(paywall: PaywallDefinition, placement: String): ShowDecision =
        engine()?.shouldShow(paywall, placement) ?: ShowDecision.Skip(SkipReason.NOT_INITIALIZED)

    /**
     * The registered paywall named [name] (its [PaywallDefinition.name], for example from an Intent extra or an XML
     * attribute), or null when `BillingSettings.paywalls` has none or `AdroitBilling.initialize` hasn't run.
     */
    public fun definition(name: String): PaywallDefinition? = definitions().firstOrNull { it.name == name }

    /** [paywall]'s content, updated when the products or the configuration change. */
    public fun model(paywall: PaywallDefinition): StateFlow<PaywallModelState> = engine()?.model(paywall) ?: loading

    /**
     * A [PaywallController] for [paywall] opened at [placement], working in [scope] (a `viewModelScope`). It is
     * [PaywallUiState.Unavailable] when [paywall] can't be shown at all (disabled, out of its campaign, the user is
     * entitled, no plans): the frequency rules aren't checked again, since the app already decided to show it.
     */
    public fun controller(paywall: PaywallDefinition, placement: String, scope: CoroutineScope): PaywallController {
        val engine = engine()
        val resolved = engine?.resolved(paywall) ?: ResolvedPaywall(paywall.name, paywall.defaults)
        return PaywallController(paywall, placement, scope, billing, engine, events, resolved, foreground(), clock)
    }

    /**
     * Records that [paywall] is now visible, for the caps, counter and timer. [PaywallController.onShown] calls it;
     * a custom paywall without a controller calls it itself. Main thread.
     */
    public fun recordShown(paywall: PaywallDefinition, placement: String) {
        engine()?.recordShown(paywall)
    }

    /**
     * Records that the user closed [paywall], and returns its `exitPaywall` to show next, or null: only after
     * [PaywallCloseReason.CLOSE_BUTTON] or [PaywallCloseReason.BACK], at most once per session, and only when
     * [shouldShow] allows it. Main thread.
     */
    public fun recordClosed(paywall: PaywallDefinition, placement: String, reason: PaywallCloseReason): PaywallDefinition? =
        engine()?.recordClosed(paywall, placement, reason)
}
