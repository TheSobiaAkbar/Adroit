package io.github.thesobiaakbar.adroit.billing.internal.play

import com.android.billingclient.api.ProductDetails

/** Play `ProductDetails` as Play sends them, for the mapping tests (needs Robolectric for `org.json`). */
object CatalogFixtures {
    /** Play's constructor from its JSON is package-private. */
    fun productDetails(json: String): ProductDetails =
        ProductDetails::class.java.getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance(json)

    private fun phase(price: String, micros: Long, period: String, cycles: Int, recurrence: Int) =
        """{"formattedPrice":"$price","priceAmountMicros":$micros,"priceCurrencyCode":"USD",""" +
            """"billingPeriod":"$period","billingCycleCount":$cycles,"recurrenceMode":$recurrence}"""

    private fun offer(plan: String, offerId: String?, tags: List<String>, vararg phases: String) =
        """{"basePlanId":"$plan",""" + (offerId?.let { """"offerId":"$it",""" } ?: "") +
            """"offerIdToken":"token-$plan-${offerId ?: "base"}","offerTags":${tags.joinToString(",", "[", "]") { "\"$it\"" }},""" +
            """"pricingPhases":[${phases.joinToString(",")}]}"""

    /** Weekly (base only), monthly with a 1-week free trial, yearly with a 3-month intro price. */
    val subscription = """{"productId":"premium_sub","type":"subs","title":"Premium (App)","name":"Premium",""" +
        """"description":"All features","subscriptionOfferDetails":[""" +
        offer("weekly", null, emptyList(), phase("$1.99", 1_990_000, "P1W", 0, 1)) + "," +
        offer("monthly", null, emptyList(), phase("$4.99", 4_990_000, "P1M", 0, 1)) + "," +
        offer("monthly", "trial", listOf("trial"), phase("Free", 0, "P1W", 1, 2), phase("$4.99", 4_990_000, "P1M", 0, 1)) + "," +
        offer("yearly", null, emptyList(), phase("$39.99", 39_990_000, "P1Y", 0, 1)) + "," +
        offer("yearly", "intro", listOf("intro", "discount"), phase("$0.99", 990_000, "P1M", 3, 2), phase("$39.99", 39_990_000, "P1Y", 0, 1)) +
        "]}"

    /** A non-consumable with two purchase options, one with an offer. */
    val lifetime = """{"productId":"premium_lifetime","type":"inapp","title":"Lifetime (App)","name":"Lifetime",""" +
        """"description":"Forever","oneTimePurchaseOfferDetailsList":[""" +
        """{"purchaseOptionId":"buy","offerIdToken":"token-buy","offerTags":[],"formattedPrice":"$59.99",""" +
        """"priceAmountMicros":59990000,"priceCurrencyCode":"USD"},""" +
        """{"purchaseOptionId":"buy","offerId":"launch","offerIdToken":"token-buy-launch","offerTags":["launch"],""" +
        """"formattedPrice":"$29.99","priceAmountMicros":29990000,"priceCurrencyCode":"USD"}]}"""

    /** A consumable in Play's older single-offer shape. */
    val coins = """{"productId":"coins_100","type":"inapp","title":"100 coins (App)","name":"100 coins",""" +
        """"description":"Coins","oneTimePurchaseOfferDetails":{"offerIdToken":"token-coins","formattedPrice":"$0.99",""" +
        """"priceAmountMicros":990000,"priceCurrencyCode":"USD"}}"""
}
