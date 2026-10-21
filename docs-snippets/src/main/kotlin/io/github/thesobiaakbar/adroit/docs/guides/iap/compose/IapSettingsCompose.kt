package io.github.thesobiaakbar.adroit.docs.guides.iap.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.thesobiaakbar.adroit.billing.compose.Paywall
import io.github.thesobiaakbar.adroit.docs.billing.PremiumRows
import io.github.thesobiaakbar.adroit.docs.guides.iap.AppPaywalls

// The Settings screen of docs/guides/IAP_COMPOSE.md and ADS_IAP_COMPOSE.md, step 7.

// region guide-settings-compose
@Composable
fun SettingsScreen() {
    var upgrade by rememberSaveable { mutableStateOf(false) }
    // "Upgrade", "Restore purchases" and "Manage subscription" (PremiumRows, below).
    PremiumRows(onUpgrade = { upgrade = true })
    // The user asked: show the paywall without its show rules.
    if (upgrade) Paywall(AppPaywalls.Settings, placement = "settings", onClose = { upgrade = false })
}
// endregion
