@file:Suppress("unused")

package io.github.thesobiaakbar.adroit.docs

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.thesobiaakbar.adroit.Adroit
import kotlinx.coroutines.launch

// region privacy-button-views
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val privacyButton = Button(this).apply { text = "Privacy options" }
        setContentView(privacyButton)

        privacyButton.setOnClickListener {
            Adroit.privacy.showPrivacyOptions(this) { error ->
                if (error != null) Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            }
        }
        // Shown only while UMP requires an entry point (for example, for users in the EEA).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Adroit.privacy.isPrivacyOptionsRequired.collect { privacyButton.isVisible = it }
            }
        }
    }
}
// endregion

// region privacy-button-compose
@Composable
fun PrivacyOptionsButton() {
    val required by Adroit.privacy.isPrivacyOptionsRequired.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    if (required && activity != null) {
        Button(onClick = { Adroit.privacy.showPrivacyOptions(activity) }) { Text("Privacy options") }
    }
}
// endregion

// region consent-state
@Composable
fun ConsentStatusLine() {
    val consent by Adroit.privacy.consentState.collectAsStateWithLifecycle()
    Text("Consent: ${consent.status}, can request ads: ${consent.canRequestAds}")
}
// endregion

// region reset-consent
/** Debug builds only: forget the stored consent, so the next start() shows the form again. */
fun resetConsentForQa() {
    Adroit.privacy.resetConsentForTesting()
}
// endregion
