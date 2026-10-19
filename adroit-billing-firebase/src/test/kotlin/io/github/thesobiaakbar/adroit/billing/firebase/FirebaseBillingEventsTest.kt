package io.github.thesobiaakbar.adroit.billing.firebase

import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirebaseBillingEventsTest {
    private val logged = mutableListOf<Pair<String, Map<String, Any>>>()
    private val attribution = Attribution("a-1", "home", "Onboarding", "spring", "premium_sub", "monthly", "trial", false)
    private val completed = BillingEvent.PurchaseCompleted(listOf("premium_sub"), attribution, 1L, 4_990_000L, "USD")

    private fun events(logRevenue: Boolean = false) =
        FirebaseBillingEvents({ name, params -> logged += name to params }, logRevenue)

    @Test
    fun `events are logged as their name and params, within Firebase limits`() {
        events().onEvent(completed)

        val (name, params) = logged.single()
        assertEquals("adroit_billing_purchase_completed", name)
        assertEquals(completed.toParams(), params)
        assertTrue(name.length <= 40 && name.matches(Regex("[a-zA-Z0-9_]+")))
        assertTrue(params.size <= 25)
        assertTrue(params.values.all { it !is String || it.length <= 100 })
    }

    @Test
    fun `revenue isn't logged by default`() {
        events().onEvent(completed)

        assertEquals(listOf("adroit_billing_purchase_completed"), logged.map { it.first })
    }

    @Test
    fun `with logRevenue a priced purchase also logs purchase with value and currency`() {
        events(logRevenue = true).onEvent(completed)
        events(logRevenue = true).onEvent(completed.copy(priceMicros = null, currency = null))

        val purchases = logged.filter { it.first == "purchase" }
        assertEquals(listOf(mapOf<String, Any>("value" to 4.99, "currency" to "USD")), purchases.map { it.second })
    }

    @Test
    fun `a Firebase failure never reaches billing`() {
        FirebaseBillingEvents({ _, _ -> error("boom") }, logRevenue = true).onEvent(completed)
    }
}
