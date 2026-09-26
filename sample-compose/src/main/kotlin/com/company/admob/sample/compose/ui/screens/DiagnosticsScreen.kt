package com.company.admob.sample.compose.ui.screens

import android.content.ClipData
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.sample.compose.AppContainer
import com.company.admob.sample.compose.ui.components.Hint
import com.company.admob.sample.compose.ui.components.describe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsScreen(container: AppContainer) {
    val ads = container.ads
    val activity = LocalActivity.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // No polling: the report is rebuilt whenever the library reports an event (or on demand).
    val events by container.eventLog.events.collectAsStateWithLifecycle()
    var refreshes by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf("") }
    var inspectorResult by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(events.firstOrNull(), refreshes) {
        report = withContext(Dispatchers.Default) { ads.diagnostics.snapshot().toString() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("CompanyAdMob diagnostics", report))) }
            }) { Text("Copy") }
            OutlinedButton(onClick = { refreshes++ }) { Text("Refresh") }
            OutlinedButton(onClick = {
                activity?.let {
                    ads.diagnostics.openAdInspector(it) { error -> inspectorResult = error?.describe() ?: "closed" }
                }
            }) { Text("Ad Inspector") }
        }
        inspectorResult?.let { Hint("Ad Inspector: $it") }
        Hint("Ad unit IDs are redacted in PRODUCTION. Updated on every library event.")
        SelectionContainer(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
