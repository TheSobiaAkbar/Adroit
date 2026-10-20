package io.github.thesobiaakbar.adroit.docs.billing

import android.app.Application
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.HttpPurchaseVerifier

// docs/BILLING_BACKEND.md.

/** Stands for the app's own sign-in. */
object Auth {
    suspend fun freshIdToken(): String = "token"
}

class BackendApp : Application() {
    private val auth = Auth

    override fun onCreate() {
        super.onCreate()
        // region billing-http-verifier
        val backend = HttpPurchaseVerifier(
            url = "https://api.example.com/billing/verify",
            entitlementsUrl = "https://api.example.com/billing/entitlements",
            headers = { mapOf("Authorization" to "Bearer ${auth.freshIdToken()}") },
        )
        AdroitBilling.initialize(
            this,
            BillingSettings(AppProducts.entries, AppPaywalls.entries, verifier = backend, entitlementProvider = backend),
        )
        // endregion
    }
}
