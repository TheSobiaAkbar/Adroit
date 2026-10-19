package io.github.thesobiaakbar.adroit.sample.compose.billing

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thesobiaakbar.adroit.billing.PaywallUiState
import io.github.thesobiaakbar.adroit.billing.compose.rememberPaywallController
import io.github.thesobiaakbar.adroit.sample.compose.R

/**
 * The Credits screen: the `CreditsStore` paywall in a UI of the app's own, on its controller alone (no `Paywall`
 * composable), so a custom UI needs nothing else. The coins are credited by [CoinWallet], for the whole app.
 */
@Composable
fun CreditsScreen() {
    val balance by CoinWallet.balance.collectAsStateWithLifecycle()
    // A purchase ends a controller; each "Buy more" opens the paywall again with a new one.
    var round by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.credits_balance, balance), style = MaterialTheme.typography.headlineSmall)
        key(round) { CreditsStore(onBuyMore = { round++ }) }
    }
}

@Composable
private fun CreditsStore(onBuyMore: () -> Unit) {
    val activity = LocalActivity.current
    val controller = rememberPaywallController(SamplePaywalls.CreditsStore, "credits")
    val state by controller.state.collectAsStateWithLifecycle()
    // What Paywall does for its own UI: the paywall is on screen, so its showing is reported now.
    LaunchedEffect(controller) { controller.onShown() }
    when (val current = state) {
        PaywallUiState.Loading -> CircularProgressIndicator()
        is PaywallUiState.Content -> {
            val plan = current.selectedPlan
            Text(plan.texts.title ?: plan.product.displayName, style = MaterialTheme.typography.titleLarge)
            plan.option?.price?.let { Text(it.formatted, style = MaterialTheme.typography.bodyLarge) }
            Button(
                onClick = { activity?.let(controller::purchase) },
                enabled = current.busy == null && activity != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(plan.texts.cta ?: stringResource(R.string.credits_buy)) }
            current.message?.let { Text(stringResource(it.text), color = MaterialTheme.colorScheme.error) }
        }
        is PaywallUiState.Purchased -> {
            Text(stringResource(R.string.credits_thanks), style = MaterialTheme.typography.titleLarge)
            Button(onClick = onBuyMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.credits_buy_more)) }
        }
        is PaywallUiState.Unavailable -> Text(stringResource(R.string.credits_unavailable))
        is PaywallUiState.Closed -> Unit
    }
}
