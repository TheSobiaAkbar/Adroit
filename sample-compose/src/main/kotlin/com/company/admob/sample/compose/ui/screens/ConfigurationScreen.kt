package com.company.admob.sample.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.AdFormat
import com.company.admob.DiagnosticsReport
import com.company.admob.config.FrequencyRule
import com.company.admob.config.PlacementConfig
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import com.company.admob.sample.compose.AppContainer
import com.company.admob.sample.compose.BuildConfig
import com.company.admob.sample.compose.ads.ConfigProfile
import com.company.admob.sample.compose.ui.components.Hint
import com.company.admob.sample.compose.ui.components.LabeledValue
import com.company.admob.sample.compose.ui.components.ScreenColumn
import com.company.admob.sample.compose.ui.components.Section
import com.company.admob.sample.compose.ui.components.redact
import com.company.admob.sample.compose.ui.components.short
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ConfigurationScreen(container: AppContainer, onRestart: () -> Unit) {
    val overrides by container.remoteOverrides.collectAsStateWithLifecycle()
    val events by container.eventLog.events.collectAsStateWithLifecycle()
    // Rebuilt when the overrides or the library's state change: no polling.
    var report by remember { mutableStateOf<DiagnosticsReport?>(null) }
    LaunchedEffect(overrides, events.firstOrNull()) {
        report = withContext(Dispatchers.Default) { container.ads.diagnostics.snapshot() }
    }

    ScreenColumn {
        if (BuildConfig.DEBUG) ProfileSection(container, onRestart)
        Section("Effective placements") {
            Hint("Local configuration; 'enabled' and the ad unit are the effective values, after remote overrides.")
            container.config.placements.forEachIndexed { index, placement ->
                if (index > 0) HorizontalDivider()
                PlacementRow(placement, report)
            }
        }
        Section("Active remote overrides") {
            Text(report?.activeOverrides ?: "…", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        RemoteEditor(container, overrides)
    }
}

@Composable
private fun ProfileSection(container: AppContainer, onRestart: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(container.profile) }
    Section("Configuration profile (debug)") {
        Hint(
            "The library's configuration is fixed for the life of the process: initialize() keeps the first one. " +
                "A new profile is stored and applied when the app restarts.",
        )
        ConfigProfile.entries.forEach { profile ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = profile == selected, role = Role.RadioButton) { selected = profile },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = profile == selected, onClick = null)
                Column {
                    Text(profile.label + if (profile == container.profile) " (running)" else "")
                    Hint(profile.description)
                }
            }
        }
        Button(
            onClick = {
                container.preferences.storeProfile(selected)
                onRestart()
            },
            enabled = selected != container.profile,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply and restart the app") }
    }
}

@Composable
private fun PlacementRow(placement: PlacementConfig, report: DiagnosticsReport?) {
    val effective = report?.placements?.firstOrNull { it.key == placement.placement.key }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(placement.placement.key, style = MaterialTheme.typography.titleSmall)
        LabeledValue("Format", placement.format.toString())
        LabeledValue("Enabled", effective?.enabled?.toString() ?: placement.enabled.toString())
        LabeledValue("Ad unit", redact(effective?.adUnitId ?: placement.adUnitId.value))
        LabeledValue("Preload", placement.preload.toString())
        LabeledValue("Frequency", placement.frequency.describe())
    }
}

private fun FrequencyRule.describe(): String {
    val perSession = if (maxPerSession == Int.MAX_VALUE) "unlimited" else "$maxPerSession"
    return "min interval ${minInterval.short()}, max $perSession per session"
}

/** Edits the fake remote config. Each change is a new [AdOverrides], applied live by the library. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteEditor(container: AppContainer, overrides: AdOverrides) {
    fun publish(
        adsEnabled: Boolean? = overrides.adsEnabled,
        disabledFormats: Set<AdFormat> = overrides.disabledFormats,
        placements: Map<String, PlacementOverride> = overrides.placements,
    ) {
        container.remoteOverrides.value = AdOverrides(
            adsEnabled = adsEnabled,
            disabledFormats = disabledFormats,
            placements = placements,
            minIntervalBetweenFullScreenAds = overrides.minIntervalBetweenFullScreenAds,
        )
    }

    Section("Fake remote config") {
        Hint("A local AdConfigProvider backed by a MutableStateFlow, standing in for a remote config service.")
        SwitchRow("Ads enabled (kill switch)", checked = overrides.adsEnabled != false) {
            publish(adsEnabled = if (it) null else false)
        }
        Text("Disabled formats", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdFormat.entries.forEach { format ->
                val disabled = format in overrides.disabledFormats
                FilterChip(
                    selected = disabled,
                    onClick = {
                        publish(
                            disabledFormats = if (disabled) {
                                overrides.disabledFormats - format
                            } else {
                                overrides.disabledFormats + format
                            },
                        )
                    },
                    label = { Text(format.name) },
                )
            }
        }
        Text("Placements", style = MaterialTheme.typography.labelLarge)
        container.config.placements.forEach { placement ->
            val key = placement.placement.key
            SwitchRow(key, checked = overrides.placements[key]?.enabled != false) { enabled ->
                publish(
                    placements = if (enabled) {
                        overrides.placements - key
                    } else {
                        overrides.placements + (key to PlacementOverride(enabled = false))
                    },
                )
            }
        }
        OutlinedButton(onClick = { container.remoteOverrides.value = AdOverrides() }, Modifier.fillMaxWidth()) {
            Text("Clear all overrides")
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
