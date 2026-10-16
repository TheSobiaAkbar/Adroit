package io.github.thesobiaakbar.adroit.billing.internal

import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.ProductType

/** Every problem in [settings] that would make products or paywalls misbehave; empty when it is valid. */
internal fun validate(settings: BillingSettings): List<String> = buildList {
    val products = settings.products
    if (products.isEmpty()) add("products is empty: declare at least one product")
    products.map { it.name }.duplicates().forEach { add("Product name '$it' is declared more than once") }
    products.map { it.defaults.productId }.duplicates().forEach { add("Play product ID '$it' is declared more than once") }
    (products.map { it.name } + settings.paywalls.map { it.name }).filterNot(NAME::matches).forEach {
        add("Invalid name '$it': letters, digits and underscores, up to 40")
    }

    val paywallNames = settings.paywalls.map { it.name }
    paywallNames.duplicates().forEach { add("Paywall name '$it' is declared more than once") }
    for (paywall in settings.paywalls) {
        val spec = paywall.defaults
        val where = "Paywall '${paywall.name}'"
        if (spec.plans.isEmpty()) add("$where has no plans")
        if (spec.plans.isNotEmpty() && spec.selected >= spec.plans.size) {
            add("$where selects plan ${spec.selected} but has ${spec.plans.size}")
        }
        spec.plans.forEachIndexed { index, plan ->
            if (plan.product !in products) add("$where plan $index uses '${plan.product.name}', which isn't in products")
            if (plan.basePlanId != null && plan.product.defaults.type != ProductType.SUBSCRIPTION) {
                add("$where plan $index sets a base plan on '${plan.product.name}', which isn't a subscription")
            }
        }
        spec.exitPaywall?.let {
            if (it == paywall.name) add("$where is its own exitPaywall")
            else if (it !in paywallNames) add("$where has exitPaywall '$it', which isn't in paywalls")
        }
    }
}

private val NAME = Regex("[A-Za-z0-9_]{1,40}")

private fun List<String>.duplicates(): Set<String> = groupingBy { it }.eachCount().filterValues { it > 1 }.keys
