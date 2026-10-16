package io.github.thesobiaakbar.adroit.billing.internal.purchase

import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.ConsumableGrant
import io.github.thesobiaakbar.adroit.billing.EntitlementGrant
import io.github.thesobiaakbar.adroit.billing.GrantSource
import io.github.thesobiaakbar.adroit.billing.GrantState
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import io.github.thesobiaakbar.adroit.billing.PurchaseToVerify
import io.github.thesobiaakbar.adroit.billing.PurchaseUpdate
import io.github.thesobiaakbar.adroit.billing.VerificationResult
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import io.github.thesobiaakbar.adroit.billing.internal.play.ConnectionManager
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayBillingPort
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchase
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchaseState
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import io.github.thesobiaakbar.adroit.billing.internal.sha256Hex
import io.github.thesobiaakbar.adroit.billing.internal.tokenHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns each purchase Play reports into exactly one outcome (06 §4, §7): verifies it, grants it, then acknowledges or
 * consumes it once. [ledger] remembers each token's progress across restarts, so a purchase seen again (a sync, a
 * restart) continues where it stopped instead of being granted twice.
 *
 * Purchases of the same token are processed one at a time.
 */
internal class PurchaseProcessor(
    private val connection: ConnectionManager,
    private val settings: BillingSettings,
    private val ledger: TokenLedger,
    private val updates: MutableSharedFlow<PurchaseUpdate>,
    private val consumables: MutableStateFlow<List<ConsumableGrant>>,
    private val events: EventBus,
    private val log: BillingLog,
    private val packageName: String,
    private val account: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val verifierTimeoutMillis: Long = VERIFIER_TIMEOUT_MILLIS,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** The outcome to report, or null when there is nothing to report (a suspended or unspecified purchase). */
    suspend fun process(purchase: PlayPurchase, attribution: Attribution): PurchaseResult? = locked(purchase) {
        val hash = tokenHash(purchase.purchaseToken)
        when {
            purchase.state == PlayPurchaseState.UNSPECIFIED -> null
                .also { log.log(BillingLogLevel.WARN, "Ignored purchase $hash in an unspecified state") }
            purchase.state == PlayPurchaseState.PENDING -> PurchaseResult.Pending(attribution)
                .also { events.emit(BillingEvent.PurchasePending(purchase.products, attribution, now())) }
            purchase.isSuspended -> null.also {
                log.log(BillingLogLevel.INFO, "Purchase $hash of ${purchase.products} is suspended; nothing granted")
                purchase.products.forEach { updates.tryEmit(PurchaseUpdate.Suspended(it)) }
            }
            else -> purchased(purchase, attribution)
        }
    }

    private suspend fun purchased(purchase: PlayPurchase, attribution: Attribution): PurchaseResult {
        val token = purchase.purchaseToken
        val hash = tokenHash(token)
        val definitions = purchase.products.mapNotNull { id -> settings.products.firstOrNull { it.defaults.productId == id } }
        if (definitions.isEmpty()) {
            log.log(BillingLogLevel.WARN, "Purchase $hash of undeclared products ${purchase.products}; nothing granted")
            return PurchaseResult.Failed(BillingError.ProductNotFound(purchase.products.first()), attribution)
        }
        val type = definitions.first().defaults.type
        var state = ledger[token]
        var acknowledgedByBackend = false
        when (state) {
            TokenState.REJECTED ->
                return PurchaseResult.Failed(BillingError.VerificationFailed("Rejected earlier by the verifier"), attribution)
            null -> {
                when (val verdict = verify(purchase, type, attribution)) {
                    is VerificationResult.Verified -> acknowledgedByBackend = verdict.acknowledgedByBackend
                    is VerificationResult.Rejected -> {
                        write(token, TokenState.REJECTED)
                        log.log(BillingLogLevel.WARN, "Purchase $hash rejected by the verifier: ${verdict.reason}")
                        return PurchaseResult.Failed(BillingError.VerificationFailed(verdict.reason), attribution)
                    }
                    is VerificationResult.Unreachable -> {
                        log.log(BillingLogLevel.WARN, "Purchase $hash not verified (${verdict.reason}); retried on the next sync")
                        return PurchaseResult.Failed(BillingError.BackendError(verdict.reason), attribution)
                    }
                }
                // Recorded before acknowledging: a crash in between acknowledges on the next sync, without a second grant.
                write(token, TokenState.GRANTED)
                state = TokenState.GRANTED
                events.emit(BillingEvent.PurchaseCompleted(purchase.products, attribution, now()))
                if (type != ProductType.CONSUMABLE) definitions.forEach {
                    events.emit(BillingEvent.EntitlementActivated(it.defaults.productId, it.defaults.entitlements, attribution, now()))
                }
                log.log(BillingLogLevel.INFO, "Purchase $hash of ${purchase.products} granted")
            }
            else -> Unit
        }
        if (type == ProductType.CONSUMABLE) {
            when (state) {
                TokenState.CONFIRMED -> consume(purchase, attribution)
                TokenState.CONSUMED -> Unit
                else -> deliver(purchase, definitions.first(), attribution)
            }
            return PurchaseResult.Purchased(grant = null, attribution)
        }
        when {
            acknowledgedByBackend -> write(token, TokenState.ACKNOWLEDGED)
            !purchase.isAcknowledged && state != TokenState.ACKNOWLEDGED -> acknowledge(purchase, attribution)
        }
        val definition = definitions.first().defaults
        val grant = EntitlementGrant(
            definition.productId, type, definition.entitlements, GrantState.ACTIVE, GrantSource.CLIENT, purchase.purchaseTimeMillis,
        )
        return PurchaseResult.Purchased(grant, attribution)
    }

    /** Verified(false) without a verifier; a verifier that times out or throws counts as unreachable. */
    private suspend fun verify(purchase: PlayPurchase, type: ProductType, attribution: Attribution): VerificationResult {
        val verifier = settings.verifier ?: return VerificationResult.Verified()
        val request = PurchaseToVerify(
            purchase.products, type, purchase.purchaseToken, packageName, attribution, account()?.let(::sha256Hex),
        )
        val verdict = try {
            withTimeoutOrNull(verifierTimeoutMillis) { verifier.verify(request) }
                ?: VerificationResult.Unreachable("Verifier timed out after $verifierTimeoutMillis ms")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VerificationResult.Unreachable("Verifier threw ${e::class.java.name}")
        }
        if (verdict is VerificationResult.Verified) events.emit(BillingEvent.PurchaseVerified(attribution, now()))
        return verdict
    }

    private suspend fun acknowledge(purchase: PlayPurchase, attribution: Attribution) {
        val error = retrying("acknowledgePurchase") { acknowledge(purchase.purchaseToken) }
        if (error == null) {
            write(purchase.purchaseToken, TokenState.ACKNOWLEDGED)
            return
        }
        val failure = BillingError.AcknowledgeFailed(error.playResponseCode, error.debugMessage)
        events.emit(BillingEvent.AcknowledgeFailed(failure, attribution, now()))
        log.log(BillingLogLevel.WARN, "Acknowledging ${tokenHash(purchase.purchaseToken)} failed (${error.code}); retried on the next sync")
    }

    /**
     * Hands a consumable to the app on [consumables] until it confirms; consumed only then. Unconfirmed, it is
     * delivered again on the next sync or after a restart.
     */
    private fun deliver(purchase: PlayPurchase, definition: ProductDefinition, attribution: Attribution) {
        // ponytail: one grant per purchase, for its first product; Play sells consumables one product per purchase.
        val productId = definition.defaults.productId
        val orderIdHash = sha256Hex(purchase.orderId ?: purchase.purchaseToken)
        lateinit var grant: ConsumableGrant
        grant = ConsumableGrant(productId, purchase.quantity, attribution, orderIdHash) { confirm(purchase, attribution, grant) }
        consumables.update { if (grant in it) it else it + grant }
    }

    private suspend fun confirm(purchase: PlayPurchase, attribution: Attribution, grant: ConsumableGrant) {
        locked(purchase) {
            consumables.update { it - grant }
            if (ledger[purchase.purchaseToken] == TokenState.CONSUMED) return@locked
            write(purchase.purchaseToken, TokenState.CONFIRMED)
            consume(purchase, attribution)
        }
    }

    private suspend fun consume(purchase: PlayPurchase, attribution: Attribution) {
        val error = retrying("consumePurchase") { consume(purchase.purchaseToken) }
        // ITEM_NOT_OWNED: consumed already, elsewhere or by an earlier try whose answer was lost.
        if (error == null || error is BillingError.ItemNotOwned) {
            write(purchase.purchaseToken, TokenState.CONSUMED)
            return
        }
        val failure = BillingError.ConsumeFailed(error.playResponseCode, error.debugMessage)
        events.emit(BillingEvent.ConsumeFailed(failure, attribution, now()))
        log.log(BillingLogLevel.WARN, "Consuming ${tokenHash(purchase.purchaseToken)} failed (${error.code}); retried on the next sync")
    }

    /** Up to [TRIES] calls, backing off between them. Null on success, else the last error. */
    private suspend fun retrying(name: String, block: suspend PlayBillingPort.() -> PlayResult<Unit>): BillingError? {
        var error: BillingError? = null
        for (attempt in 0 until TRIES) {
            if (attempt > 0) delay(BACKOFF_MILLIS shl (attempt - 1))
            error = when (val result = connection.call(name, block)) {
                is PlayResult.Ok -> return null
                is PlayResult.Failed -> result.error
                is PlayResult.Cancelled -> BillingError.Unknown(debugMessage = "$name cancelled")
            }
            if (error is BillingError.ItemNotOwned) break
        }
        return error
    }

    private fun write(token: String, state: TokenState) {
        if (!ledger.set(token, state)) log.log(BillingLogLevel.WARN, "Couldn't save the state of ${tokenHash(token)} ($state)")
    }

    private suspend fun <T> locked(purchase: PlayPurchase, block: suspend () -> T): T =
        locks.getOrPut(tokenHash(purchase.purchaseToken)) { Mutex() }.withLock { block() }

    private companion object {
        const val VERIFIER_TIMEOUT_MILLIS = 15_000L
        const val TRIES = 3
        const val BACKOFF_MILLIS = 1_000L
    }
}
