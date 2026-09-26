package com.company.admob.sample.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdsState
import com.company.admob.BannerState
import com.company.admob.FullScreenResult
import com.company.admob.RewardResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration

/** A scrollable screen body with the standard padding and spacing. */
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A titled card grouping related values and actions. */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** "label: value", with the value in monospace so states line up and stay copyable by eye. */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

/** Explanatory text for a screen or section. */
@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A colored chip: green when ready, amber while loading, red on failure, grey otherwise. */
@Composable
fun StatusChip(label: String, tone: Tone, modifier: Modifier = Modifier) {
    val color = when (tone) {
        Tone.GOOD -> Color(0xFF2E7D32)
        Tone.PENDING -> Color(0xFFF9A825)
        Tone.BAD -> Color(0xFFC62828)
        Tone.NEUTRAL -> MaterialTheme.colorScheme.outline
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        modifier = modifier,
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = color),
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
    )
}

enum class Tone { GOOD, PENDING, BAD, NEUTRAL }

fun AdAvailability.tone(): Tone = when (this) {
    AdAvailability.Ready -> Tone.GOOD
    AdAvailability.Loading -> Tone.PENDING
    is AdAvailability.Failed -> Tone.BAD
    AdAvailability.Disabled, AdAvailability.Unavailable -> Tone.NEUTRAL
}

fun AdAvailability.label(): String = when (this) {
    is AdAvailability.Failed -> "Failed: ${error.code}"
    else -> toString()
}

fun AdsState.tone(): Tone = when (this) {
    AdsState.Ready -> Tone.GOOD
    AdsState.GatheringConsent, AdsState.InitializingSdk -> Tone.PENDING
    is AdsState.Failed -> Tone.BAD
    else -> Tone.NEUTRAL
}

fun BannerState?.label(): String = when (this) {
    null -> "not created"
    is BannerState.Failed -> "Failed: ${error.describe()}"
    is BannerState.Hidden -> "Hidden(${reason ?: "destroyed"})"
    else -> toString()
}

fun AdError.describe(): String {
    val retry = if (this is AdError.FrequencyCapped) " (retry after ${retryAfter.short()})" else ""
    return "$code: $message$retry"
}

fun FullScreenResult?.describe(): String = when (this) {
    null -> "none yet"
    is FullScreenResult.Dismissed -> "Dismissed(clicked=$clicked)"
    is FullScreenResult.NotShown -> "NotShown — ${error.describe()}"
}

fun RewardResult?.describe(): String = when (this) {
    null -> "none yet"
    is RewardResult.Earned -> "Earned ${reward.amount} ${reward.type} (rewardId …${rewardId.takeLast(6)})"
    RewardResult.NotEarned -> "NotEarned (closed early)"
    is RewardResult.Failed -> "Failed — ${error.describe()}"
}

/** A duration rounded to whole seconds, or "∞". */
fun Duration.short(): String = if (isInfinite()) "∞" else "${inWholeSeconds} s"

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

/** Wall-clock time of day, for event timestamps. */
fun formatTime(timestampMillis: Long): String = synchronized(timeFormat) { timeFormat.format(Date(timestampMillis)) }

/** An ad unit ID shortened to its last 4 characters, like the library's production logs. */
fun redact(adUnitId: String): String = if (adUnitId.length <= 4) "…" else "…" + adUnitId.takeLast(4)
