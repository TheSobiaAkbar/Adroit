package com.company.admob.sample.mixed

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.company.admob.CompanyAdMob
import com.company.admob.sample.mixed.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/** The launcher (XML). Starts CompanyAdMob once for the whole app, then opens either integration style. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Gathers consent and initializes the SDK. Called again after a rotation, start() joins the work in progress.
        CompanyAdMob.start(this)

        binding.mediation.text = getString(
            if (BuildConfig.WITH_MEDIATION) R.string.mediation_on else R.string.mediation_off,
        )
        binding.openLegacyXml.setOnClickListener { startActivity(Intent(this, LegacyXmlActivity::class.java)) }
        binding.openComposeHost.setOnClickListener { startActivity(Intent(this, ComposeHostActivity::class.java)) }
        binding.adInspector.setOnClickListener {
            // Lists the mediation adapters (AppLovin with -PwithMediation=true) and their initialization status.
            CompanyAdMob.diagnostics.openAdInspector(this)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CompanyAdMob.state.collect { binding.state.text = getString(R.string.ads_state, it.toString()) }
            }
        }
    }
}
