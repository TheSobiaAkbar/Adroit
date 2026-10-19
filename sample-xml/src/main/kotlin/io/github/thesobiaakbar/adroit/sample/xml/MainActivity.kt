package io.github.thesobiaakbar.adroit.sample.xml

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import io.github.thesobiaakbar.adroit.AdSlots
import io.github.thesobiaakbar.adroit.sample.xml.ads.SampleAds
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.views.AdroitPaywall
import io.github.thesobiaakbar.adroit.sample.xml.billing.SamplePaywalls
import io.github.thesobiaakbar.adroit.sample.xml.databinding.ActivityMainBinding
import io.github.thesobiaakbar.adroit.sample.xml.feed.FeedFragment
import io.github.thesobiaakbar.adroit.sample.xml.home.HomeFragment
import io.github.thesobiaakbar.adroit.sample.xml.settings.SettingsFragment

/**
 * Bottom navigation over three Fragments. Like Navigation's tabs, a tab keeps its Fragment (and its ViewModels) while
 * another one shows: only its views go. Its ads are kept by the Fragment, so coming back shows the same ones.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            show(item.itemId)
            true
        }
        if (savedInstanceState == null) {
            show(R.id.nav_home)
            // Right after the splash: the onboarding paywall, when its show rules allow (once per session, never
            // to a premium user). Firebase's Onboarding key changes it on the next launch.
            AdroitPaywall.show(this, SamplePaywalls.Onboarding, "onboarding")
        }
        // Ads load only when asked: the app open ad now, for the user's first return to the app. It has loadAgain,
        // so each later return finds a new one. Nothing happens if it's loaded already.
        AdSlots.load(SampleAds.AppOpenAd)
    }

    override fun onResume() {
        super.onResume()
        // Play's messages about the user's subscriptions, for example a declined payment.
        AdroitBilling.showInAppMessages(this)
    }

    /** Shows [tab]'s Fragment, creating it the first time; the shown one is detached (its views go, it stays). */
    private fun show(tab: Int) {
        val tag = "tab_$tab"
        val fragments = supportFragmentManager
        if (fragments.findFragmentByTag(tag)?.isDetached == false) return
        fragments.commit {
            setReorderingAllowed(true)
            fragments.fragments.filter { it.id == R.id.fragment_container && !it.isDetached }.forEach { detach(it) }
            fragments.findFragmentByTag(tag)?.let { attach(it) } ?: add(R.id.fragment_container, newTab(tab), tag)
        }
    }

    private fun newTab(tab: Int): Fragment = when (tab) {
        R.id.nav_feed -> FeedFragment()
        R.id.nav_settings -> SettingsFragment()
        else -> HomeFragment()
    }
}
