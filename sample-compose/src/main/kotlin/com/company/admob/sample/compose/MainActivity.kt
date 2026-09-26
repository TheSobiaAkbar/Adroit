package com.company.admob.sample.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.company.admob.sample.compose.ui.PlaygroundApp
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = appContainer
        // Gathers consent and initializes the SDK. Called again after a rotation, start() joins the work in progress.
        container.ads.start(this)
        setContent { PlaygroundApp(container, onRestart = ::restartApp) }
    }

    /** Restarts the process, so a new configuration profile is applied by `initialize`. Debug tool only. */
    private fun restartApp() {
        val restart = Intent.makeRestartActivityTask(checkNotNull(intent.component))
        startActivity(restart)
        exitProcess(0)
    }
}
