package io.github.thesobiaakbar.adroit.billing

/**
 * Demo mode ([BillingSettings.demo]): the paywalls and purchase flows run without Google Play, so the UI and the
 * Firebase configuration can be shown before the products exist in Play Console.
 *
 * When [enabled] **and** the app is debuggable, the library uses made-up products instead of Play. Every price reads
 * "(DEMO)", so it shows in the paywall disclosure. A purchase opens a dialog to choose its result: bought, pending
 * (it completes 15 seconds later) or cancelled. Demo purchases are kept on the device until consumed. No money moves
 * and nothing reaches Play.
 *
 * In an app that isn't debuggable, demo mode is ignored and an ERROR is logged, so it can never reach users.
 *
 * ```
 * BillingSettings(products = AppProducts.entries, paywalls = AppPaywalls.entries, demo = DemoBilling(BuildConfig.DEBUG))
 * ```
 */
public class DemoBilling @JvmOverloads constructor(public val enabled: Boolean = false) {
    override fun equals(other: Any?): Boolean = other is DemoBilling && enabled == other.enabled

    override fun hashCode(): Int = enabled.hashCode()

    override fun toString(): String = "DemoBilling(enabled=$enabled)"
}
