package io.github.thesobiaakbar.adroit.billing.internal.catalog

import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.BillingConnectionState
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingEventListener
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.Price
import io.github.thesobiaakbar.adroit.billing.TestProducts
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import io.github.thesobiaakbar.adroit.billing.internal.play.CatalogFixtures
import io.github.thesobiaakbar.adroit.billing.internal.play.ConnectionManager
import io.github.thesobiaakbar.adroit.billing.internal.play.FakePlayBillingPort
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import io.github.thesobiaakbar.adroit.billing.internal.play.toPlayProduct
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CatalogAndConnectionEventsTest {
    private val port = FakePlayBillingPort()
    private val log = BillingLog(BillingLogLevel.NONE)
    private val events = mutableListOf<BillingEvent>()
    private val bus = EventBus(listOf(BillingEventListener { events += it }), log)

    private fun attribution(product: String, plan: String?, offer: String?, attempt: String? = "a-1") =
        Attribution(attempt, "home", null, null, product, plan, offer, recovered = attempt == null)

    @Test
    fun `connecting reports each attempt and its outcome`() = runTest {
        val error = BillingError.ServiceDisconnected(-1)
        port.connectResults += PlayResult.Failed(error)
        val state = MutableStateFlow<BillingConnectionState>(BillingConnectionState.Disconnected)
        ConnectionManager(port, backgroundScope, state, log, bus, now = { testScheduler.currentTime }).trigger()
        runCurrent()
        advanceTimeBy(1_001)
        assertEquals(
            listOf(
                BillingEvent.ConnectionStarted(0), BillingEvent.ConnectionFailed(error, 0),
                BillingEvent.ConnectionStarted(1_000), BillingEvent.ConnectionEstablished(1_000),
            ),
            events,
        )
    }

    @Test
    fun `a catalog load reports its count and missing products, a failure its error, and prices come from it`() = runTest {
        port.products["premium_sub"] = CatalogFixtures.productDetails(CatalogFixtures.subscription).toPlayProduct(TestProducts.PremiumSub)
        port.products["premium_lifetime"] = CatalogFixtures.productDetails(CatalogFixtures.lifetime).toPlayProduct(TestProducts.Lifetime)
        val state = MutableStateFlow<CatalogState>(CatalogState.Loading)
        val connection = ConnectionManager(port, backgroundScope, MutableStateFlow(BillingConnectionState.Connected), log)
        val catalog = CatalogRepository(connection, TestProducts.entries, state, backgroundScope, log, 1, { 0 }, bus)
        catalog.load()
        assertEquals(BillingEvent.CatalogLoaded(2, listOf("coins_100"), 0), events.single())

        val catalogState = state.value
        assertEquals(Price("Free", 0, "USD"), catalogState.priceOf(attribution("premium_sub", "monthly", "trial")))
        assertEquals(Price("$0.99", 990_000, "USD"), catalogState.priceOf(attribution("premium_sub", "yearly", "intro")))
        assertEquals(Price("$59.99", 59_990_000, "USD"), catalogState.priceOf(attribution("premium_lifetime", null, null)))
        assertNull(catalogState.priceOf(attribution("premium_sub", null, null)))
        assertNull(catalogState.priceOf(attribution("premium_sub", "monthly", null, attempt = null)))
        assertNull(CatalogState.Loading.priceOf(attribution("premium_sub", "monthly", null)))

        val error = BillingError.NetworkError()
        port.productsResults += PlayResult.Failed(error)
        catalog.load()
        assertEquals(BillingEvent.CatalogFailed(error, 0), events.last())
    }
}
