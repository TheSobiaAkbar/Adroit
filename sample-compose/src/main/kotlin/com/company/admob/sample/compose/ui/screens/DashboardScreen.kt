package com.company.admob.sample.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.AdPlacement
import com.company.admob.CompanyAdMobVersion
import com.company.admob.compose.rememberAdAvailability
import com.company.admob.sample.compose.AppContainer
import com.company.admob.sample.compose.ads.Placements
import com.company.admob.sample.compose.ui.Destination
import com.company.admob.sample.compose.ui.PlaygroundViewModel
import com.company.admob.sample.compose.ui.components.LabeledValue
import com.company.admob.sample.compose.ui.components.ScreenColumn
import com.company.admob.sample.compose.ui.components.Section
import com.company.admob.sample.compose.ui.components.StatusChip
import com.company.admob.sample.compose.ui.components.label
import com.company.admob.sample.compose.ui.components.tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(container: AppContainer, viewModel: PlaygroundViewModel, onNavigate: (Destination) -> Unit) {
    val ads = container.ads
    val state by ads.state.collectAsStateWithLifecycle()
    val consent by ads.privacy.consentState.collectAsStateWithLifecycle()
    val adFree by viewModel.adFree.collectAsStateWithLifecycle()
    // The Google SDK version is only in the diagnostics report; it's known once the SDK is initialized.
    var googleSdkVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state) {
        googleSdkVersion = withContext(Dispatchers.Default) { ads.diagnostics.snapshot().googleSdkVersion }
    }

    ScreenColumn {
        Section("Library") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AdsState")
                StatusChip(state.toString(), state.tone())
            }
            LabeledValue("Consent", "${consent.status}, canRequestAds=${consent.canRequestAds}")
            LabeledValue("Environment", "${container.config.environment} (profile ${container.profile.label})")
            LabeledValue("CompanyAdMob", CompanyAdMobVersion.NAME)
            LabeledValue("Google SDK", googleSdkVersion ?: "not initialized")
        }
        Section("Full-screen readiness") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Placements.fullScreen.forEach { ReadinessChip(it) }
            }
        }
        Section("Quick actions") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ad-free user", Modifier.weight(1f))
                Switch(checked = adFree, onCheckedChange = viewModel::setAdFree)
            }
            Button(onClick = { ads.preload(*Placements.fullScreen.toTypedArray()) }, Modifier.fillMaxWidth()) {
                Text("Preload all full-screen placements")
            }
            OutlinedButton(onClick = { onNavigate(Destination.Diagnostics) }, Modifier.fillMaxWidth()) {
                Text("Open diagnostics")
            }
            OutlinedButton(onClick = { onNavigate(Destination.Events) }, Modifier.fillMaxWidth()) {
                Text("Open event stream")
            }
        }
    }
}

@Composable
private fun ReadinessChip(placement: AdPlacement) {
    val availability by rememberAdAvailability(placement)
    StatusChip("${placement.format}: ${availability.label()}", availability.tone())
}
