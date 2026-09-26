package com.company.admob.sample.mixed

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.company.admob.CompanyAdMob
import com.company.admob.FullScreenResult
import com.company.admob.compose.AdNative
import com.company.admob.sample.mixed.ads.Placements
import com.company.admob.sample.mixed.databinding.ActivityLegacyXmlBinding
import com.company.admob.views.NativeTemplates
import kotlinx.coroutines.launch

/**
 * A classic XML screen part-way through a Compose migration: an XML `AdBannerView`, a `ComposeView` rendering
 * `AdNative`, and an interstitial button. The XML text and the Compose text observe the same `CompanyAdMob.state`.
 */
class LegacyXmlActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLegacyXmlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // The ComposeView uses this Activity as its lifecycle and ViewModel store owner, so the native ad is kept
        // across rotation and destroyed with the Activity.
        binding.composeNative.setContent {
            MaterialTheme {
                val state by CompanyAdMob.state.collectAsStateWithLifecycle()
                Column {
                    Text(getString(R.string.compose_state, state.toString()), style = MaterialTheme.typography.bodySmall)
                    AdNative(
                        placement = Placements.ComposeInXmlNative,
                        modifier = Modifier.fillMaxWidth(),
                        layout = NativeTemplates.Medium,
                    )
                }
            }
        }

        binding.showInterstitial.setOnClickListener {
            CompanyAdMob.showInterstitial(this, Placements.Interstitial) { result ->
                binding.lastResult.text = getString(
                    R.string.last_result,
                    if (result is FullScreenResult.NotShown) "not shown (${result.error.code})" else "shown",
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CompanyAdMob.state.collect { binding.xmlState.text = getString(R.string.xml_state, it.toString()) }
            }
        }
    }
}
