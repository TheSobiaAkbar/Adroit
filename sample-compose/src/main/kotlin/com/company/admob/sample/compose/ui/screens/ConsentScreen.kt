package com.company.admob.sample.compose.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.sample.compose.AppContainer
import com.company.admob.sample.compose.BuildConfig
import com.company.admob.sample.compose.ui.components.Hint
import com.company.admob.sample.compose.ui.components.LabeledValue
import com.company.admob.sample.compose.ui.components.ScreenColumn
import com.company.admob.sample.compose.ui.components.Section
import com.company.admob.sample.compose.ui.components.describe

@Composable
fun ConsentScreen(container: AppContainer) {
    val ads = container.ads
    val activity = LocalActivity.current
    val consent by ads.privacy.consentState.collectAsStateWithLifecycle()
    val privacyOptionsRequired by ads.privacy.isPrivacyOptionsRequired.collectAsStateWithLifecycle()
    var lastAction by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenColumn {
        Section("Consent state") {
            LabeledValue("Status", consent.status.toString())
            LabeledValue("canRequestAds", consent.canRequestAds.toString())
            LabeledValue("Privacy options required", privacyOptionsRequired.toString())
            LabeledValue("Last error", consent.lastError?.describe() ?: "none")
            LabeledValue("Debug geography", container.config.privacy.debugGeography.toString())
            Hint("The debug geography is part of the configuration profile; UMP applies it on test devices only.")
        }
        Section("Actions") {
            // Shown only while UMP requires a privacy options entry point, as Google's policy asks.
            if (privacyOptionsRequired) {
                Button(
                    onClick = {
                        activity?.let {
                            ads.privacy.showPrivacyOptions(it) { error ->
                                lastAction = "Privacy options closed: ${error?.describe() ?: "OK"}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Privacy options") }
            } else {
                Hint("No privacy options entry point is required for this user.")
            }
            if (BuildConfig.DEBUG) {
                OutlinedButton(
                    onClick = {
                        ads.privacy.resetConsentForTesting()
                        lastAction = "Consent reset. Restart the app to see the consent form again."
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reset consent (debug)") }
            }
            lastAction?.let { Hint(it) }
        }
    }
}
