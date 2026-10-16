package io.github.thesobiaakbar.adroit.billing

/**
 * One product of the app on Google Play, with its settings: usually an enum entry.
 *
 * ```
 * enum class AppProducts(override val defaults: ProductSpec) : ProductDefinition {
 *     PremiumSub(ProductSpec.subscription("premium_sub", entitlements = setOf("premium"))),
 *     Lifetime(ProductSpec.nonConsumable("premium_lifetime", entitlements = setOf("premium"))),
 *     Coins100(ProductSpec.consumable("coins_100")),
 * }
 * ```
 *
 * [name] is the product's key in paywall JSON (`"product": "PremiumSub"`) and in the `AdroitBilling` Firebase
 * parameter. An enum supplies it: the entry's name. Register every entry with `BillingSettings(products = …)`.
 */
public interface ProductDefinition {
    /** The product's key: letters, digits and underscores, up to 40. */
    public val name: String

    /** The product's Play ID, type and entitlements. */
    public val defaults: ProductSpec
}

/** The kind of a Play product. Stability: the values won't change before the next major version. */
public enum class ProductType {
    /** An auto-renewing or prepaid subscription. Grants its entitlements while Play reports it active. */
    SUBSCRIPTION,

    /** A one-time product bought once and owned for good, for example lifetime premium. */
    NON_CONSUMABLE,

    /** A one-time product that can be bought again, for example coins. It grants no entitlement. */
    CONSUMABLE,
}

/**
 * A product's settings, as written in its [ProductDefinition]. Create one with [subscription], [nonConsumable] or
 * [consumable]. Immutable.
 *
 * @property productId the product ID created in Play Console.
 * @property type the kind of product.
 * @property entitlements what owning the product unlocks, for example `"premium"` or `"ad_free"`. Always empty for a
 *   [ProductType.CONSUMABLE]. The `AdroitBilling` Firebase parameter can change this set (never the product ID).
 */
public class ProductSpec private constructor(
    public val productId: String,
    public val type: ProductType,
    public val entitlements: Set<String>,
) {
    init {
        require(PRODUCT_ID.matches(productId)) {
            "Invalid Play product ID '$productId': start with a lowercase letter or digit, then lowercase letters, " +
                "digits, '_' or '.', up to 139 characters"
        }
        entitlements.forEach {
            require(ENTITLEMENT.matches(it)) { "Invalid entitlement '$it': letters, digits, '_' and '.', up to 40" }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ProductSpec && productId == other.productId && type == other.type && entitlements == other.entitlements

    override fun hashCode(): Int = (productId.hashCode() * 31 + type.hashCode()) * 31 + entitlements.hashCode()

    override fun toString(): String = "ProductSpec(productId=$productId, type=$type, entitlements=$entitlements)"

    /** Factories, one per [ProductType]. */
    public companion object {
        private val PRODUCT_ID = Regex("[a-z0-9][a-z0-9._]{0,138}")
        private val ENTITLEMENT = Regex("[A-Za-z0-9_.]{1,40}")

        /** A subscription with Play ID [productId] that unlocks [entitlements] while active. */
        @JvmStatic
        public fun subscription(productId: String, entitlements: Set<String>): ProductSpec =
            ProductSpec(productId, ProductType.SUBSCRIPTION, entitlements.toSet())

        /** A one-time product with Play ID [productId] that unlocks [entitlements] for good. */
        @JvmStatic
        public fun nonConsumable(productId: String, entitlements: Set<String>): ProductSpec =
            ProductSpec(productId, ProductType.NON_CONSUMABLE, entitlements.toSet())

        /** A one-time product with Play ID [productId] that can be bought again; the app keeps the balance. */
        @JvmStatic
        public fun consumable(productId: String): ProductSpec =
            ProductSpec(productId, ProductType.CONSUMABLE, emptySet())
    }
}
