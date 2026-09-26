package com.company.admob.sample.compose.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.sample.compose.AppContainer
import com.company.admob.sample.compose.ui.components.Hint
import com.company.admob.sample.compose.ui.components.LabeledValue
import com.company.admob.sample.compose.ui.components.ScreenColumn
import com.company.admob.sample.compose.ui.components.Section
import com.company.admob.sample.compose.ui.components.formatTime
import kotlinx.coroutines.launch

@Composable
fun InitializationScreen(container: AppContainer) {
    val ads = container.ads
    val activity = LocalActivity.current
    val state by ads.state.collectAsStateWithLifecycle()
    val timeline by container.eventLog.stateTimeline.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var startResult by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitResult by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenColumn {
        Section("Current state") {
            LabeledValue("AdsState", state.toString())
            LabeledValue("isReady", ads.isReady.toString())
        }
        Section("Actions") {
            Hint("start() is idempotent: MainActivity already called it. Calling again joins or repeats nothing.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    startResult = "waiting…"
                    activity?.let { ads.start(it) { result -> startResult = result.toString() } }
                }) { Text("start()") }
                OutlinedButton(onClick = {
                    awaitResult = "waiting…"
                    scope.launch { awaitResult = if (ads.awaitReady()) "true (Ready)" else "false (timeout)" }
                }) { Text("awaitReady()") }
            }
            startResult?.let { LabeledValue("start → onComplete", it) }
            awaitResult?.let { LabeledValue("awaitReady", it) }
        }
        Section("Timeline (StateChanged events)") {
            if (timeline.isEmpty()) Hint("No state change yet.")
            timeline.forEach { LabeledValue(formatTime(it.timestampMillis), it.state.toString()) }
        }
    }
}
