package io.github.thesobiaakbar.adroit.consumersmoke

import androidx.compose.runtime.Composable
import io.github.thesobiaakbar.adroit.billing.compose.Paywall

/** Compiles the Compose paywall call against the published adroit-billing-compose. */
@Composable
internal fun SmokePaywall(onClose: () -> Unit) {
    Paywall(SmokePaywalls.Upgrade, placement = "smoke", onClose = { onClose() })
}
