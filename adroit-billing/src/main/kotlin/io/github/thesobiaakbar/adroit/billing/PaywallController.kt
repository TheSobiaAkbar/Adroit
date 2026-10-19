package io.github.thesobiaakbar.adroit.billing

import android.app.Activity
import android.os.SystemClock
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.config.ResolvedPaywall
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import io.github.thesobiaakbar.adroit.billing.internal.paywall.PaywallEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The state of one open paywall, independent of the UI toolkit: plans, selection, the close button's delay from the
 * configuration, the back policy, purchase, restore and manage. Create it with `AdroitBilling.paywalls.controller`,
 * keep it in a ViewModel, and render [state]. Every call is on the main thread.
 *
 * The close button follows `close.delaySeconds`: 0 shows it at once, N after N seconds of the paywall being **visible**
 * (from [onShown]; the count pauses while the app is in the background, and a second cut short counts again), -1
 * never. A paywall that can't load ([PaywallUiState.Loading], [PaywallUiState.Unavailable]) can always be closed, so
 * a broken paywall never traps the user.
 *
 * The plans are frozen when they first load: a configuration update applies to the next opening.
 */
public class PaywallController internal constructor(
    /** The paywall. */
    public val paywall: PaywallDefinition,
    /** Where it was opened, reported with its events and purchases. */
    public val placement: String,
    private val scope: CoroutineScope,
    private val billing: BillingClientApi,
    private val engine: PaywallEngine?,
    private val events: EventBus,
    resolved: ResolvedPaywall,
    foreground: Flow<Boolean>,
    private val clock: () -> Long,
    fixed: PaywallModel? = null,
) {
    /**
     * The paywall's settings in force (app overrides > Firebase > defaults), fixed when it opened: the template, style
     * and close behavior to render, available before the plans load.
     */
    public val spec: PaywallSpec = resolved.spec
    private val close = spec.close
    private val campaignId = spec.campaign?.id

    private val mutableState = MutableStateFlow<PaywallUiState>(PaywallUiState.Loading)

    /** What to show now. */
    public val state: StateFlow<PaywallUiState> = mutableState.asStateFlow()

    private var model: PaywallModel? = null
    private var selected: Int? = null
    private var remaining = close.delaySeconds.coerceAtLeast(0)
    private var closeVisible = close.delaySeconds == 0
    private var busy: Busy? = null
    private var message: UiMessage? = null
    private var shown = false
    private var shownBefore = false
    private var inForeground = true
    private var visibleMillis = 0L
    private var visibleSince: Long? = null
    private var ticker: Job? = null
    private val jobs = mutableListOf<Job>()

    init {
        val blocked = when {
            fixed != null -> null
            engine == null -> SkipReason.NOT_INITIALIZED
            else -> engine.availability(resolved)
        }
        if (fixed != null) {
            model = fixed
            selected = fixed.selected.coerceIn(0, fixed.plans.lastIndex)
            publish()
        } else if (blocked != null) {
            mutableState.value = PaywallUiState.Unavailable(blocked, null)
        } else {
            jobs += scope.launch {
                when (val loaded = engine!!.model(paywall).first { it != PaywallModelState.Loading }) {
                    is PaywallModelState.Ready -> {
                        model = loaded.model
                        selected = (selected ?: loaded.model.selected).coerceIn(0, loaded.model.plans.lastIndex)
                        if (mutableState.value == PaywallUiState.Loading) publish()
                        if (shown) reportOffer()
                    }
                    is PaywallModelState.Unavailable -> unavailable(PaywallUiState.Unavailable(loaded.reason, null))
                    is PaywallModelState.Failed -> unavailable(PaywallUiState.Unavailable(null, loaded.error))
                    PaywallModelState.Loading -> Unit
                }
            }
        }
        jobs += scope.launch {
            foreground.collect {
                inForeground = it
                updateClock()
            }
        }
    }

    /** Call when the paywall's first frame is visible: starts the close-button delay and reports the showing. */
    public fun onShown() {
        if (shown) return
        shown = true
        if (!shownBefore && mutableState.value !is PaywallUiState.Unavailable) {
            engine?.recordShown(paywall)
            events.emit(BillingEvent.PaywallShown(paywall.name, placement, campaignId, now()))
            if (close.delaySeconds == 0) events.emit(BillingEvent.PaywallCloseButtonShown(paywall.name, placement, campaignId, 0, now()))
            if (model != null) reportOffer()
        }
        updateClock()
    }

    /** Selects plan [index]. Ignored while busy or out of range. */
    public fun select(index: Int) {
        val plans = model?.plans ?: return
        if (busy != null || index !in plans.indices || index == selected || mutableState.value !is PaywallUiState.Content) return
        selected = index
        message = null
        publish()
        if (shown) reportOffer()
    }

    /** Buys the selected plan, with this paywall's placement, campaign and offer. [activity] is used only for this call. */
    public fun purchase(activity: Activity) {
        val plan = (mutableState.value as? PaywallUiState.Content)?.selectedPlan ?: return
        if (busy != null) return
        busy = Busy.PURCHASING
        message = null
        publish()
        jobs += scope.launch {
            val request = PurchaseRequest(
                plan.product.definition, placement, plan.basePlanId, plan.offerSelection, campaignId, paywall.name,
            )
            val result = billing.purchase(activity, request)
            busy = null
            when (result) {
                is PurchaseResult.Purchased -> {
                    stopClock()
                    mutableState.value = PaywallUiState.Purchased(result)
                    return@launch
                }
                is PurchaseResult.Pending -> message = UiMessage(R.string.adroit_billing_msg_pending)
                is PurchaseResult.Cancelled -> Unit
                is PurchaseResult.AlreadyOwned -> message = UiMessage(R.string.adroit_billing_msg_already_owned)
                is PurchaseResult.Failed -> message = messageFor(result.error)
            }
            publish()
        }
    }

    /** Restores purchases and reports the outcome as a message. */
    public fun restore() {
        if (busy != null || mutableState.value !is PaywallUiState.Content) return
        busy = Busy.RESTORING
        message = null
        publish()
        jobs += scope.launch {
            val result = billing.restore()
            busy = null
            message = when (result) {
                is RestoreResult.Restored -> UiMessage(R.string.adroit_billing_msg_restored)
                RestoreResult.NothingToRestore -> UiMessage(R.string.adroit_billing_msg_nothing_to_restore)
                is RestoreResult.Failed -> messageFor(result.error)
            }
            publish()
        }
    }

    /** Opens Google Play's subscription management, for the selected plan's product when it is a subscription. */
    public fun manage(activity: Activity) {
        val plan = (mutableState.value as? PaywallUiState.Content)?.selectedPlan ?: return
        val product = plan.product.definition.takeIf { plan.product.type == ProductType.SUBSCRIPTION }
        if (!billing.openManageSubscriptions(activity, product)) {
            message = UiMessage(R.string.adroit_billing_msg_manage_failed)
            publish()
        }
    }

    /** The close button: closes when it is visible, and always on a paywall that is loading, unavailable or purchased. */
    public fun onCloseClicked() {
        when (val current = mutableState.value) {
            is PaywallUiState.Content -> if (current.closeVisible && busy == null) close(PaywallCloseReason.CLOSE_BUTTON)
            PaywallUiState.Loading -> close(PaywallCloseReason.CLOSE_BUTTON)
            is PaywallUiState.Unavailable -> close(PaywallCloseReason.ERROR)
            is PaywallUiState.Purchased -> close(PaywallCloseReason.PURCHASED)
            is PaywallUiState.Closed -> Unit
        }
    }

    /**
     * The back gesture. Returns true when it is consumed and the paywall stays; false when the paywall closes (the
     * state becomes [PaywallUiState.Closed]).
     */
    public fun onBackPressed(): Boolean {
        when (val current = mutableState.value) {
            is PaywallUiState.Content -> {
                if (!current.backAllowed) return true
                close(PaywallCloseReason.BACK)
            }
            PaywallUiState.Loading -> close(PaywallCloseReason.BACK)
            is PaywallUiState.Unavailable -> close(PaywallCloseReason.ERROR)
            is PaywallUiState.Purchased -> close(PaywallCloseReason.PURCHASED)
            is PaywallUiState.Closed -> Unit
        }
        return false
    }

    /** Stops the controller's work. The UI calls it when the paywall is gone (a ViewModel's `onCleared`). */
    public fun dispose() {
        stopClock()
        jobs.forEach { it.cancel() }
    }

    /** The selection, the close delay left and the visible time, for a `SavedStateHandle`. */
    public fun saveState(): Map<String, Any> = buildMap {
        selected?.let { put(KEY_SELECTED, it) }
        put(KEY_REMAINING, remaining)
        put(KEY_CLOSE_VISIBLE, closeVisible)
        put(KEY_VISIBLE_MILLIS, currentVisibleMillis())
        put(KEY_SHOWN, shown || shownBefore)
    }

    /** Restores [saveState]'s values, before [onShown]; a restored paywall doesn't report its showing again. */
    public fun restore(state: Map<String, Any>) {
        (state[KEY_SELECTED] as? Int)?.let { index -> selected = model?.let { index.coerceIn(0, it.plans.lastIndex) } ?: index }
        (state[KEY_REMAINING] as? Int)?.let { remaining = it.coerceIn(0, close.delaySeconds.coerceAtLeast(0)) }
        (state[KEY_CLOSE_VISIBLE] as? Boolean)?.let { closeVisible = it && close.delaySeconds >= 0 }
        (state[KEY_VISIBLE_MILLIS] as? Long)?.let { visibleMillis = it }
        shownBefore = state[KEY_SHOWN] == true
        if (model != null) publish()
    }

    private fun close(reason: PaywallCloseReason) {
        val wasContent = mutableState.value is PaywallUiState.Content
        stopClock()
        val exit = if (wasContent && reason != PaywallCloseReason.PURCHASED) engine?.recordClosed(paywall, placement, reason) else null
        events.emit(BillingEvent.PaywallClosed(paywall.name, placement, campaignId, reason, currentVisibleMillis() / 1000, now()))
        mutableState.value = PaywallUiState.Closed(reason, exit)
        jobs.forEach { it.cancel() }
    }

    private fun unavailable(state: PaywallUiState.Unavailable) {
        stopClock()
        mutableState.value = state
    }

    /** Runs the visible-time clock and the close-button countdown only while the paywall is shown in the foreground. */
    private fun updateClock() {
        val running = shown && inForeground && mutableState.value.let { it is PaywallUiState.Content || it == PaywallUiState.Loading }
        if (!running) {
            stopClock()
            return
        }
        if (visibleSince == null) visibleSince = clock()
        if (ticker == null && !closeVisible && remaining > 0) {
            ticker = scope.launch {
                while (remaining > 0) {
                    delay(1_000)
                    remaining--
                    if (remaining > 0) publish()
                }
                closeVisible = true
                events.emit(BillingEvent.PaywallCloseButtonShown(paywall.name, placement, campaignId, close.delaySeconds.toLong(), now()))
                ticker = null
                publish()
            }
        }
    }

    private fun stopClock() {
        ticker?.cancel()
        ticker = null
        visibleSince?.let { visibleMillis += clock() - it }
        visibleSince = null
    }

    private fun currentVisibleMillis(): Long = visibleMillis + (visibleSince?.let { clock() - it } ?: 0)

    private fun publish() {
        val model = model ?: return
        if (mutableState.value.let { it is PaywallUiState.Purchased || it is PaywallUiState.Closed || it is PaywallUiState.Unavailable }) return
        val backAllowed = busy == null && when (close.back) {
            BackBehavior.ALWAYS -> true
            BackBehavior.AFTER_DELAY -> closeVisible
            BackBehavior.NEVER -> false
        }
        val countdown = remaining.takeIf { close.showCountdown && !closeVisible && close.delaySeconds > 0 }
        mutableState.value = PaywallUiState.Content(model, selected ?: model.selected, closeVisible, countdown, backAllowed, busy, message)
    }

    private fun reportOffer() {
        val plan = (mutableState.value as? PaywallUiState.Content)?.selectedPlan ?: return
        events.emit(
            BillingEvent.OfferShown(plan.product.productId, plan.basePlanId, plan.offerId, paywall.name, placement, campaignId, now()),
        )
    }

    private fun now() = System.currentTimeMillis()

    private fun messageFor(error: BillingError): UiMessage {
        val text = when (error) {
            is BillingError.NetworkError, is BillingError.ServiceDisconnected -> R.string.adroit_billing_msg_network
            is BillingError.Unavailable, is BillingError.FeatureNotSupported -> R.string.adroit_billing_msg_billing_unavailable
            is BillingError.PaymentDeclined -> R.string.adroit_billing_msg_declined
            is BillingError.ItemAlreadyOwned -> R.string.adroit_billing_msg_already_owned
            is BillingError.ProductNotFound, is BillingError.OfferNotFound, is BillingError.UserIneligible ->
                R.string.adroit_billing_msg_plan_unavailable
            is BillingError.PurchaseInProgress -> R.string.adroit_billing_msg_in_progress
            else -> R.string.adroit_billing_msg_error
        }
        return UiMessage(text, retry = error.isRetryable)
    }

    /** Creating a controller without `AdroitBilling`. */
    public companion object {
        /**
         * A controller over a fixed [model], for previews, UI tests and design reviews: it behaves like a real one
         * (close delay, back policy, selection, messages), buys through [billing], and records no show counts or
         * events. Apps use `AdroitBilling.paywalls.controller` instead.
         *
         * @param foreground whether the app is in the foreground; the close delay counts only while it is true.
         * @param clock elapsed milliseconds, for the visible time.
         */
        @JvmStatic
        @JvmOverloads
        public fun forModel(
            paywall: PaywallDefinition,
            model: PaywallModel,
            placement: String,
            scope: CoroutineScope,
            billing: BillingClientApi,
            foreground: Flow<Boolean> = flowOf(true),
            clock: () -> Long = SystemClock::elapsedRealtime,
        ): PaywallController {
            require(model.plans.isNotEmpty()) { "The model has no plans" }
            val events = EventBus(emptyList(), BillingLog(BillingLogLevel.NONE))
            val resolved = ResolvedPaywall(model.name, model.spec)
            return PaywallController(paywall, placement, scope, billing, null, events, resolved, foreground, clock, model)
        }

        private const val KEY_SELECTED = "adroit_paywall_selected"
        private const val KEY_REMAINING = "adroit_paywall_close_remaining"
        private const val KEY_CLOSE_VISIBLE = "adroit_paywall_close_visible"
        private const val KEY_VISIBLE_MILLIS = "adroit_paywall_visible_millis"
        private const val KEY_SHOWN = "adroit_paywall_shown"
    }
}
