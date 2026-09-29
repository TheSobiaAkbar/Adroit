package com.company.admob.sample.mixed.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.company.admob.AdsState
import com.company.admob.CompanyAdMob
import com.company.admob.UnavailableReason
import com.company.admob.sample.mixed.BuildConfig
import com.company.admob.sample.mixed.PremiumStore
import com.company.admob.sample.mixed.R
import com.company.admob.sample.mixed.databinding.FragmentSettingsBinding
import com.company.admob.sample.mixed.styles.NativeStylesActivity
import kotlinx.coroutines.launch

/**
 * - "Privacy options": shown only while UMP requires a privacy options entry point (`isPrivacyOptionsRequired`),
 *   which Google requires apps to offer in that case.
 * - "Ad-free": `setAdFree`, as an app would after a purchase, saved in [PremiumStore] so the next launch's splash
 *   treats the user as premium (no consent form, no ad, a 3-second splash).
 * - "Ad Inspector": Google's debugging tool; opens on test devices only. In the mediation build it lists the AppLovin
 *   adapter and its status (MEDIATION_DEMO.md).
 */
class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentSettingsBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSettingsBinding.bind(view)
        binding.privacyOptions.setOnClickListener {
            CompanyAdMob.privacy.showPrivacyOptions(requireActivity()) { error ->
                error?.let { toast(getString(R.string.privacy_options_error, it.message)) }
            }
        }
        // The library doesn't expose the ad-free flag; the state carries it (reported as an API gap in PROMPT 17).
        // Read once, before the listener is attached, so restoring the switch never calls setAdFree.
        binding.adFree.isChecked = CompanyAdMob.state.value == AdsState.Unavailable(UnavailableReason.AD_FREE_USER)
        binding.adFree.setOnCheckedChangeListener { _, checked ->
            CompanyAdMob.setAdFree(checked)
            PremiumStore.setPremium(requireContext(), checked)
        }
        binding.adInspector.setOnClickListener {
            CompanyAdMob.diagnostics.openAdInspector(requireActivity()) { error ->
                error?.let { toast(getString(R.string.ad_inspector_error, it.message)) }
            }
        }
        binding.mediation.setText(if (BuildConfig.WITH_MEDIATION) R.string.mediation_on else R.string.mediation_off)
        binding.nativeStyles.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), NativeStylesActivity::class.java))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                CompanyAdMob.privacy.isPrivacyOptionsRequired.collect { binding.privacyOptions.isVisible = it }
            }
        }
    }

    private fun toast(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
    }
}
