package io.github.thesobiaakbar.adroit.billing

/**
 * Log verbosity of the billing library, from most verbose to [NONE] (logging off). Purchase tokens and account IDs
 * are never logged at any level.
 *
 * Stability: the levels won't change before the next major version, so an exhaustive `when` is safe.
 */
public enum class BillingLogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE,
}

/** How a purchase is matched back to the attempt that started it (docs/architecture/06_BILLING_ARCHITECTURE.md §6). */
public enum class AttributionToken {
    /** By product and time, from a record kept on the device. The default. */
    LOCAL_ONLY,

    /**
     * Also by the attempt ID, sent to Play as the purchase's obfuscated profile ID and returned with the purchase,
     * so the match is exact and the backend sees it too. Play intends that field for a profile ID and may use it in
     * fraud checks; an account ID is then always sent too (the [AdroitBilling.setAccount] hash, or a random ID per
     * install).
     */
    PROFILE_ID,
}

/**
 * Everything `AdroitBilling.initialize` needs. Usually built once in `Application.onCreate`:
 *
 * ```
 * AdroitBilling.initialize(this, BillingSettings(products = AppProducts.entries, paywalls = AppPaywalls.entries))
 * ```
 *
 * @property products every product the app sells. Product names and Play IDs must be unique.
 * @property paywalls every paywall of the app. Names must be unique, and their plans must use [products].
 * @property config where the remote JSON values come from; [BillingConfigSource.none] uses only the in-code defaults.
 * @property overrides JSON values by key that win over [config], for values the app sets in code.
 * @property verifier checks purchases with the app's backend before granting; null trusts Play on the device.
 * @property entitlementProvider the app's backend as an extra source of entitlements; null for none.
 * @property events listeners of every [BillingEvent]; more can be added with [AdroitBilling.addEventListener].
 * @property adFreeEntitlements the entitlements that make the user ad-free ([EntitlementSnapshot.adFree]); null means
 *   any entitlement of [products]. The `AdroitBilling` Firebase parameter can change it.
 * @property offlineGraceHours how long a saved subscription is trusted without a successful Play query, 1 to 720.
 * @property catalogMaxAgeHours how old the products' prices may get before they are queried again when the app
 *   returns to the foreground, 1 or more.
 * @property attributionToken how purchases are matched to their attempts.
 * @property logLevel the log verbosity.
 */
public class BillingSettings @JvmOverloads constructor(
    public val products: List<ProductDefinition>,
    public val paywalls: List<PaywallDefinition> = emptyList(),
    public val config: BillingConfigSource = BillingConfigSource.none(),
    public val overrides: Map<String, String> = emptyMap(),
    public val verifier: PurchaseVerifier? = null,
    public val entitlementProvider: EntitlementProvider? = null,
    public val events: List<BillingEventListener> = emptyList(),
    public val adFreeEntitlements: Set<String>? = null,
    public val offlineGraceHours: Int = 72,
    public val catalogMaxAgeHours: Int = 6,
    public val attributionToken: AttributionToken = AttributionToken.LOCAL_ONLY,
    public val logLevel: BillingLogLevel = BillingLogLevel.WARN,
) {
    init {
        require(offlineGraceHours in 1..720) { "offlineGraceHours must be 1 to 720, was $offlineGraceHours" }
        require(catalogMaxAgeHours >= 1) { "catalogMaxAgeHours must be 1 or more, was $catalogMaxAgeHours" }
    }

    private fun fields(): List<Any?> = listOf(
        products, paywalls, config, overrides, verifier, entitlementProvider, events, adFreeEntitlements,
        offlineGraceHours, catalogMaxAgeHours, attributionToken, logLevel,
    )

    override fun equals(other: Any?): Boolean = other is BillingSettings && fields() == other.fields()

    override fun hashCode(): Int = fields().hashCode()

    override fun toString(): String =
        "BillingSettings(products=${products.map { it.name }}, paywalls=${paywalls.map { it.name }}, " +
            "overrides=${overrides.keys}, verifier=${verifier != null}, entitlementProvider=" +
            "${entitlementProvider != null}, events=${events.size}, adFreeEntitlements=$adFreeEntitlements, " +
            "offlineGraceHours=$offlineGraceHours, catalogMaxAgeHours=$catalogMaxAgeHours, " +
            "attributionToken=$attributionToken, logLevel=$logLevel)"
}
