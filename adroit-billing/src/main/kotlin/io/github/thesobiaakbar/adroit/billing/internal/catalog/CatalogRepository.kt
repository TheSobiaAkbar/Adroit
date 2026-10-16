package io.github.thesobiaakbar.adroit.billing.internal.catalog

import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductInfo
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.play.ConnectionManager
import io.github.thesobiaakbar.adroit.billing.internal.play.OfferKey
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import io.github.thesobiaakbar.adroit.billing.internal.play.playType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Loads every declared product from Play into [state], one query per Play product type. Prices live in memory only:
 * they depend on the user's locale and Play account, so each process asks Play again.
 *
 * Loads run one at a time; a request during a load shares it. While a load runs, and when it fails, the last
 * [CatalogState.Ready] stays; [CatalogState.Failed] only replaces [CatalogState.Loading].
 */
internal class CatalogRepository(
    private val connection: ConnectionManager,
    private val products: List<ProductDefinition>,
    private val state: MutableStateFlow<CatalogState>,
    private val scope: CoroutineScope,
    private val log: BillingLog,
    private val maxAgeMillis: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var loading: Deferred<Unit>? = null
    private var loadedAt: Long? = null

    /** The offer tokens of every loaded product, for launching a purchase (B06). Never leaves the library. */
    @Volatile
    var offerTokens: Map<ProductDefinition, Map<OfferKey, String>> = emptyMap()
        private set

    /** Loads now, or joins the load already running. */
    suspend fun load() {
        val job = synchronized(this) {
            loading?.takeIf { it.isActive } ?: scope.async { loadOnce() }.also { loading = it }
        }
        job.await()
    }

    /** Loads in the background when nothing was loaded yet or the catalog is older than [maxAgeMillis]. */
    fun loadIfStale() {
        val at = synchronized(this) { loadedAt }
        if (at == null || now() - at >= maxAgeMillis) scope.launch { load() }
    }

    private suspend fun loadOnce() {
        val infos = mutableMapOf<ProductDefinition, ProductInfo>()
        val tokens = mutableMapOf<ProductDefinition, Map<OfferKey, String>>()
        val missing = mutableSetOf<ProductDefinition>()
        for ((type, definitions) in products.groupBy { it.defaults.type.playType }) {
            val result = connection.call("queryProducts") { queryProducts(type, definitions) }
            val found = when (result) {
                is PlayResult.Ok -> result.value
                is PlayResult.Failed -> return failed(result.error)
                is PlayResult.Cancelled -> return failed(BillingError.Unknown(debugMessage = "Product query cancelled"))
            }
            val byId = definitions.associateBy { it.defaults.productId }
            found.unfetched.forEach { (id, reason) -> byId[id]?.let { missing(missing, it, reason) } }
            for (product in found.products) {
                val definition = product.info.definition
                if (product.info.plans.isEmpty() && product.info.oneTimeOptions.isEmpty()) {
                    missing(missing, definition, "Play returned no offer")
                } else {
                    infos[definition] = product.info
                    tokens[definition] = product.offerTokens
                }
            }
        }
        offerTokens = tokens
        synchronized(this) { loadedAt = now() }
        state.value = CatalogState.Ready(infos, missing)
        log.log(BillingLogLevel.INFO, "Catalog loaded: ${infos.size} products, ${missing.size} missing")
    }

    private fun missing(missing: MutableSet<ProductDefinition>, definition: ProductDefinition, reason: String) {
        missing += definition
        log.log(BillingLogLevel.WARN, "Product ${definition.name} (${definition.defaults.productId}) missing: $reason")
    }

    private fun failed(error: BillingError) {
        val kept = state.value is CatalogState.Ready
        log.log(
            BillingLogLevel.WARN,
            "Loading products failed: ${error.code} ${error.debugMessage.orEmpty()}" + if (kept) "; kept the last catalog" else "",
        )
        if (!kept) state.value = CatalogState.Failed(error)
    }
}
