package io.github.thesobiaakbar.adroit.billing.compose

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.CloseButtonPosition
import io.github.thesobiaakbar.adroit.billing.PaywallCloseReason
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.PaywallTemplate
import io.github.thesobiaakbar.adroit.billing.PaywallUiState
import io.github.thesobiaakbar.adroit.billing.SuccessAction
import kotlinx.coroutines.CoroutineScope
import java.util.UUID
import io.github.thesobiaakbar.adroit.billing.R as BillingR

/**
 * A paywall for [definition], opened at [placement], laid out by its Firebase `template` (`plans` and `single` fill
 * the screen, `bottom_sheet` and `dialog` open a window) and styled by its `style`. Its controller lives in a
 * ViewModel, so a rotation keeps the selection and the close-button countdown.
 *
 * ```
 * if (showPaywall) {
 *     Paywall(AppPaywalls.Onboarding, placement = "onboarding", onClose = { showPaywall = false })
 * }
 * ```
 *
 * The paywall handles back per `close.back`, its own window insets (edge-to-edge), and closes itself after a purchase
 * when `successAction` is `CLOSE`. A paywall that can't load can always be closed.
 *
 * @param onClose called once when the paywall closes, with why; remove the paywall from composition then.
 * @param imageLoader draws `style.headerImage`; the library has no image loader. With Coil:
 *   `imageLoader = { url -> AsyncImage(model = url, contentDescription = null, modifier = Modifier.height(160.dp)) }`.
 *   Without one, the header image is skipped.
 * @param onExitPaywall called after [onClose] with the `exitPaywall` to show next, when the configuration names one
 *   and its rules allow it.
 * @param content a custom layout of [PaywallScope] blocks, used while the plans are shown; null uses the template.
 */
@Composable
public fun Paywall(
    definition: PaywallDefinition,
    placement: String,
    onClose: (PaywallCloseReason) -> Unit,
    modifier: Modifier = Modifier,
    imageLoader: (@Composable (url: String) -> Unit)? = null,
    onExitPaywall: ((PaywallDefinition) -> Unit)? = null,
    content: (@Composable PaywallScope.() -> Unit)? = null,
) {
    Paywall(rememberPaywallController(definition, placement), onClose, modifier, imageLoader, onExitPaywall, content)
}

/** A paywall rendering [controller], for example one from [rememberPaywallController] or `PaywallController.forModel`. */
@Composable
public fun Paywall(
    controller: PaywallController,
    onClose: (PaywallCloseReason) -> Unit,
    modifier: Modifier = Modifier,
    imageLoader: (@Composable (url: String) -> Unit)? = null,
    onExitPaywall: ((PaywallDefinition) -> Unit)? = null,
    content: (@Composable PaywallScope.() -> Unit)? = null,
) {
    val state by controller.state.collectAsState()
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnExit by rememberUpdatedState(onExitPaywall)
    LaunchedEffect(controller) {
        // After the first frame: the paywall is on screen, so its close delay and its showing start now.
        withFrameNanos { }
        controller.onShown()
    }
    LaunchedEffect(state) {
        when (val current = state) {
            is PaywallUiState.Purchased -> if (controller.spec.successAction == SuccessAction.CLOSE) controller.onCloseClicked()
            is PaywallUiState.Closed -> {
                currentOnClose(current.reason)
                current.exitPaywall?.let { exit -> currentOnExit?.invoke(exit) }
            }
            else -> Unit
        }
    }
    if (state is PaywallUiState.Closed) return
    PaywallTheme(controller.spec.style) {
        when (controller.spec.template) {
            PaywallTemplate.DIALOG -> PaywallDialog(controller, state, imageLoader, content)
            PaywallTemplate.BOTTOM_SHEET -> PaywallBottomSheet(controller, state, imageLoader, content)
            else -> PaywallScreen(controller, state, modifier, imageLoader, content)
        }
    }
}

/**
 * The controller of [definition] at [placement], kept in a ViewModel of the current owner, for a fully custom paywall.
 * Render its `state` and call `onShown()` once it is on screen; [Paywall] does both.
 */
@Composable
public fun rememberPaywallController(definition: PaywallDefinition, placement: String): PaywallController =
    rememberPaywallController("${definition.name}:$placement") { scope ->
        AdroitBilling.paywalls.controller(definition, placement, scope)
    }

/**
 * A controller from [factory], in a ViewModel keyed by [key] and by this opening: a rotation keeps it, a later opening
 * of the same paywall gets a new one.
 */
@Composable
internal fun rememberPaywallController(key: String, factory: (CoroutineScope) -> PaywallController): PaywallController {
    val opening = rememberSaveable { UUID.randomUUID().toString() }
    // ponytail: a closed opening's ViewModel stays in the owner's store until the owner is cleared (a few small objects
    // per opening, no Activity). ViewModelStore can't remove one key.
    return viewModel(key = "adroit_paywall:$key:$opening") { PaywallViewModel(createSavedStateHandle(), factory) }.controller
}

internal class PaywallViewModel(handle: SavedStateHandle, factory: (CoroutineScope) -> PaywallController) : ViewModel() {
    val controller: PaywallController = factory(viewModelScope)

    init {
        // After process death: the selection, the delay left and the visible time come back.
        handle.get<Bundle>(STATE_KEY)?.let { saved ->
            controller.restore(saved.keySet().mapNotNull { key -> saved.value(key)?.let { key to it } }.toMap())
        }
        handle.setSavedStateProvider(STATE_KEY) {
            Bundle().apply {
                controller.saveState().forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Boolean -> putBoolean(key, value)
                    }
                }
            }
        }
    }

    override fun onCleared() = controller.dispose()

    private fun Bundle.value(key: String): Any? {
        @Suppress("DEPRECATION")
        return get(key)
    }

    private companion object {
        const val STATE_KEY = "adroit_paywall_state"
    }
}

/**
 * Everything inside the paywall's surface: the loading, unavailable, purchased and content states, the close button
 * at its configured corner, and the TalkBack order title, plans, purchase button, terms (then the close button).
 */
@Composable
internal fun PaywallBody(
    controller: PaywallController,
    state: PaywallUiState,
    imageLoader: (@Composable (url: String) -> Unit)?,
    content: (@Composable PaywallScope.() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val corner = if (controller.spec.close.position == CloseButtonPosition.TOP_START) Alignment.TopStart else Alignment.TopEnd
    Box(modifier.semantics { isTraversalGroup = true }) {
        when (state) {
            is PaywallUiState.Content -> {
                val scope = PaywallScope(controller, state, imageLoader)
                if (content != null) content(scope) else DefaultLayout(scope, single = controller.spec.template == PaywallTemplate.SINGLE)
                if (content == null) scope.CloseButton(Modifier.align(corner).semantics { traversalIndex = 1f })
            }
            PaywallUiState.Loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center).padding(48.dp))
                AlwaysClose(controller, Modifier.align(corner))
            }
            is PaywallUiState.Unavailable -> {
                Text(
                    stringResource(BillingR.string.adroit_billing_paywall_unavailable),
                    Modifier.align(Alignment.Center).padding(48.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                AlwaysClose(controller, Modifier.align(corner))
            }
            is PaywallUiState.Purchased -> if (controller.spec.successAction == SuccessAction.STAY_WITH_MESSAGE) {
                Premium(controller, consumable = state.result.grant == null, Modifier.align(Alignment.Center))
            }
            is PaywallUiState.Closed -> Unit
        }
    }
}

@Composable
private fun AlwaysClose(controller: PaywallController, modifier: Modifier) {
    CloseControl(visible = true, countdown = null, delaySeconds = 0, enabled = true, onClick = controller::onCloseClicked, modifier = modifier)
}

@Composable
// A consumable grants nothing (no grant): thank the user without calling them premium.
private fun Premium(controller: PaywallController, consumable: Boolean, modifier: Modifier) {
    Column(
        modifier.padding(PaywallDefaults.ContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (consumable) BillingR.string.adroit_billing_paywall_thanks_title else BillingR.string.adroit_billing_paywall_premium_title),
            Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(if (consumable) BillingR.string.adroit_billing_paywall_thanks_text else BillingR.string.adroit_billing_paywall_premium_text),
            textAlign = TextAlign.Center,
        )
        Button(onClick = controller::onCloseClicked) { Text(stringResource(BillingR.string.adroit_billing_paywall_done)) }
    }
}

/** The built-in layout: scrolling header, features and plans; the purchase button and terms below. */
@Composable
private fun DefaultLayout(scope: PaywallScope, single: Boolean) = with(scope) {
    Column(Modifier.padding(PaywallDefaults.ContentPadding)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(PaywallDefaults.CloseButtonSize - 16.dp))
            HeaderImage()
            Title()
            Features()
            if (single) PlanCard(state.selectedPlan) else PlanList()
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Message()
            CtaButton()
            Disclosure()
            Row(horizontalArrangement = Arrangement.Center) {
                RestoreButton()
                ManageButton()
            }
        }
    }
}
