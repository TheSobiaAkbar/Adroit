package io.github.thesobiaakbar.adroit.billing.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Default values of the Adroit Billing paywall composables. */
public object PaywallDefaults {
    /** The close button's touch target: 48dp, the accessibility minimum. */
    public val CloseButtonSize: Dp = 48.dp

    /** The padding around a paywall's content. */
    public val ContentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp)

    /** The widest a dialog or bottom-sheet paywall gets. */
    public val MaxWidth: Dp = 560.dp
}
