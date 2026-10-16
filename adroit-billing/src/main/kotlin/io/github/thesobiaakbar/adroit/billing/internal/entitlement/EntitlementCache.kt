package io.github.thesobiaakbar.adroit.billing.internal.entitlement

import android.annotation.SuppressLint
import android.content.Context
import io.github.thesobiaakbar.adroit.billing.EntitlementGrant
import io.github.thesobiaakbar.adroit.billing.GrantSource
import io.github.thesobiaakbar.adroit.billing.GrantState
import io.github.thesobiaakbar.adroit.billing.ProductType
import org.json.JSONArray
import org.json.JSONObject

/** The grants of the last successful sync, confirmed at [atMillis]. */
internal data class SavedGrants(val grants: List<EntitlementGrant>, val atMillis: Long)

/**
 * The last fresh entitlements in SharedPreferences `adroit_billing_entitlements`, read at start-up so the snapshot is
 * known before Play answers. Holds product IDs, entitlements, states and times: no purchase token, no account ID
 * (only the hash of the last one set, to notice a switch).
 */
@SuppressLint("UseKtx")
internal class EntitlementCache(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Null when nothing was saved or the saved value is unreadable. */
    fun load(): SavedGrants? = prefs.getString(GRANTS, null)?.let { json ->
        runCatching {
            val o = JSONObject(json)
            val grants = o.getJSONArray("grants")
            SavedGrants(List(grants.length()) { grants.getJSONObject(it).toGrant() }, o.getLong("at"))
        }.getOrNull()
    }

    fun save(saved: SavedGrants) {
        val grants = JSONArray().apply { saved.grants.forEach { put(it.toJson()) } }
        prefs.edit().putString(GRANTS, JSONObject().put("at", saved.atMillis).put("grants", grants).toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(GRANTS).apply()
    }

    /** The hash of the last non-null account set with `setAccount`. */
    var accountHash: String?
        get() = prefs.getString(ACCOUNT, null)
        set(value) {
            prefs.edit().putString(ACCOUNT, value).apply()
        }

    private fun EntitlementGrant.toJson() = JSONObject()
        .put("productId", productId)
        .put("type", type.name)
        .put("entitlements", JSONArray(entitlements.toList()))
        .put("state", state.name)
        .put("source", source.name)
        .put("purchaseTime", purchaseTimeMillis)

    private fun JSONObject.toGrant(): EntitlementGrant {
        val entitlements = getJSONArray("entitlements")
        return EntitlementGrant(
            getString("productId"),
            ProductType.valueOf(getString("type")),
            List(entitlements.length()) { entitlements.getString(it) }.toSet(),
            GrantState.valueOf(getString("state")),
            GrantSource.valueOf(getString("source")),
            getLong("purchaseTime"),
        )
    }

    companion object {
        const val NAME = "adroit_billing_entitlements"
        private const val GRANTS = "grants"
        private const val ACCOUNT = "account_hash"
    }
}
