package io.github.thesobiaakbar.adroit.billing.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallUiState

/**
 * The `bottom_sheet` template. Swiping down and tapping outside close it only once its close button shows; back follows
 * `close.back` (with Material's predictive back animation when allowed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaywallBottomSheet(
    controller: PaywallController,
    state: PaywallUiState,
    imageLoader: (@Composable (url: String) -> Unit)?,
    content: (@Composable PaywallScope.() -> Unit)?,
) {
    val dismissible by rememberUpdatedState(state.dismissible() || state.backAllowed())
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || dismissible },
    )
    ModalBottomSheet(
        onDismissRequest = {
            // Back when it is allowed, else the close button's rule (a swipe or tap outside once the X shows).
            if (controller.onBackPressed()) controller.onCloseClicked()
        },
        sheetState = sheetState,
        sheetMaxWidth = PaywallDefaults.MaxWidth,
        sheetGesturesEnabled = state.dismissible(),
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = state.backAllowed(),
            shouldDismissOnClickOutside = state.dismissible(),
        ),
    ) {
        PaywallBody(controller, state, imageLoader, content)
    }
}
