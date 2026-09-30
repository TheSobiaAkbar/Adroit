package io.github.thesobiaakbar.adroit.sample.compose.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.thesobiaakbar.adroit.sample.compose.R
import kotlinx.coroutines.delay

/**
 * The intro screen Google requires before a rewarded interstitial
 * (https://developers.google.com/admob/android/next-gen/rewarded-interstitial): it announces the reward and gives the
 * user a way to opt out ("No thanks") before the ad starts, after a 5-second countdown. The countdown survives
 * rotation.
 */
@Composable
fun BonusIntroDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    var remaining by rememberSaveable { mutableIntStateOf(COUNTDOWN_SECONDS) }
    val currentOnAccept by rememberUpdatedState(onAccept)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
        currentOnAccept()
        currentOnDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bonus_intro)) },
        text = { Text(stringResource(R.string.bonus_countdown, remaining.coerceAtLeast(1))) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.no_thanks)) } },
    )
}

private const val COUNTDOWN_SECONDS = 5
