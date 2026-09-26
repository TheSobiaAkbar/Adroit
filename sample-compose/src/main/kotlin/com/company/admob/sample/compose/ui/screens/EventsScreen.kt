package com.company.admob.sample.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.AdFormat
import com.company.admob.events.AdEvent
import com.company.admob.sample.compose.AppContainer
import com.company.admob.sample.compose.ui.components.Hint
import com.company.admob.sample.compose.ui.components.formatTime

/** The live event stream (last 200), filterable by format and by event type. */
@Composable
fun EventsScreen(container: AppContainer) {
    val events by container.eventLog.events.collectAsStateWithLifecycle()
    // Filters are names, so they survive rotation; empty means "all".
    var formats by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var types by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val shown = events.filter { event ->
        (formats.isEmpty() || event.formatName() in formats) && (types.isEmpty() || event.typeName() in types)
    }

    Column(Modifier.fillMaxSize()) {
        ChipRow(FORMAT_FILTERS, formats) { formats = it }
        ChipRow(TYPE_FILTERS, types) { types = it }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Hint("${shown.size} of ${events.size} events, newest first")
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = container.eventLog::clear) { Text("Clear") }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(shown) { event ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        "${formatTime(event.timestampMillis)}  ${event.typeName()}  ${event.formatName()}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(event.toString(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: List<String>, onChange: (List<String>) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options) { option ->
            val isSelected = option in selected
            FilterChip(
                selected = isSelected,
                onClick = { onChange(if (isSelected) selected - option else selected + option) },
                label = { Text(option) },
            )
        }
    }
}

/** "GLOBAL" for events that concern no placement (state, consent, configuration). */
private fun AdEvent.formatName(): String = format?.name ?: GLOBAL

/** Spelled out rather than `this::class.simpleName`, which R8 renames in the minified release build. */
private fun AdEvent.typeName(): String = when (this) {
    is AdEvent.LoadRequested -> "LoadRequested"
    is AdEvent.Loaded -> "Loaded"
    is AdEvent.LoadFailed -> "LoadFailed"
    is AdEvent.ShowRequested -> "ShowRequested"
    is AdEvent.NotShown -> "NotShown"
    is AdEvent.Shown -> "Shown"
    is AdEvent.Impression -> "Impression"
    is AdEvent.Clicked -> "Clicked"
    is AdEvent.Dismissed -> "Dismissed"
    is AdEvent.RewardEarned -> "RewardEarned"
    is AdEvent.Paid -> "Paid"
    is AdEvent.Expired -> "Expired"
    is AdEvent.Destroyed -> "Destroyed"
    is AdEvent.StateChanged -> "StateChanged"
    is AdEvent.ConsentUpdated -> "ConsentUpdated"
    is AdEvent.ConfigurationWarning -> "ConfigurationWarning"
}

private const val GLOBAL = "GLOBAL"

private val FORMAT_FILTERS: List<String> = AdFormat.entries.map { it.name } + GLOBAL

private val TYPE_FILTERS: List<String> = listOf(
    "LoadRequested", "Loaded", "LoadFailed", "ShowRequested", "NotShown", "Shown", "Impression", "Clicked",
    "Dismissed", "RewardEarned", "Paid", "Expired", "Destroyed", "StateChanged", "ConsentUpdated",
    "ConfigurationWarning",
)
