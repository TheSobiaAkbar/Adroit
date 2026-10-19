package io.github.thesobiaakbar.adroit.billing.views

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContract
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.PaywallCloseReason
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.ShowDecision
import kotlinx.coroutines.CoroutineScope

/**
 * Shows Adroit Billing paywalls in View-based (XML) apps, without Compose, AppCompat, Material or Fragment:
 *
 * ```
 * AdroitPaywall.show(this, AppPaywalls.Onboarding, "onboarding")
 * ```
 *
 * Every template (`plans`, `single`, `dialog`, `bottom_sheet`) opens in [AdroitPaywallActivity], styled from Firebase,
 * and keeps its state across rotation and process death. To embed a paywall in a screen, use [PaywallView].
 */
public object AdroitPaywall {
    /**
     * Draws `style.headerImage` into the template's image view; the library has no image loader. For example with
     * Coil: `AdroitPaywall.imageLoader = { view, url -> view.load(url) }`. Null skips header images.
     */
    @JvmStatic
    @Volatile
    public var imageLoader: ((ImageView, String) -> Unit)? = null

    internal var definitions: (String) -> PaywallDefinition? = { AdroitBilling.paywalls.definition(it) }
    internal var controllers: (PaywallDefinition, String, CoroutineScope) -> PaywallController =
        { definition, placement, scope -> AdroitBilling.paywalls.controller(definition, placement, scope) }
    internal var shouldShow: (PaywallDefinition, String) -> ShowDecision =
        { definition, placement -> AdroitBilling.paywalls.shouldShow(definition, placement) }

    /**
     * Opens [definition] at [placement] when its show rules allow it (`AdroitBilling.paywalls.shouldShow`), and
     * returns the decision: on [ShowDecision.Skip] nothing opens. [force] skips the rules, for a button the user taps
     * ("Upgrade" in settings). [definition] must be in `BillingSettings.paywalls`. Main thread.
     */
    @JvmStatic
    @JvmOverloads
    public fun show(activity: Activity, definition: PaywallDefinition, placement: String, force: Boolean = false): ShowDecision {
        val decision = if (force) ShowDecision.Show else shouldShow(definition, placement)
        if (decision == ShowDecision.Show) activity.startActivity(intent(activity, definition, placement))
        return decision
    }

    /** The Intent that opens [definition] at [placement] in [AdroitPaywallActivity], without checking the show rules. */
    @JvmStatic
    public fun intent(context: Context, definition: PaywallDefinition, placement: String): Intent =
        Intent(context, AdroitPaywallActivity::class.java)
            .putExtra(AdroitPaywallActivity.EXTRA_PAYWALL, definition.name)
            .putExtra(AdroitPaywallActivity.EXTRA_PLACEMENT, placement)

    /**
     * For apps that want to know how the paywall closed. It doesn't check the show rules: call
     * `AdroitBilling.paywalls.shouldShow` first unless the user asked for the paywall.
     *
     * ```
     * val paywall = registerForActivityResult(AdroitPaywall.contract()) { reason ->
     *     if (reason == PaywallCloseReason.PURCHASED) unlock()
     * }
     * paywall.launch(PaywallLaunch(AppPaywalls.Onboarding, "onboarding"))
     * ```
     */
    @JvmStatic
    public fun contract(): ActivityResultContract<PaywallLaunch, PaywallCloseReason> = PaywallContract
}

/** What [AdroitPaywall.contract] opens: [definition] at [placement]. */
public data class PaywallLaunch(public val definition: PaywallDefinition, public val placement: String)

private object PaywallContract : ActivityResultContract<PaywallLaunch, PaywallCloseReason>() {
    override fun createIntent(context: Context, input: PaywallLaunch): Intent =
        AdroitPaywall.intent(context, input.definition, input.placement)

    // No result (the Activity was finished from outside) counts as the user leaving it: BACK.
    override fun parseResult(resultCode: Int, intent: Intent?): PaywallCloseReason =
        intent?.getStringExtra(AdroitPaywallActivity.EXTRA_REASON)
            ?.let { name -> PaywallCloseReason.entries.firstOrNull { it.name == name } }
            ?: PaywallCloseReason.BACK
}
