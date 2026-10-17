package io.github.thesobiaakbar.adroit.billing

import android.content.Context
import androidx.annotation.RawRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONException
import org.json.JSONObject

/**
 * Where the billing and paywall JSON values come from: the `AdroitBilling` key and one key per [PaywallDefinition]
 * name. Firebase Remote Config is one source (`adroit-billing-firebase`); [none], [of] and [local] need nothing.
 * Values over 64 KB, or that aren't JSON objects, are ignored with a `ConfigInvalid` event.
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

        /**
         * Values from a raw resource holding one JSON object whose top-level keys are the parameter names, for
         * example `{"AdroitBilling": {...}, "Onboarding": {...}}`. Read once, here.
         *
         * @throws IllegalArgumentException when the resource isn't a JSON object.
         */
        @JvmStatic
        public fun local(context: Context, @RawRes res: Int): BillingConfigSource {
            val text = context.resources.openRawResource(res).bufferedReader().use { it.readText() }
            return localSource(text, "resource $res")
        }
    }
}

/** [text], one JSON object of parameter values, as a source; [what] names it in the error. */
internal fun localSource(text: String, what: String): BillingConfigSource {
    val json = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw IllegalArgumentException("Billing config $what isn't a JSON object: ${e.message}", e)
    }
    return BillingConfigSource.of(json.keys().asSequence().associateWith { json.get(it).toString() })
}
