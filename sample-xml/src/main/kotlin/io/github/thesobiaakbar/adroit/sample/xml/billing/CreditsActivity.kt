package io.github.thesobiaakbar.adroit.sample.xml.billing

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.thesobiaakbar.adroit.sample.xml.R
import io.github.thesobiaakbar.adroit.sample.xml.databinding.ActivityCreditsBinding
import kotlinx.coroutines.launch

/**
 * A consumable: the `CreditsStore` paywall embedded in XML (`PaywallView`), and the balance [CoinWallet] keeps. The
 * wallet, not this screen, credits each purchase, so a purchase finishing after the screen closed still counts.
 */
class CreditsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityCreditsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CoinWallet.balance.collect { binding.balance.text = getString(R.string.credits_balance, it) }
            }
        }
    }
}
