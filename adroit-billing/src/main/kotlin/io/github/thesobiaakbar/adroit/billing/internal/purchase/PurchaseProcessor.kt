package io.github.thesobiaakbar.adroit.billing.internal.purchase

import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchase
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchaseState

/** Turns one Play purchase into its outcome: verifying, granting, acknowledging or consuming it (B07). */
internal fun interface PurchaseProcessor {
    suspend fun process(purchase: PlayPurchase, attribution: Attribution): PurchaseResult

    companion object {
        /** Until B07: reports Play's state and grants, acknowledges and consumes nothing. */
        val ReportOnly = PurchaseProcessor { purchase, attribution ->
            when (purchase.state) {
                PlayPurchaseState.PURCHASED -> PurchaseResult.Purchased(grant = null, attribution)
                PlayPurchaseState.PENDING -> PurchaseResult.Pending(attribution)
                PlayPurchaseState.UNSPECIFIED ->
                    PurchaseResult.Failed(BillingError.Unknown(debugMessage = "Purchase in an unspecified state"), attribution)
            }
        }
    }
}
