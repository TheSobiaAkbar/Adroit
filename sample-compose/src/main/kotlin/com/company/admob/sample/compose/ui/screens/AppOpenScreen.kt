package com.company.admob.sample.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.sample.compose.AppContainer
import com.company.admob.sample.compose.ads.PlaygroundAdsConfig
import com.company.admob.sample.compose.ads.Placements
import com.company.admob.sample.compose.ui.PlaygroundViewModel
import com.company.admob.sample.compose.ui.components.Hint
import com.company.admob.sample.compose.ui.components.LabeledValue
import com.company.admob.sample.compose.ui.components.ScreenColumn
import com.company.admob.sample.compose.ui.components.Section

@Composable
fun AppOpenScreen(container: AppContainer, viewModel: PlaygroundViewModel) {
    val appOpen = container.ads.appOpen
    val enabled by viewModel.appOpenEnabled.collectAsStateWithLifecycle()
    var skipRequested by rememberSaveable { mutableStateOf(false) }
    var suppressHere by rememberSaveable { mutableStateOf(false) }

    // suppressWhile has no "unsuppress": it follows the owner. This screen's NavBackStackEntry is the owner, so
    // suppression ends when the screen is left. Re-applied after a rotation (same entry, so it's harmless).
    val lifecycleOwner = LocalLifecycleOwner.current
    if (suppressHere) LaunchedEffect(lifecycleOwner) { appOpen.suppressWhile(lifecycleOwner) }

    ScreenColumn {
        AvailabilitySection(Placements.AppOpen)
        Section("Controls") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Automatic app open ads", Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = viewModel::setAppOpenEnabled)
            }
            OutlinedButton(
                onClick = {
                    appOpen.skipNextForeground()
                    skipRequested = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Skip next foreground") }
            if (skipRequested) Hint("The next return to the app won't show an app open ad.")
            OutlinedButton(onClick = { suppressHere = true }, Modifier.fillMaxWidth(), enabled = !suppressHere) {
                Text(if (suppressHere) "Suppressed while this screen is open" else "Suppress while this screen is open")
            }
        }
        Section("Launches") {
            LabeledValue("This launch", "#${container.launchNumber}")
            LabeledValue("First automatic show", "after launch #${PlaygroundAdsConfig.APP_OPEN_MIN_LAUNCHES}")
            Hint("Counted by the playground; the library's own launch count isn't exposed.")
        }
        Section("How to test") {
            Hint(
                "1. Wait for the availability to be Ready (it preloads after start).\n" +
                    "2. Press Home, then return to the app: an app open ad shows.\n" +
                    "3. It won't show again within the 1-minute cooldown, within 30 s of another full-screen ad, " +
                    "after Skip next foreground, or while suppressed.",
            )
        }
    }
}
