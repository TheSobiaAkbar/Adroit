package io.github.thesobiaakbar.adroit.billing

import androidx.annotation.StringRes

/**
 * What a paywall screen shows ([PaywallController.state]). Immutable; the Compose and XML paywalls render it the same
 * way.
 *
 * Stability: new states may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface PaywallUiState {
    /** The products are loading. The user can always close the paywall now. */
    public data object Loading : PaywallUiState

    /**
     * The paywall.
     *
     * @property model the plans, frozen when the paywall opened.
     * @property selectedIndex the selected plan in [PaywallModel.plans].
     * @property closeVisible whether the close button shows.
     * @property closeCountdownSeconds the seconds left until the close button appears, while `close.showCountdown`
     *   asks for a countdown; null otherwise.
     * @property backAllowed whether the back gesture closes the paywall now.
     * @property busy a purchase or restore in progress; null when idle.
     * @property message a message to show, for example after a failed purchase; null for none.
     */
    public data class Content(
        public val model: PaywallModel,
        public val selectedIndex: Int,
        public val closeVisible: Boolean,
        public val closeCountdownSeconds: Int?,
        public val backAllowed: Boolean,
        public val busy: Busy?,
        public val message: UiMessage?,
    ) : PaywallUiState {
        /** The selected plan. */
        public val selectedPlan: PaywallPlan
            get() = model.plans[selectedIndex]
    }

    /** The purchase succeeded. The UI closes ([PaywallController.onCloseClicked]) or stays, per `successAction`. */
    public data class Purchased(public val result: PurchaseResult.Purchased) : PaywallUiState

    /**
     * Nothing can be shown: [reason] says why the paywall is unavailable (disabled, entitled, no plans…), or [error]
     * why the products couldn't load. The UI shows a friendly error, and closing is **always** allowed, whatever the
     * configuration says.
     */
    public data class Unavailable(public val reason: SkipReason?, public val error: BillingError?) : PaywallUiState

    /** The paywall closed for [reason]; the UI dismisses it, then shows [exitPaywall] if it isn't null. */
    public data class Closed(public val reason: PaywallCloseReason, public val exitPaywall: PaywallDefinition?) : PaywallUiState
}

/** What the paywall is busy with. */
public enum class Busy {
    /** A purchase: the Play sheet is open. */
    PURCHASING,

    /** Restoring purchases. */
    RESTORING,
}

/**
 * A message for the user, as a string resource (an app can override or translate it).
 *
 * @property text the string resource.
 * @property retry whether retrying the last action may help.
 */
public data class UiMessage(@param:StringRes public val text: Int, public val retry: Boolean = false)
