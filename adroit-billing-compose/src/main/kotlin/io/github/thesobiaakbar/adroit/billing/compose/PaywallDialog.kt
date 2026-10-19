package io.github.thesobiaakbar.adroit.billing.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallUiState

/**
 * The `dialog` template. Back goes to the controller, which keeps the dialog open while `close.back` forbids it; a tap
 * outside never closes it (the close button does).
 */
@Composable
internal fun PaywallDialog(
    controller: PaywallController,
    state: PaywallUiState,
    imageLoader: (@Composable (url: String) -> Unit)?,
    content: (@Composable PaywallScope.() -> Unit)?,
) {
    Dialog(
        onDismissRequest = { controller.onBackPressed() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.padding(24.dp).widthIn(max = PaywallDefaults.MaxWidth),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            PaywallBody(controller, state, imageLoader, content)
        }
    }
}
