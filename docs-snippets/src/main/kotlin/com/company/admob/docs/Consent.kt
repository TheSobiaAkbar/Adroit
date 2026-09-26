@file:Suppress("unused")

package com.company.admob.docs

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
import com.company.admob.CompanyAdMob
import kotlinx.coroutines.launch

// region privacy-button-views
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val privacyButton = Button(this).apply { text = "Privacy options" }
        setContentView(privacyButton)

        privacyButton.setOnClickListener {
            CompanyAdMob.privacy.showPrivacyOptions(this) { error ->
                if (error != null) Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            }
        }
        // Shown only while UMP requires an entry point (for example, for users in the EEA).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CompanyAdMob.privacy.isPrivacyOptionsRequired.collect { privacyButton.isVisible = it }
            }
        }
    }
}
// endregion

// region privacy-button-compose
@Composable
fun PrivacyOptionsButton() {
    val required by CompanyAdMob.privacy.isPrivacyOptionsRequired.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    if (required && activity != null) {
        Button(onClick = { CompanyAdMob.privacy.showPrivacyOptions(activity) }) { Text("Privacy options") }
    }
}
// endregion

// region consent-state
@Composable
fun ConsentStatusLine() {
    val consent by CompanyAdMob.privacy.consentState.collectAsStateWithLifecycle()
    Text("Consent: ${consent.status}, can request ads: ${consent.canRequestAds}")
}
// endregion

// region reset-consent
/** Debug builds only: forget the stored consent, so the next start() shows the form again. */
fun resetConsentForQa() {
    CompanyAdMob.privacy.resetConsentForTesting()
}
// endregion
