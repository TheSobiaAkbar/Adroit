package io.github.thesobiaakbar.adroit.docs.billing

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.PaywallUiState
import io.github.thesobiaakbar.adroit.billing.PriceText
import io.github.thesobiaakbar.adroit.billing.RestoreResult
import io.github.thesobiaakbar.adroit.billing.ShowDecision
import io.github.thesobiaakbar.adroit.billing.compose.Paywall
import io.github.thesobiaakbar.adroit.billing.compose.rememberPaywallController

// docs/BILLING_NEW_APP_COMPOSE.md, steps 5 to 7.

// region billing-show-compose
@Composable
fun HomeScreen() {
    // Asked once per screen: true when the show rules (which Firebase can change) allow it now.
    var showPaywall by rememberSaveable {
        mutableStateOf(AdroitBilling.paywalls.shouldShow(AppPaywalls.Onboarding, "onboarding") == ShowDecision.Show)
    }
    // … the screen's content …
    if (showPaywall) {
        Paywall(AppPaywalls.Onboarding, placement = "onboarding", onClose = { showPaywall = false })
    }
}
// endregion

// region billing-inapp-compose
@Composable
fun InAppMessages() {
    val activity = LocalActivity.current
    // Play's messages, for example "your payment was declined": shows nothing when there's none.
    LifecycleResumeEffect(activity) {
        activity?.let(AdroitBilling::showInAppMessages)
        onPauseOrDispose {}
    }
}
// endregion

// region billing-custom-blocks-compose
@Composable
fun UpgradeSheet(onDone: () -> Unit) {
    // The library's blocks in your own order; the template still decides the window (a sheet, a dialog…).
    Paywall(AppPaywalls.Settings, placement = "settings", onClose = { onDone() }) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title(Modifier.weight(1f))
                CloseButton()
            }
            PlanList()
            Message()
            CtaButton()
            Disclosure()
            RestoreButton(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
// endregion

// region billing-custom-ui-compose
@Composable
fun CoinsStore() {
    // A purchase ends a controller: each "Buy more" opens the paywall again with a new one.
    var round by rememberSaveable { mutableIntStateOf(0) }
    key(round) { CoinsOffer(onBuyMore = { round++ }) }
}

@Composable
private fun CoinsOffer(onBuyMore: () -> Unit) {
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val controller = rememberPaywallController(AppPaywalls.Coins, "coins_screen")
    val state by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(controller) { controller.onShown() } // on screen now: reported once per opening
    when (val current = state) {
        PaywallUiState.Loading -> CircularProgressIndicator()
        is PaywallUiState.Content -> Column {
            Text(PriceText.planPrice(resources, current.selectedPlan).orEmpty())
            Button(onClick = { activity?.let(controller::purchase) }, enabled = current.busy == null) {
                Text(PriceText.cta(resources, current.selectedPlan))
            }
        }
        is PaywallUiState.Purchased -> Button(onClick = onBuyMore) { Text("Thank you! Buy more") }
        is PaywallUiState.Unavailable -> Text("The store isn't available right now.")
        is PaywallUiState.Closed -> Unit
    }
}
// endregion

// region billing-restore-compose
@Composable
fun PremiumRows(onUpgrade: () -> Unit, premium: PremiumViewModel = viewModel()) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val entitlements by premium.entitlements.collectAsStateWithLifecycle()
    LaunchedEffect(premium) {
        premium.restoreResults.collect { result ->
            val message = when (result) {
                is RestoreResult.Restored -> if (result.newlyActive.isEmpty()) "Your purchases are up to date" else "Restored"
                RestoreResult.NothingToRestore -> "Nothing to restore"
                is RestoreResult.Failed -> "Couldn't reach Google Play. Try again later."
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (entitlements.has("premium")) "Premium: active" else "Premium: not active")
        Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) { Text("Upgrade") }
        OutlinedButton(onClick = premium::restore, modifier = Modifier.fillMaxWidth()) { Text("Restore purchases") }
        if (activity != null) {
            OutlinedButton(onClick = { premium.manage(activity) }, modifier = Modifier.fillMaxWidth()) {
                Text("Manage subscription")
            }
        }
    }
}
// endregion
