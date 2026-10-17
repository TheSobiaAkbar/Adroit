package io.github.thesobiaakbar.adroit.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingEventTest {
    private val attribution = Attribution("a-1", "home", "Onboarding", "spring", "premium_sub", "monthly", "trial", false)
    private val error = BillingError.NetworkError(playResponseCode = 12, debugMessage = "raw message")

    /** One of every event, in declaration order. */
    private val all: List<BillingEvent> = listOf(
        BillingEvent.ConnectionStarted(1),
        BillingEvent.ConnectionEstablished(1),
        BillingEvent.ConnectionFailed(error, 1),
        BillingEvent.CatalogLoaded(2, listOf("gone_sub"), 1),
        BillingEvent.CatalogFailed(error, 1),
        BillingEvent.PaywallShown("Onboarding", "home", "spring", 1),
        BillingEvent.PaywallClosed("Onboarding", "home", null, PaywallCloseReason.CLOSE_BUTTON, 7, 1),
        BillingEvent.PaywallCloseButtonShown("Onboarding", "home", null, 3, 1),
        BillingEvent.PaywallSuppressed("Onboarding", "home", null, "frequency_cap", 1),
        BillingEvent.OfferShown("premium_sub", "monthly", "trial", "Onboarding", "home", null, 1),
        BillingEvent.OfferUnavailable("premium_sub", "monthly", "trial", null, "Onboarding", "home", 1),
        BillingEvent.PurchaseInitiated(attribution, 1),
        BillingEvent.PurchaseCancelled(attribution, 1),
        BillingEvent.PurchaseFailed(error, null, 1),
        BillingEvent.PurchasePending(listOf("premium_sub"), attribution, 1),
        BillingEvent.PurchaseVerified(attribution, 1),
        BillingEvent.PurchaseCompleted(listOf("premium_sub"), attribution, 1, 4_990_000, "EUR"),
        BillingEvent.EntitlementActivated("premium_sub", setOf("premium", "ad_free"), attribution, 1),
        BillingEvent.EntitlementLost("premium", EntitlementLossReason.PAYMENT_ISSUE, 1),
        BillingEvent.RestoreInitiated(1),
        BillingEvent.RestoreCompleted(setOf("premium"), 1),
        BillingEvent.AcknowledgeFailed(BillingError.AcknowledgeFailed(6, null), attribution, 1),
        BillingEvent.ConsumeFailed(BillingError.ConsumeFailed(6, null), attribution, 1),
        BillingEvent.ConfigLoaded("7", 1),
        BillingEvent.ConfigInvalid("AdroitBilling", "not json", 1),
        BillingEvent.HardPaywall("Onboarding", 1),
        BillingEvent.ServerLifecycle(ServerLifecycleKind.GRACE_PERIOD, "premium_sub", 1),
    )

    private val attributionParams = mapOf(
        "placement" to "home", "paywall_id" to "Onboarding", "campaign_id" to "spring", "product_id" to "premium_sub",
        "base_plan_id" to "monthly", "offer_id" to "trial", "attempt_id" to "a-1", "recovered" to "false",
    )

    private val errorParams = mapOf("error_code" to "network_error", "play_response_code" to 12L)

    @Test
    fun `the samples cover every event`() {
        val declared = BillingEvent::class.java.declaredClasses.filter { BillingEvent::class.java.isAssignableFrom(it) }.toSet()
        assertEquals(declared, all.map { it.javaClass }.toSet())
    }

    @Test
    fun `names are unique, prefixed and within Firebase's limits`() {
        val names = all.map { it.name }
        assertEquals(names.size, names.toSet().size)
        names.forEach {
            assertTrue(it, it.startsWith("adroit_billing_") && it.length <= 40 && it.matches(Regex("[a-z][a-z0-9_]*")))
        }
        assertEquals("adroit_billing_purchase_completed", all[16].name)
    }

    @Test
    fun `params use short snake_case keys and only String or Long values`() {
        for (event in all) {
            for ((key, value) in event.toParams()) {
                assertTrue("$key in ${event.name}", key.length <= 40 && key.matches(Regex("[a-z][a-z0-9_]*")))
                assertTrue("$key in ${event.name}", value is String && value.length <= 100 || value is Long)
            }
        }
    }

    @Test
    fun `every event's params`() {
        val expected: List<Map<String, Any>> = listOf(
            emptyMap(),
            emptyMap(),
            errorParams,
            mapOf("count" to 2L, "missing_count" to 1L, "missing" to "gone_sub"),
            errorParams,
            mapOf("paywall_id" to "Onboarding", "placement" to "home", "campaign_id" to "spring"),
            mapOf("paywall_id" to "Onboarding", "placement" to "home", "reason" to "close_button", "seconds_visible" to 7L),
            mapOf("paywall_id" to "Onboarding", "placement" to "home", "after_seconds" to 3L),
            mapOf("paywall_id" to "Onboarding", "placement" to "home", "reason" to "frequency_cap"),
            mapOf("product_id" to "premium_sub", "base_plan_id" to "monthly", "offer_id" to "trial", "paywall_id" to "Onboarding", "placement" to "home"),
            mapOf("product_id" to "premium_sub", "base_plan_id" to "monthly", "requested_offer" to "trial", "paywall_id" to "Onboarding", "placement" to "home"),
            attributionParams,
            attributionParams,
            errorParams,
            attributionParams + ("product_ids" to "premium_sub"),
            attributionParams,
            attributionParams + mapOf("product_ids" to "premium_sub", "price_micros" to 4_990_000L, "currency" to "EUR"),
            attributionParams + ("entitlements" to "ad_free,premium"),
            mapOf("entitlement" to "premium", "reason" to "payment_issue"),
            emptyMap(),
            mapOf("restored_count" to 1L, "restored" to "premium"),
            attributionParams + mapOf("error_code" to "acknowledge_failed", "play_response_code" to 6L),
            attributionParams + mapOf("error_code" to "consume_failed", "play_response_code" to 6L),
            mapOf("version" to "7"),
            mapOf("key" to "AdroitBilling", "reason" to "not json"),
            mapOf("paywall_id" to "Onboarding"),
            mapOf("kind" to "grace_period", "product_id" to "premium_sub", "source" to "backend"),
        )
        all.zip(expected).forEach { (event, params) -> assertEquals(event.name, params, event.toParams()) }
    }

    @Test
    fun `long values are cut to 100 characters`() {
        val long = "x".repeat(150)
        val params = BillingEvent.ConfigInvalid(long, long, 1).toParams()
        assertEquals("x".repeat(100), params["key"])
        assertEquals(100, (params["reason"] as String).length)
        val many = BillingEvent.CatalogLoaded(0, List(40) { "product_$it" }, 1).toParams()
        assertEquals(100, (many["missing"] as String).length)
    }

    @Test
    fun `only PurchaseCompleted carries a price, never a formatted one or a debug message`() {
        for (event in all) {
            val params = event.toParams()
            val priced = params.keys.any { "price" in it || it == "currency" }
            assertEquals(event.name, event is BillingEvent.PurchaseCompleted, priced)
            assertFalse(params.values.any { it == "raw message" || it.toString().contains("€") })
        }
    }

    @Test
    fun `no event, reason or kind claims a renewal, expiry or cancellation`() {
        val words = Regex("renew|expire|cancel")
        val texts = all.filterNot { it is BillingEvent.PurchaseCancelled }.map { it.name } +
            EntitlementLossReason.entries.map { it.name.lowercase() } + ServerLifecycleKind.entries.map { it.name.lowercase() }
        texts.forEach { assertFalse(it, words.containsMatchIn(it)) }
    }
}
