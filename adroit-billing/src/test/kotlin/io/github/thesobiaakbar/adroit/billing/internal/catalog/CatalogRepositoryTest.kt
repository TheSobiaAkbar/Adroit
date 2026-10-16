package io.github.thesobiaakbar.adroit.billing.internal.catalog

import io.github.thesobiaakbar.adroit.billing.BillingConnectionState
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.OneTimeOption
import io.github.thesobiaakbar.adroit.billing.Phases
import io.github.thesobiaakbar.adroit.billing.Price
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductInfo
import io.github.thesobiaakbar.adroit.billing.SubscriptionPlan
import io.github.thesobiaakbar.adroit.billing.TestProducts
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.play.ConnectionManager
import io.github.thesobiaakbar.adroit.billing.internal.play.FakePlayBillingPort
import io.github.thesobiaakbar.adroit.billing.internal.play.OfferKey
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayProduct
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogRepositoryTest {
    private val port = FakePlayBillingPort()
    private val state = MutableStateFlow<CatalogState>(CatalogState.Loading)
    private val log = BillingLog(BillingLogLevel.NONE)

    private fun TestScope.repository(maxAgeMillis: Long = 6 * HOUR): CatalogRepository {
        val connection = ConnectionManager(
            port, backgroundScope, MutableStateFlow(BillingConnectionState.Connected), log,
            now = { testScheduler.currentTime },
        )
        return CatalogRepository(
            connection, TestProducts.entries, state, backgroundScope, log, maxAgeMillis, now = { testScheduler.currentTime },
        )
    }

    private fun sub(price: Long = 4_990_000) = PlayProduct(
        info(TestProducts.PremiumSub, plans = listOf(SubscriptionPlan("monthly", listOf(Phases.offer(Phases.base("P1M", price), offerId = null, basePlanId = "monthly"))))),
        mapOf(OfferKey("monthly", null) to "token-monthly"),
    )

    private val lifetime = PlayProduct(
        info(TestProducts.Lifetime, options = listOf(OneTimeOption("buy", null, emptyList(), Price("$59.99", 59_990_000, "USD")))),
        mapOf(OfferKey("buy", null) to "token-buy"),
    )

    private fun info(definition: ProductDefinition, plans: List<SubscriptionPlan> = emptyList(), options: List<OneTimeOption> = emptyList()) =
        ProductInfo(definition, definition.defaults.productId, definition.defaults.type, "t", "n", "d", options, plans)

    private val ready get() = state.value as CatalogState.Ready

    @Test
    fun `subscriptions and one-time products load in one query each`() = runTest {
        port.products["premium_sub"] = sub()
        port.products["premium_lifetime"] = lifetime
        val repository = repository()
        repository.load()
        assertEquals(2, port.queryProductsCalls)
        assertEquals(mapOf(TestProducts.PremiumSub to sub().info, TestProducts.Lifetime to lifetime.info), ready.products)
        assertEquals(setOf(TestProducts.Coins), ready.missing)
        assertEquals(mapOf(OfferKey("monthly", null) to "token-monthly"), repository.offerTokens[TestProducts.PremiumSub])
    }

    @Test
    fun `a product Play returns without any offer is missing`() = runTest {
        port.products["premium_sub"] = PlayProduct(info(TestProducts.PremiumSub), emptyMap())
        repository().load()
        assertEquals(setOf(TestProducts.PremiumSub, TestProducts.Lifetime, TestProducts.Coins), ready.missing)
        assertEquals(emptyMap<ProductDefinition, ProductInfo>(), ready.products)
    }

    @Test
    fun `concurrent loads share one query`() = runTest {
        port.productsGate = CompletableDeferred()
        val repository = repository()
        val loads = List(3) { backgroundScope.launch { repository.load() } }
        repository.loadIfStale()
        runCurrent()
        port.productsGate!!.complete(Unit)
        runCurrent()
        assertEquals(true, loads.all { it.isCompleted })
        assertEquals(2, port.queryProductsCalls) // one per product type
    }

    @Test
    fun `the last catalog stays while reloading and when reloading fails`() = runTest {
        port.products["premium_sub"] = sub()
        val repository = repository()
        repository.load()
        val first = ready

        port.products["premium_sub"] = sub(price = 5_990_000)
        port.productsGate = CompletableDeferred()
        backgroundScope.launch { repository.load() }
        runCurrent()
        assertSame(first, state.value)

        port.productsGate!!.complete(Unit)
        runCurrent()
        assertEquals(5_990_000L, ready.products.getValue(TestProducts.PremiumSub).plans.single().basePhase?.price?.micros)
        val second = ready

        port.productsGate = null
        port.productsResults += PlayResult.Failed(BillingError.NetworkError(12))
        repository.load()
        assertSame(second, state.value)
    }

    @Test
    fun `a failure with nothing loaded before is Failed`() = runTest {
        port.productsResults += PlayResult.Failed(BillingError.Unavailable(3))
        repository().load()
        assertEquals(CatalogState.Failed(BillingError.Unavailable(3)), state.value)
    }

    @Test
    fun `loadIfStale reloads only once the catalog is older than the max age`() = runTest {
        val repository = repository(maxAgeMillis = 6 * HOUR)
        repository.loadIfStale()
        runCurrent()
        assertEquals(2, port.queryProductsCalls)

        advanceTimeBy(6 * HOUR - 1)
        repository.loadIfStale()
        runCurrent()
        assertEquals(2, port.queryProductsCalls)

        advanceTimeBy(1)
        repository.loadIfStale()
        runCurrent()
        assertEquals(4, port.queryProductsCalls)
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
