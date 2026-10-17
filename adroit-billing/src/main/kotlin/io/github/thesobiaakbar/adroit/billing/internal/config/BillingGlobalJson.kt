package io.github.thesobiaakbar.adroit.billing.internal.config

import io.github.thesobiaakbar.adroit.billing.BillingSettings
import org.json.JSONObject

/**
 * The resolved `AdroitBilling` key (06 §5.1).
 *
 * @property enabled false: no paywall shows; purchases still work.
 * @property adFreeEntitlements null: any entitlement of the declared products.
 * @property productEntitlements each declared product's entitlements, by product name.
 * @property version `configVersion`, reported in `ConfigLoaded`.
 */
internal class GlobalConfig(
    val enabled: Boolean,
    val adFreeEntitlements: Set<String>?,
    val offlineGraceHours: Int,
    val productEntitlements: Map<String, Set<String>>,
    val version: String?,
) {
    companion object {
        fun defaults(settings: BillingSettings) = GlobalConfig(
            enabled = true,
            adFreeEntitlements = settings.adFreeEntitlements,
            offlineGraceHours = settings.offlineGraceHours,
            productEntitlements = settings.products.associate { it.name to it.defaults.entitlements },
            version = null,
        )
    }
}

internal object BillingGlobalJson {
    const val KEY = "AdroitBilling"

    /** [json] over [base], field by field. `products` can only remap products [base] already has. */
    fun merge(base: GlobalConfig, json: JSONObject, issue: (String) -> Unit): GlobalConfig {
        val f = JsonFields(json, "", issue)
        val products = base.productEntitlements.toMutableMap()
        f.obj("products")?.let { remap ->
            val o = json.getJSONObject("products")
            for (name in o.keys()) {
                if (name !in products) {
                    issue("\"products.$name\" isn't a declared product; ignored")
                    continue
                }
                remap.obj(name)?.strings("entitlements")?.let { products[name] = it.toSet() }
            }
        }
        return GlobalConfig(
            enabled = f.boolean("enabled") ?: base.enabled,
            adFreeEntitlements = f.strings("adFreeEntitlements")?.toSet() ?: base.adFreeEntitlements,
            offlineGraceHours = f.int("offlineGraceHours", 1..720) ?: base.offlineGraceHours,
            productEntitlements = products,
            version = f.version("configVersion") ?: base.version,
        )
    }
}
