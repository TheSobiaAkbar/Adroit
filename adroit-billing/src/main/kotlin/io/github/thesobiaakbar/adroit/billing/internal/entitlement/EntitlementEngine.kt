package io.github.thesobiaakbar.adroit.billing.internal.entitlement

import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.EntitlementGrant
import io.github.thesobiaakbar.adroit.billing.EntitlementLossReason
import io.github.thesobiaakbar.adroit.billing.EntitlementSnapshot
import io.github.thesobiaakbar.adroit.billing.GrantSource
import io.github.thesobiaakbar.adroit.billing.GrantState
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import io.github.thesobiaakbar.adroit.billing.RestoreResult
import io.github.thesobiaakbar.adroit.billing.ServerLifecycleKind
import io.github.thesobiaakbar.adroit.billing.SnapshotStatus
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.attribution.AttemptStore
import io.github.thesobiaakbar.adroit.billing.internal.attribution.AttributionMatcher
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import io.github.thesobiaakbar.adroit.billing.internal.play.ConnectionManager
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayFeature
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayProductType
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchase
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchaseState
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import io.github.thesobiaakbar.adroit.billing.internal.purchase.PurchaseProcessor
import io.github.thesobiaakbar.adroit.billing.internal.purchase.TokenLedger
import io.github.thesobiaakbar.adroit.billing.internal.sha256Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Keeps [state], the answer to "what does this user own now" (06 §4, §5.1, §7).
 *
 * A sync queries Play's purchases, runs each through [processor], merges the [BillingSettings.entitlementProvider]'s
 * grants (the backend wins for a product both report) and saves the result in [cache]. Until the first sync of the
 * process the cache is published as [SnapshotStatus.Cached]. A failed sync changes nothing: it never adds a grant and
 * never removes one. Subscriptions confirmed longer than `offlineGraceHours` ago are dropped; other products stay.
 *
 * Grants are mapped to entitlements when published, so a new `AdroitBilling` remote value applies at once. The remote
 * value can remap only declared products; it can't add one.
 *
 * Each entitlement that leaves [EntitlementSnapshot.active] is reported as [BillingEvent.EntitlementLost]; a change in
 * the provider's grants between two syncs that both reached it, as [BillingEvent.ServerLifecycle].
 *
 * Runs on [scope]'s (main) thread.
 */
internal class EntitlementEngine(
    private val connection: ConnectionManager,
    private val settings: BillingSettings,
    private val processor: PurchaseProcessor,
    private val ledger: TokenLedger,
    private val attempts: AttemptStore,
    private val cache: EntitlementCache,
    private val state: MutableStateFlow<EntitlementSnapshot>,
    private val events: EventBus,
    private val scope: CoroutineScope,
    private val log: BillingLog,
    private val account: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val providerTimeoutMillis: Long = PROVIDER_TIMEOUT_MILLIS,
) {
    private val matcher = AttributionMatcher(attempts)
    private val mutex = Mutex()

    private var saved: SavedGrants? = cache.load()
    private var freshInProcess = false
    private var lastSyncAt: Long? = null
    private var expiry: Job? = null

    init {
        publish()
    }

    /** Queries Play and rebuilds the snapshot. Null on success, else why it failed (and nothing changed). */
    suspend fun sync(): BillingError? = mutex.withLock {
        lastSyncAt = now()
        val purchases = mutableListOf<PlayPurchase>()
        for (type in PlayProductType.entries) {
            val result = connection.call("queryPurchases") {
                val suspended = type == PlayProductType.SUBS &&
                    isFeatureSupported(PlayFeature.INCLUDE_SUSPENDED_SUBSCRIPTIONS) is PlayResult.Ok
                queryPurchases(type, suspended)
            }
            when (result) {
                is PlayResult.Ok -> purchases += result.value
                is PlayResult.Failed -> return@withLock failed(result.error)
                is PlayResult.Cancelled -> return@withLock failed(BillingError.Unknown(debugMessage = "queryPurchases cancelled"))
            }
        }
        val play = purchases.flatMap { grants(it) }
        val backend = backend()
        if (backend != null) lifecycle(saved?.grants.orEmpty().filter { it.source == GrantSource.BACKEND }, backend)
        val merged = if (backend == null) play else play.filter { p -> backend.none { it.productId == p.productId } } + backend
        saved = SavedGrants(merged, now()).also(cache::save)
        freshInProcess = true
        publish()
        log.log(BillingLogLevel.DEBUG, "Synced ${purchases.size} purchases; active ${state.value.active}")
        null
    }

    fun requestSync() {
        scope.launch { sync() }
    }

    /** The app came to the foreground: re-checks the grace period, and syncs unless a sync started within 30 s. */
    fun onForeground() {
        publish()
        val last = lastSyncAt
        if (last == null || now() - last >= FOREGROUND_DEBOUNCE_MILLIS) requestSync()
    }

    /** Called with each processed purchase: syncs, so the snapshot holds it when `purchase()` returns. */
    suspend fun afterPurchase(result: PurchaseResult) {
        val grant = (result as? PurchaseResult.Purchased)?.grant
        if (grant == null && result !is PurchaseResult.AlreadyOwned) return
        if (sync() == null || grant == null) return
        // Play confirmed this purchase just now but the query failed: show it until the next sync.
        mutex.withLock {
            val current = saved
            saved = SavedGrants(current?.grants.orEmpty().filter { it.productId != grant.productId } + grant, current?.atMillis ?: now())
            publish()
        }
    }

    suspend fun restore(): RestoreResult {
        events.emit(BillingEvent.RestoreInitiated(now()))
        val before = state.value.active
        sync()?.let { error ->
            log.log(BillingLogLevel.WARN, "Restore failed: ${error.code}")
            return RestoreResult.Failed(error)
        }
        val snapshot = state.value
        val newlyActive = snapshot.active - before
        events.emit(BillingEvent.RestoreCompleted(newlyActive, now()))
        return if (snapshot.grants.isEmpty()) RestoreResult.NothingToRestore else RestoreResult.Restored(newlyActive, snapshot)
    }

    /**
     * Remembers the hash of [accountId]. A switch from one account to another (signing out in between included)
     * forgets the saved entitlements and attempts, and syncs again.
     */
    fun setAccount(accountId: String?) {
        val hash = accountId?.let(::sha256Hex) ?: return
        scope.launch {
            val switched = mutex.withLock {
                val previous = cache.accountHash
                if (hash == previous) return@withLock false
                cache.accountHash = hash
                if (previous == null) return@withLock false
                log.log(BillingLogLevel.INFO, "Account switched; saved entitlements cleared")
                cache.clear()
                attempts.clear()
                saved = null
                freshInProcess = false
                publish(EntitlementLossReason.ACCOUNT_SWITCHED)
                true
            }
            if (switched) sync()
        }
    }

    /** One grant per declared product of [purchase]; none for consumables or undeclared products. */
    private suspend fun grants(purchase: PlayPurchase): List<EntitlementGrant> {
        // Only a purchase new to the ledger takes an open attempt; a known one keeps the attempt it matched, if any.
        val matched = if (ledger[purchase.purchaseToken] == null) matcher.match(purchase) else Attribution.unknown(purchase.products.first())
        val attribution = if (matched.attemptId != null) matched.copy(recovered = true) else matched
        val result = processor.process(purchase, attribution)
        val grantState = when {
            result is PurchaseResult.Purchased -> GrantState.ACTIVE
            result is PurchaseResult.Pending -> GrantState.PENDING
            purchase.isSuspended && purchase.state == PlayPurchaseState.PURCHASED -> GrantState.SUSPENDED
            else -> return emptyList()
        }
        return purchase.products.mapNotNull { id ->
            val spec = settings.products.firstOrNull { it.defaults.productId == id }?.defaults
            if (spec == null || spec.type == ProductType.CONSUMABLE) return@mapNotNull null
            EntitlementGrant(id, spec.type, spec.entitlements, grantState, GrantSource.CLIENT, purchase.purchaseTimeMillis)
        }
    }

    /** The provider's grants, or null without a provider or when it can't answer. */
    private suspend fun backend(): List<EntitlementGrant>? {
        val provider = settings.entitlementProvider ?: return null
        val grants = try {
            withTimeoutOrNull(providerTimeoutMillis) { provider.fetch(account()?.let(::sha256Hex)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.log(BillingLogLevel.WARN, "EntitlementProvider threw ${e::class.java.name}")
            null
        }
        if (grants == null) log.log(BillingLogLevel.INFO, "EntitlementProvider unavailable; using Play's purchases only")
        return grants
    }

    private fun failed(error: BillingError): BillingError {
        log.log(BillingLogLevel.WARN, "Sync failed (${error.code}); kept the current entitlements")
        publish()
        return error
    }

    /** Reports how each backend grant in [before] became the one in [after]. */
    private fun lifecycle(before: List<EntitlementGrant>, after: List<EntitlementGrant>) {
        val now = now()
        for (old in before) {
            val new = after.firstOrNull { it.productId == old.productId }
            val kind = when {
                new == null -> ServerLifecycleKind.ENDED
                new.state == old.state -> null
                new.state == GrantState.GRACE_FROM_BACKEND -> ServerLifecycleKind.GRACE_PERIOD
                new.state == GrantState.SUSPENDED -> ServerLifecycleKind.ON_HOLD
                new.state == GrantState.ACTIVE && old.state in RECOVERABLE -> ServerLifecycleKind.RECOVERED
                else -> null
            }
            if (kind != null) events.emit(BillingEvent.ServerLifecycle(kind, old.productId, now))
        }
    }

    /**
     * Publishes [saved] as it stands now: remapped, with the grace period applied. An entitlement lost for no reason
     * seen here is reported with [lossReason].
     */
    fun publish(lossReason: EntitlementLossReason = EntitlementLossReason.NOT_OWNED) {
        val before = state.value
        val expired = publishSaved()
        val after = state.value
        for (entitlement in before.active - after.active) {
            val providers = before.grants.filter { it.grantsAccess && entitlement in it.entitlements }.map { it.productId }
            val reason = when {
                after.grants.any { it.needsPaymentFix && entitlement in it.entitlements } -> EntitlementLossReason.PAYMENT_ISSUE
                after.grants.any { it.grantsAccess && it.productId in providers } -> EntitlementLossReason.CONFIG_CHANGED
                expired && saved?.grants.orEmpty().any { it.productId in providers } -> EntitlementLossReason.OFFLINE_GRACE_ENDED
                else -> lossReason
            }
            events.emit(BillingEvent.EntitlementLost(entitlement, reason, now()))
        }
    }

    /** Sets [state] from [saved]; true when the offline grace period has run out. */
    private fun publishSaved(): Boolean {
        val remote = remote()
        val mapping = settings.products.associate { def ->
            def.defaults.productId to (remote.entitlements[def.name] ?: def.defaults.entitlements)
        }
        val adFree = remote.adFree ?: settings.adFreeEntitlements ?: mapping.values.flatten().toSet()
        val current = saved
        if (current == null) {
            state.value = EntitlementSnapshot(emptyList(), SnapshotStatus.Unknown, adFree)
            return false
        }
        val graceMillis = (remote.graceHours ?: settings.offlineGraceHours) * HOUR_MILLIS
        val expiresAt = current.atMillis + graceMillis
        val expired = now() > expiresAt
        val grants = current.grants
            .filter { !expired || it.type != ProductType.SUBSCRIPTION }
            .map { if (it.source == GrantSource.CLIENT) it.copy(entitlements = mapping[it.productId] ?: it.entitlements) else it }
        val status = when {
            expired && grants.isEmpty() -> SnapshotStatus.Unknown
            freshInProcess && !expired -> SnapshotStatus.Fresh(current.atMillis)
            else -> SnapshotStatus.Cached(current.atMillis)
        }
        state.value = EntitlementSnapshot(grants, status, adFree)
        expiry?.cancel()
        if (!expired && grants.any { it.type == ProductType.SUBSCRIPTION }) {
            expiry = scope.launch {
                delay(expiresAt - now() + 1)
                publish()
            }
        }
        return expired
    }

    private class Remote(val adFree: Set<String>?, val graceHours: Int?, val entitlements: Map<String, Set<String>>)

    // ponytail: reads only what entitlements need from the `AdroitBilling` key; B11 brings the full schema.
    private fun remote(): Remote {
        val json = settings.overrides[REMOTE_KEY] ?: settings.config.raw(REMOTE_KEY) ?: return Remote(null, null, emptyMap())
        return runCatching {
            val o = JSONObject(json)
            fun strings(key: String, from: JSONObject = o) = from.optJSONArray(key)?.let { a -> List(a.length()) { a.getString(it) }.toSet() }
            val products = o.optJSONObject("products")
            Remote(
                strings("adFreeEntitlements"),
                o.optInt("offlineGraceHours", 0).takeIf { it in 1..720 },
                settings.products.mapNotNull { def -> products?.optJSONObject(def.name)?.let { strings("entitlements", it) }?.let { def.name to it } }.toMap(),
            )
        }.getOrElse {
            log.log(BillingLogLevel.WARN, "Ignored the invalid $REMOTE_KEY remote value")
            Remote(null, null, emptyMap())
        }
    }

    private companion object {
        const val REMOTE_KEY = "AdroitBilling"
        const val HOUR_MILLIS = 3_600_000L
        const val FOREGROUND_DEBOUNCE_MILLIS = 30_000L
        const val PROVIDER_TIMEOUT_MILLIS = 15_000L
        val RECOVERABLE = setOf(GrantState.GRACE_FROM_BACKEND, GrantState.SUSPENDED)
    }
}
