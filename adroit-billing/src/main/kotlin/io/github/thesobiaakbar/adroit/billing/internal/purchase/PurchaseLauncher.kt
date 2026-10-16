package io.github.thesobiaakbar.adroit.billing.internal.purchase

import android.app.Activity
import android.os.Looper
import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.AttributionToken
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.OfferInfo
import io.github.thesobiaakbar.adroit.billing.OfferSelection
import io.github.thesobiaakbar.adroit.billing.OneTimeOption
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.PurchaseRequest
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import io.github.thesobiaakbar.adroit.billing.PurchaseUpdate
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.attribution.Attempt
import io.github.thesobiaakbar.adroit.billing.internal.attribution.AttemptStore
import io.github.thesobiaakbar.adroit.billing.internal.attribution.AttributionMatcher
import io.github.thesobiaakbar.adroit.billing.internal.catalog.CatalogRepository
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import io.github.thesobiaakbar.adroit.billing.internal.play.ConnectionManager
import io.github.thesobiaakbar.adroit.billing.internal.play.LaunchParams
import io.github.thesobiaakbar.adroit.billing.internal.play.OfferKey
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchase
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchasesUpdate
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import io.github.thesobiaakbar.adroit.billing.internal.sha256Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs `purchase()` (06 §4, §6): picks the offer, records the attempt, opens Play's sheet and waits for Play's
 * answer. Only one purchase runs at a time, from the first call until Play answers.
 *
 * Every purchase Play reports goes through [handle]: it is matched to its attempt, processed by [processor], and its
 * outcome returned to the waiting `purchase()` call, or published on [updates] when nobody waits (the caller's scope
 * was cancelled, a pending payment completed, a purchase from outside the app).
 */
internal class PurchaseLauncher(
    private val connection: ConnectionManager,
    private val catalog: CatalogRepository,
    private val catalogState: StateFlow<CatalogState>,
    private val settings: BillingSettings,
    private val store: AttemptStore,
    private val updates: MutableSharedFlow<PurchaseUpdate>,
    private val events: EventBus,
    private val log: BillingLog,
    private val account: () -> String?,
    private val debuggable: Boolean,
    private val processor: PurchaseProcessor = PurchaseProcessor.ReportOnly,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val matcher = AttributionMatcher(store)
    private val busy = AtomicBoolean(false)

    /** The attempt whose sheet is open. Touched on the main thread only. */
    private var current: InFlight? = null

    private class InFlight(val attribution: Attribution) {
        val result = CompletableDeferred<PurchaseResult>()
        var outcome: Pair<PurchaseResult, List<String>>? = null
        var callerGone = false
    }

    private sealed interface Choice {
        class Offer(val basePlanId: String?, val offerId: String?, val token: String) : Choice

        class None(val error: BillingError) : Choice
    }

    suspend fun purchase(activity: Activity, request: PurchaseRequest): PurchaseResult {
        if (debuggable) check(Looper.myLooper() == Looper.getMainLooper()) { "purchase() must be called on the main thread" }
        if (activity.isFinishing || activity.isDestroyed) return fail(BillingError.NoActivity)
        if (!busy.compareAndSet(false, true)) return fail(BillingError.PurchaseInProgress)
        var attempt: InFlight? = null
        var launched = false
        try {
            val offer = when (val choice = choose(request)) {
                is Choice.None -> return fail(choice.error)
                is Choice.Offer -> choice
            }
            val attribution = Attribution(
                UUID.randomUUID().toString(), request.placement, request.paywallId, request.campaignId,
                request.product.defaults.productId, offer.basePlanId, offer.offerId, recovered = false,
            )
            if (!store.save(Attempt(attribution, now()))) {
                log.log(BillingLogLevel.WARN, "Couldn't save the purchase attempt; its placement may be lost")
            }
            attempt = InFlight(attribution).also { current = it }
            events.emit(BillingEvent.PurchaseInitiated(attribution, now()))
            log.log(BillingLogLevel.INFO, "Purchase of ${attribution.productId} from ${attribution.placement}")
            val params = LaunchParams(attribution.productId, offer.token, accountId(), profileId(attribution))
            when (val launch = connection.call("launchBillingFlow") { launch(activity, params) }) {
                is PlayResult.Ok -> launched = true
                is PlayResult.Cancelled -> return abandon(attempt, PurchaseResult.Cancelled(attribution))
                is PlayResult.Failed -> return abandon(attempt, outcome(launch.error, attribution))
            }
            return attempt.result.await()
        } catch (e: CancellationException) {
            // The caller left (a rotation with lifecycleScope); the outcome goes to purchaseUpdates instead.
            attempt?.run {
                callerGone = true
                outcome?.let { (result, products) -> publish(result, products) }
            }
            throw e
        } finally {
            if (!launched) {
                if (current === attempt) current = null
                busy.set(false)
            }
        }
    }

    /** Handles every purchase update from Play, for as long as the library runs. */
    suspend fun handle(playUpdates: Flow<PlayPurchasesUpdate>) {
        playUpdates.collect { update ->
            when (update) {
                is PlayPurchasesUpdate.Purchased -> update.purchases.forEach { onPurchase(it) }
                is PlayPurchasesUpdate.Cancelled -> current?.let { abandon(it, PurchaseResult.Cancelled(it.attribution)) }
                    ?: log.log(BillingLogLevel.WARN, "Play reported a cancelled purchase while none was running")
                is PlayPurchasesUpdate.Failed -> current?.let { abandon(it, outcome(update.error, it.attribution)) }
                    ?: log.log(BillingLogLevel.WARN, "Play reported a failed purchase while none was running: ${update.error.code}")
            }
        }
    }

    private suspend fun onPurchase(purchase: PlayPurchase) {
        val matched = matcher.match(purchase)
        val live = current?.takeIf { matched.attemptId != null && it.attribution.attemptId == matched.attemptId }
        val attribution = if (live == null && matched.attemptId != null) matched.copy(recovered = true) else matched
        val result = processor.process(purchase, attribution)
        if (live != null) finish(live, result, purchase.products) else publish(result, purchase.products)
    }

    private suspend fun choose(request: PurchaseRequest): Choice {
        val definition = request.product
        val productId = definition.defaults.productId
        val state = withTimeoutOrNull(PRODUCT_TIMEOUT_MILLIS) { catalogState.first { it !is CatalogState.Loading } }
        if (state is CatalogState.Failed) return Choice.None(state.error)
        val info = (catalogState.value as? CatalogState.Ready)?.products?.get(definition)
            ?: return Choice.None(BillingError.ProductNotFound(productId))
        val selection = request.offer
        val requested = when (selection) {
            is OfferSelection.Id -> selection.offerId
            is OfferSelection.Tags -> "with tags ${selection.tags}"
            else -> null
        }
        val subscription = info.type == ProductType.SUBSCRIPTION
        val (planOrOption, offerId) = if (subscription) {
            val wanted = request.basePlanId ?: paywallBasePlan(request)
            val plan = (if (wanted == null) info.plans.firstOrNull() else info.plans.firstOrNull { it.basePlanId == wanted })
                ?: return Choice.None(BillingError.OfferNotFound(productId, wanted, null))
            val offer = plan.offers.pick(selection, OfferInfo::offerId, OfferInfo::offerTags)
                ?: return Choice.None(BillingError.OfferNotFound(productId, plan.basePlanId, requested))
            plan.basePlanId to offer.offerId
        } else {
            val option = info.oneTimeOptions.pick(selection, OneTimeOption::offerId, OneTimeOption::offerTags)
                ?: return Choice.None(BillingError.OfferNotFound(productId, null, requested))
            option.purchaseOptionId to option.offerId
        }
        val token = catalog.offerTokens[definition]?.get(OfferKey(planOrOption, offerId))
            ?: return Choice.None(BillingError.OfferNotFound(productId, planOrOption, offerId))
        return Choice.Offer(planOrOption.takeIf { subscription }, offerId, token)
    }

    /** The requested offer only; never another one in its place. */
    private fun <T> List<T>.pick(selection: OfferSelection, id: (T) -> String?, tags: (T) -> List<String>): T? =
        when (selection) {
            // ponytail: Auto is the base price until B12 brings the paywall's offer rules.
            OfferSelection.Auto, OfferSelection.BaseOnly -> firstOrNull { id(it) == null }
            is OfferSelection.Id -> firstOrNull { id(it) == selection.offerId }
            is OfferSelection.Tags -> firstOrNull { id(it) != null && tags(it).containsAll(selection.tags) }
        }

    private fun paywallBasePlan(request: PurchaseRequest): String? =
        settings.paywalls.firstOrNull { it.name == request.paywallId }
            ?.defaults?.plans?.firstOrNull { it.product == request.product }?.basePlanId

    /** A hash of the app's account ID, never the ID; with [AttributionToken.PROFILE_ID], Play needs one regardless. */
    private fun accountId(): String? = account()?.let(::sha256Hex)
        ?: if (settings.attributionToken == AttributionToken.PROFILE_ID) store.installId() else null

    private fun profileId(attribution: Attribution): String? =
        attribution.attemptId!!.replace("-", "").takeIf { settings.attributionToken == AttributionToken.PROFILE_ID }

    private fun outcome(error: BillingError, attribution: Attribution): PurchaseResult =
        if (error is BillingError.ItemAlreadyOwned) PurchaseResult.AlreadyOwned(attribution)
        else PurchaseResult.Failed(error, attribution)

    /** Ends an attempt that produced no purchase, so no later purchase is matched to it. */
    private fun abandon(attempt: InFlight, result: PurchaseResult): PurchaseResult {
        store.remove(attempt.attribution.attemptId!!)
        return finish(attempt, result, listOf(attempt.attribution.productId))
    }

    private fun finish(attempt: InFlight, result: PurchaseResult, products: List<String>): PurchaseResult {
        if (current === attempt) current = null
        busy.set(false)
        when (result) {
            is PurchaseResult.Cancelled -> events.emit(BillingEvent.PurchaseCancelled(result.attribution, now()))
            is PurchaseResult.Failed -> events.emit(BillingEvent.PurchaseFailed(result.error, result.attribution, now()))
            else -> Unit
        }
        log.log(BillingLogLevel.INFO, "Purchase of ${attempt.attribution.productId}: ${result::class.java.simpleName}")
        attempt.outcome = result to products
        attempt.result.complete(result)
        if (attempt.callerGone) publish(result, products)
        return result
    }

    private fun fail(error: BillingError): PurchaseResult {
        events.emit(BillingEvent.PurchaseFailed(error, attribution = null, now()))
        log.log(BillingLogLevel.WARN, "Purchase failed: ${error.code} ${error.debugMessage.orEmpty()}")
        return PurchaseResult.Failed(error, attribution = null)
    }

    private fun publish(result: PurchaseResult, products: List<String>) {
        val update = when (result) {
            is PurchaseResult.Purchased -> PurchaseUpdate.Completed(products, result.attribution)
            is PurchaseResult.Pending -> PurchaseUpdate.Pending(products, result.attribution)
            is PurchaseResult.Failed -> PurchaseUpdate.Failed(result.error, result.attribution)
            is PurchaseResult.Cancelled, is PurchaseResult.AlreadyOwned -> return
        }
        updates.tryEmit(update)
    }

    private companion object {
        const val PRODUCT_TIMEOUT_MILLIS = 5_000L
    }
}
