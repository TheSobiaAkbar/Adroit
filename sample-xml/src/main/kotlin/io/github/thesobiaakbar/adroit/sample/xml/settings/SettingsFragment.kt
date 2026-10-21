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
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.EntitlementSnapshot
import io.github.thesobiaakbar.adroit.billing.RestoreResult
import io.github.thesobiaakbar.adroit.billing.SnapshotStatus
import io.github.thesobiaakbar.adroit.billing.views.AdroitPaywall
import io.github.thesobiaakbar.adroit.firebase.RemoteConfigSession
import io.github.thesobiaakbar.adroit.sample.xml.BuildConfig
import io.github.thesobiaakbar.adroit.sample.xml.R
import io.github.thesobiaakbar.adroit.sample.xml.billing.DemoMode
import io.github.thesobiaakbar.adroit.sample.xml.billing.SamplePaywalls
import io.github.thesobiaakbar.adroit.sample.xml.billing.SampleProducts
import io.github.thesobiaakbar.adroit.sample.xml.databinding.FragmentSettingsBinding
import io.github.thesobiaakbar.adroit.sample.xml.styles.NativeStylesActivity
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/**
 * - "Privacy options": shown only while UMP requires a privacy options entry point (`isPrivacyOptionsRequired`),
 *   which Google requires apps to offer in that case.
 * - Premium: the entitlement and how sure it is (Fresh from Play, Cached while offline, Unknown), "Upgrade" (the
 *   `SettingsUpgrade` paywall, forced: the user asked), "Restore purchases" and "Manage subscription". Premium comes
 *   only from `AdroitBilling.entitlements`; `SampleApplication` turns it into `Adroit.setAdFree`.
 * - "Demo billing" (debug builds): made-up products instead of Play, from the next launch (`DemoMode`).
 * - Debug builds: the active Firebase `AdSettings.splashAdMode` and `AppUpdate.type` (docs/SPLASH.md, APP_UPDATE.md).
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
        binding.upgrade.setOnClickListener {
            AdroitPaywall.show(requireActivity(), SamplePaywalls.SettingsUpgrade, "settings", force = true)
        }
        binding.restore.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                toast(
                    when (val result = AdroitBilling.restore()) {
                        is RestoreResult.Restored -> if (result.newlyActive.isEmpty()) getString(R.string.restore_up_to_date)
                        else getString(R.string.restore_done, result.newlyActive.joinToString())
                        RestoreResult.NothingToRestore -> getString(R.string.restore_nothing)
                        is RestoreResult.Failed -> getString(R.string.restore_failed, result.error.debugMessage ?: result.error.toString())
                    },
                )
            }
        }
        binding.manage.setOnClickListener {
            AdroitBilling.openManageSubscriptions(requireActivity(), SampleProducts.PremiumSub)
        }
        binding.demoBilling.isVisible = BuildConfig.DEBUG
        binding.demoBilling.isChecked = DemoMode.isOn(requireContext())
        binding.demoBilling.setOnCheckedChangeListener { _, checked ->
            DemoMode.set(requireContext(), checked)
            toast(getString(R.string.demo_billing_restart))
        }
        binding.firebaseModes.isVisible = BuildConfig.DEBUG
        binding.firebaseModes.text = getString(
            R.string.firebase_modes,
            activeValue("AdSettings", "splashAdMode", "show"),
            activeValue("AppUpdate", "type", "flexible"),
        )
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
                launch { Adroit.privacy.isPrivacyOptionsRequired.collect { binding.privacyOptions.isVisible = it } }
                launch { AdroitBilling.entitlements.collect { binding.premiumStatus.text = status(it) } }
            }
        }
    }

    private fun status(snapshot: EntitlementSnapshot): String = getString(
        R.string.premium_status,
        getString(if (snapshot.has("premium")) R.string.premium_yes else R.string.premium_no),
        when (snapshot.status) {
            is SnapshotStatus.Fresh -> "Fresh"
            is SnapshotStatus.Cached -> "Cached"
            SnapshotStatus.Unknown -> "Unknown"
        },
    )

    /** [field] of the active Firebase JSON parameter [key], or [default] when it isn't set. */
    private fun activeValue(key: String, field: String, default: String): String = try {
        RemoteConfigSession.startDefault()?.string(key)?.let { JSONObject(it).optString(field) }?.ifBlank { null } ?: default
    } catch (e: JSONException) {
        "invalid"
    }

    private fun toast(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
    }
}
