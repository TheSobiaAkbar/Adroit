package io.github.thesobiaakbar.adroit.sample.xml.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.thesobiaakbar.adroit.sample.xml.DetailsActivity
import io.github.thesobiaakbar.adroit.sample.xml.R
import io.github.thesobiaakbar.adroit.sample.xml.ads.SampleAds
import io.github.thesobiaakbar.adroit.sample.xml.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

/**
 * The home banner is declared in XML (`AdSlotView`, a banner or a native ad as Firebase says): it binds itself to this Fragment's view lifecycle, so it's
 * destroyed in `onDestroyView` even though the Fragment stays on the back stack. The buttons show full-screen ads
 * through [CoinsViewModel].
 */
class HomeFragment : Fragment() {
    private var binding: FragmentHomeBinding? = null
    private val coins: CoinsViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentHomeBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = checkNotNull(binding)
        b.levelComplete.setOnClickListener { coins.onLevelComplete(requireActivity()) }
        b.counterAd.setOnClickListener { coins.onStrategyDemo(requireActivity(), SampleAds.CounterDemoAd) }
        b.timerAd.setOnClickListener { coins.onStrategyDemo(requireActivity(), SampleAds.TimerDemoAd) }
        b.extraLife.setOnClickListener { coins.onExtraLife(requireActivity()) }
        b.bonusRound.setOnClickListener { BonusIntroDialogFragment().show(childFragmentManager, BonusIntroDialogFragment.TAG) }
        // A new banner loads in the background; the shown one stays until it's loaded, and stays if it fails.
        b.refreshBanner.setOnClickListener { b.homeBanner.load(refresh = true) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { coins.coins.collect { b.coins.text = getString(R.string.coins, it) } }
                launch { coins.strategyStatus.collect { b.strategyStatus.text = it } }
                launch { coins.openDetails.collect { startActivity(Intent(requireContext(), DetailsActivity::class.java)) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        coins.loadAds()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
