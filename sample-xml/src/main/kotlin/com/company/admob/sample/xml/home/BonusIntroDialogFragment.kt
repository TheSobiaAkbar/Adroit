package com.company.admob.sample.xml.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.company.admob.sample.xml.R
import com.company.admob.sample.xml.databinding.DialogBonusIntroBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The intro screen Google requires before a rewarded interstitial
 * (https://developers.google.com/admob/android/next-gen/rewarded-interstitial): it announces the reward and gives the
 * user a way to opt out ("No thanks") before the ad starts, after a 5-second countdown.
 */
class BonusIntroDialogFragment : DialogFragment() {
    private val coins: CoinsViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        DialogBonusIntroBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = DialogBonusIntroBinding.bind(view)
        binding.noThanks.setOnClickListener { dismiss() }
        viewLifecycleOwner.lifecycleScope.launch {
            for (remaining in COUNTDOWN_SECONDS downTo 1) {
                binding.countdown.text = getString(R.string.bonus_countdown, remaining)
                delay(1_000)
            }
            coins.onBonusAccepted(requireActivity())
            dismiss()
        }
    }

    companion object {
        const val TAG: String = "bonus_intro"
        private const val COUNTDOWN_SECONDS = 5
    }
}
