package io.github.thesobiaakbar.adroit.billing.internal.play

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.OnPurchasesUpdatedSubResponseCode
import com.android.billingclient.api.Purchase
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.internal.tokenHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayMappersTest {
    private val none = OnPurchasesUpdatedSubResponseCode.NO_APPLICABLE_SUB_RESPONSE_CODE

    /** Every `BillingResponseCode` by its name, so a code Play adds fails this test until it is mapped. */
    private val expected: Map<String, PlayResult<Unit>> = mapOf(
        "OK" to PlayResult.Ok(Unit),
        "USER_CANCELED" to PlayResult.Cancelled,
        "SERVICE_UNAVAILABLE" to PlayResult.Failed(BillingError.NetworkError(2, "m")),
        "NETWORK_ERROR" to PlayResult.Failed(BillingError.NetworkError(12, "m")),
        "SERVICE_DISCONNECTED" to PlayResult.Failed(BillingError.ServiceDisconnected(-1, "m")),
        "SERVICE_TIMEOUT" to PlayResult.Failed(BillingError.ServiceDisconnected(-3, "m")),
        "BILLING_UNAVAILABLE" to PlayResult.Failed(BillingError.Unavailable(3, "m")),
        "FEATURE_NOT_SUPPORTED" to PlayResult.Failed(BillingError.FeatureNotSupported(-2, "m")),
        "ITEM_UNAVAILABLE" to PlayResult.Failed(BillingError.Unknown(4, "m")),
        "ITEM_ALREADY_OWNED" to PlayResult.Failed(BillingError.ItemAlreadyOwned(7, "m")),
        "ITEM_NOT_OWNED" to PlayResult.Failed(BillingError.ItemNotOwned(8, "m")),
        "DEVELOPER_ERROR" to PlayResult.Failed(BillingError.DeveloperError(5, "m")),
        "ERROR" to PlayResult.Failed(BillingError.Unknown(6, "m")),
    )

    @Test
    fun `every Play response code is mapped`() {
        val codes = BillingResponseCode::class.java.fields.associate { it.name to it.getInt(null) }
        assertEquals(expected.keys, codes.keys)
        codes.forEach { (name, code) -> assertEquals(name, expected.getValue(name), playOutcome(code, none, "m")) }
    }

    @Test
    fun `an unavailable item names the product when known`() {
        assertEquals(
            PlayResult.Failed(BillingError.ProductNotFound("premium_sub")),
            playOutcome(BillingResponseCode.ITEM_UNAVAILABLE, none, null, productId = "premium_sub"),
        )
    }

    @Test
    fun `sub-response codes win over the response code`() {
        assertEquals(
            PlayResult.Failed(BillingError.PaymentDeclined(6, null)),
            playOutcome(BillingResponseCode.ERROR, OnPurchasesUpdatedSubResponseCode.PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS, ""),
        )
        assertEquals(
            PlayResult.Failed(BillingError.UserIneligible(5, null)),
            playOutcome(BillingResponseCode.DEVELOPER_ERROR, OnPurchasesUpdatedSubResponseCode.USER_INELIGIBLE, null),
        )
    }

    @Test
    fun `an unknown code is Unknown`() {
        assertEquals(PlayResult.Failed(BillingError.Unknown(99, null)), playOutcome(99, none, null))
    }

    @Test
    fun `a purchase maps without showing its token`() {
        val json = """{"orderId":"GPA.1","packageName":"app","productIds":["premium_sub"],"purchaseTime":5,""" +
            """"purchaseState":0,"purchaseToken":"secret-token","quantity":1,"acknowledged":true,"autoRenewing":true}"""
        val purchase = Purchase(json, "sig").toPlayPurchase()
        assertEquals(listOf("premium_sub"), purchase.products)
        assertEquals("secret-token", purchase.purchaseToken)
        assertEquals(PlayPurchaseState.PURCHASED, purchase.state)
        assertEquals(true, purchase.isAcknowledged)
        assertFalse(purchase.toString().contains("secret-token"))
        assertEquals(true, purchase.toString().contains(tokenHash("secret-token")))
        assertEquals(12, tokenHash("secret-token").length)
    }
}
