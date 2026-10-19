package io.github.thesobiaakbar.adroit.billing.compose

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallUiState
import kotlin.coroutines.cancellation.CancellationException

/** The full-screen templates (`plans`, `single`), edge-to-edge with `safeDrawing` insets. */
@Composable
internal fun PaywallScreen(
    controller: PaywallController,
    state: PaywallUiState,
    modifier: Modifier,
    imageLoader: (@Composable (url: String) -> Unit)?,
    content: (@Composable PaywallScope.() -> Unit)?,
) {
    var backProgress by remember { mutableFloatStateOf(0f) }
    val backAllowed = state.backAllowed()
    // Back is consumed while `close.back` forbids it.
    BackHandler(enabled = !backAllowed) {}
    // Otherwise the predictive back gesture shrinks the paywall, and completing it closes it.
    PredictiveBackHandler(enabled = backAllowed) { progress ->
        try {
            progress.collect { backProgress = it.progress }
            controller.onBackPressed()
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }
    Surface(
        modifier = modifier.fillMaxSize().graphicsLayer {
            val scale = 1f - backProgress * 0.1f
            scaleX = scale
            scaleY = scale
        },
        color = MaterialTheme.colorScheme.background,
    ) {
        PaywallBody(controller, state, imageLoader, content, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing))
    }
}

/** Whether back closes the paywall now: per `close.back` while the plans show; always while it loads or failed. */
internal fun PaywallUiState.backAllowed(): Boolean = when (this) {
    is PaywallUiState.Content -> backAllowed
    is PaywallUiState.Closed -> false
    else -> true
}

/** Whether the user may dismiss the paywall now (tap outside, swipe down): its close button is visible. */
internal fun PaywallUiState.dismissible(): Boolean = when (this) {
    is PaywallUiState.Content -> closeVisible && busy == null
    is PaywallUiState.Closed -> false
    else -> true
}
