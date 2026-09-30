package io.github.thesobiaakbar.adroit.sample.xml.settings

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
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.UnavailableReason
import io.github.thesobiaakbar.adroit.sample.xml.PremiumStore
import io.github.thesobiaakbar.adroit.sample.xml.R
import io.github.thesobiaakbar.adroit.sample.xml.databinding.FragmentSettingsBinding
import io.github.thesobiaakbar.adroit.sample.xml.styles.NativeStylesActivity
import kotlinx.coroutines.launch

/**
 * - "Privacy options": shown only while UMP requires a privacy options entry point (`isPrivacyOptionsRequired`),
 *   which Google requires apps to offer in that case.
 * - "Ad-free": `setAdFree`, as an app would after a purchase, saved in [PremiumStore] so the next launch's splash
 *   treats the user as premium (no consent form, no ad, a 3-second splash).
 * - "Ad Inspector": Google's debugging tool; opens on test devices only.
 */
class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentSettingsBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSettingsBinding.bind(view)
        binding.privacyOptions.setOnClickListener {
            Adroit.privacy.showPrivacyOptions(requireActivity()) { error ->
                error?.let { toast(getString(R.string.privacy_options_error, it.message)) }
            }
        }
        // The library doesn't expose the ad-free flag; the state carries it (reported as an API gap in PROMPT 17).
        // Read once, before the listener is attached, so restoring the switch never calls setAdFree.
        binding.adFree.isChecked = Adroit.state.value == AdsState.Unavailable(UnavailableReason.AD_FREE_USER)
        binding.adFree.setOnCheckedChangeListener { _, checked ->
            Adroit.setAdFree(checked)
            PremiumStore.setPremium(requireContext(), checked)
        }
        binding.adInspector.setOnClickListener {
            Adroit.diagnostics.openAdInspector(requireActivity()) { error ->
                error?.let { toast(getString(R.string.ad_inspector_error, it.message)) }
            }
        }
        binding.nativeStyles.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), NativeStylesActivity::class.java))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Adroit.privacy.isPrivacyOptionsRequired.collect { binding.privacyOptions.isVisible = it }
            }
        }
    }

    private fun toast(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
    }
}
