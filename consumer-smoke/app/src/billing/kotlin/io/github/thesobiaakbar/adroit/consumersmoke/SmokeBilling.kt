package io.github.thesobiaakbar.adroit.consumersmoke

import android.app.Application
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.DemoBilling
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.PaywallSpec
import io.github.thesobiaakbar.adroit.billing.PlanSpec
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductSpec

internal enum class SmokeProducts(override val defaults: ProductSpec) : ProductDefinition {
    Premium(ProductSpec.subscription("premium_sub", entitlements = setOf("premium"))),
}

internal enum class SmokePaywalls(override val defaults: PaywallSpec) : PaywallDefinition {
    Upgrade(PaywallSpec(plans = listOf(PlanSpec(SmokeProducts.Premium, basePlanId = "monthly")))),
}

/** The billing host (`-Psmoke.billing=true`, the default): demo products, so no Play Console app is needed. */
internal object SmokeBilling {
    fun initialize(application: Application) {
        AdroitBilling.initialize(
            application,
            BillingSettings(products = SmokeProducts.entries, paywalls = SmokePaywalls.entries, demo = DemoBilling(true)),
        )
    }
}
