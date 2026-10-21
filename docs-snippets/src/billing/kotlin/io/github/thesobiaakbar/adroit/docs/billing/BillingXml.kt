package io.github.thesobiaakbar.adroit.docs.billing

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.PaywallCloseReason
import io.github.thesobiaakbar.adroit.billing.PaywallUiState
import io.github.thesobiaakbar.adroit.billing.PriceText
import io.github.thesobiaakbar.adroit.billing.RestoreResult
import io.github.thesobiaakbar.adroit.billing.views.AdroitPaywall
import io.github.thesobiaakbar.adroit.billing.views.PaywallLaunch
import io.github.thesobiaakbar.adroit.billing.views.PaywallView
import io.github.thesobiaakbar.adroit.docs.R
import kotlinx.coroutines.launch

// docs/guides/IAP_XML.md and ADS_IAP_XML.md, step 7.

class BillingMainActivity : AppCompatActivity() {
    // region billing-show-xml
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opens the paywall when its show rules allow it (Firebase can change them), once per screen, not after a rotation.
        if (savedInstanceState == null) AdroitPaywall.show(this, AppPaywalls.Onboarding, "onboarding")
    }

    override fun onResume() {
        super.onResume()
        // Play's messages, for example "your payment was declined": shows nothing when there's none.
        AdroitBilling.showInAppMessages(this)
    }
    // endregion
}

class BillingResultActivity : AppCompatActivity() {
    // region billing-contract-xml
    private val upgrade = registerForActivityResult(AdroitPaywall.contract()) { reason ->
        if (reason == PaywallCloseReason.PURCHASED) Toast.makeText(this, "Welcome to Premium!", Toast.LENGTH_LONG).show()
    }

    private fun onUpgradeClicked() {
        // The user asked: no show rules, so launch it directly.
        upgrade.launch(PaywallLaunch(AppPaywalls.Settings, "settings_button"))
    }
    // endregion
}

class CoinsShopFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // region billing-paywall-view-close
        view.findViewById<PaywallView>(R.id.coins_paywall).onClose = { parentFragmentManager.popBackStack() }
        // endregion
    }
}

// region billing-custom-xml
/** The coins paywall's controller, kept across rotations. */
class CoinsViewModel : ViewModel() {
    val store = AdroitBilling.paywalls.controller(AppPaywalls.Coins, "coins_screen", viewModelScope)
}

class CoinsFragment : Fragment(R.layout.snippet_coins) {
    private val viewModel: CoinsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val price = view.findViewById<TextView>(R.id.coins_price)
        val buy = view.findViewById<Button>(R.id.coins_buy)
        val store = viewModel.store
        buy.setOnClickListener { store.purchase(requireActivity()) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                store.state.collect { state ->
                    when (state) {
                        is PaywallUiState.Content -> {
                            price.text = PriceText.planPrice(resources, state.selectedPlan)
                            buy.text = PriceText.cta(resources, state.selectedPlan)
                            buy.isEnabled = state.busy == null
                        }
                        is PaywallUiState.Purchased -> price.text = "Thank you!"
                        is PaywallUiState.Loading, is PaywallUiState.Unavailable, is PaywallUiState.Closed -> buy.isEnabled = false
                    }
                }
            }
        }
        store.onShown() // on screen now: reported once per opening
    }
}
// endregion

class BillingSettingsFragment : Fragment() {
    // region billing-restore-xml
    private fun onRestoreClicked() {
        viewLifecycleOwner.lifecycleScope.launch {
            val message = when (val result = AdroitBilling.restore()) {
                is RestoreResult.Restored -> if (result.newlyActive.isEmpty()) "Your purchases are up to date" else "Restored"
                RestoreResult.NothingToRestore -> "Nothing to restore"
                is RestoreResult.Failed -> "Couldn't reach Google Play. Try again later."
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun onManageClicked() {
        // Play's subscription page; false when no app can open it.
        AdroitBilling.openManageSubscriptions(requireActivity(), AppProducts.PremiumSub)
    }

    private fun onUpgradeClicked() {
        // force = true: the user asked, so the show rules don't apply.
        AdroitPaywall.show(requireActivity(), AppPaywalls.Settings, "settings", force = true)
    }
    // endregion
}
