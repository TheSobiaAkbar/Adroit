package io.github.thesobiaakbar.adroit.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Where the billing and paywall JSON values come from: the `AdroitBilling` key and one key per [PaywallDefinition]
 * name. Firebase Remote Config is one source (`adroit-billing-firebase`); [none] and [of] need nothing.
 *
 * A source never waits: [raw] returns the value in force now. Values only choose among Play's products and offers
 * and shape the paywall; they never grant entitlements or change prices.
 */
public fun interface BillingConfigSource {
    /** The JSON value of [key] now, or null when the source has none. Any thread; never blocks. */
    public fun raw(key: String): String?

    /** Emits each time the source's values change (for example after a Remote Config activation). */
    public val updates: Flow<Unit>
        get() = emptyFlow()

    /** Factories. */
    public companion object {
        /** No remote values: every paywall uses its in-code defaults. */
        @JvmStatic
        public fun none(): BillingConfigSource = BillingConfigSource { null }

        /** Fixed values by key, for tests and apps that build their own configuration. */
        @JvmStatic
        public fun of(values: Map<String, String>): BillingConfigSource {
            val copy = values.toMap()
            return BillingConfigSource { copy[it] }
        }
    }
}
