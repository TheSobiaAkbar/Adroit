package io.github.thesobiaakbar.adroit.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingErrorTest {
    private val all: List<BillingError> = listOf(
        BillingError.NotInitialized,
        BillingError.Unavailable(),
        BillingError.FeatureNotSupported(),
        BillingError.ServiceDisconnected(),
        BillingError.NetworkError(),
        BillingError.ProductNotFound("p"),
        BillingError.OfferNotFound("p", "monthly", "trial"),
        BillingError.ItemAlreadyOwned(),
        BillingError.ItemNotOwned(),
        BillingError.DeveloperError(),
        BillingError.PaymentDeclined(),
        BillingError.UserIneligible(),
        BillingError.VerificationFailed("bad"),
        BillingError.AcknowledgeFailed(),
        BillingError.ConsumeFailed(),
        BillingError.BackendError(),
        BillingError.NoActivity,
        BillingError.PurchaseInProgress,
        BillingError.Unknown(),
    )

    /** Exhaustive without `else`: a new subtype doesn't compile until it is given an expectation here. */
    private fun expectedRetryable(error: BillingError): Boolean = when (error) {
        BillingError.NotInitialized, is BillingError.Unavailable, is BillingError.FeatureNotSupported,
        is BillingError.ProductNotFound, is BillingError.OfferNotFound, is BillingError.ItemAlreadyOwned,
        is BillingError.ItemNotOwned, is BillingError.DeveloperError, is BillingError.UserIneligible,
        is BillingError.VerificationFailed, BillingError.NoActivity, is BillingError.Unknown,
        -> false
        is BillingError.ServiceDisconnected, is BillingError.NetworkError, is BillingError.PaymentDeclined,
        is BillingError.AcknowledgeFailed, is BillingError.ConsumeFailed, is BillingError.BackendError,
        BillingError.PurchaseInProgress,
        -> true
    }

    @Test
    fun `every subtype is listed once`() {
        assertEquals(19, all.map { it::class }.toSet().size)
    }

    @Test
    fun `isRetryable matches the expectation`() {
        all.forEach { assertEquals(it.code, expectedRetryable(it), it.isRetryable) }
    }

    @Test
    fun `codes are unique snake_case`() {
        val codes = all.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        codes.forEach { assertTrue(it, it.matches(Regex("[a-z]+(_[a-z]+)*"))) }
    }

    @Test
    fun `Play details are carried as plain data`() {
        val error = BillingError.NetworkError(playResponseCode = 12, debugMessage = "offline")
        assertEquals(12, error.playResponseCode)
        assertEquals("offline", error.debugMessage)
    }
}
