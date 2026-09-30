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
import io.github.thesobiaakbar.adroit.sample.xml.databinding.ActivityMainBinding
import io.github.thesobiaakbar.adroit.sample.xml.feed.FeedFragment
import io.github.thesobiaakbar.adroit.sample.xml.home.HomeFragment
import io.github.thesobiaakbar.adroit.sample.xml.settings.SettingsFragment

/** Bottom navigation over three Fragments. Each Fragment's ads follow its view lifecycle (`viewLifecycleOwner`). */
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
            show(
                when (item.itemId) {
                    R.id.nav_feed -> FeedFragment()
                    R.id.nav_settings -> SettingsFragment()
                    else -> HomeFragment()
                },
            )
            true
        }
        if (savedInstanceState == null) show(HomeFragment())
        // Ads load only when asked: the app open ad now, for the user's first return to the app. It has loadAgain,
        // so each later return finds a new one. Nothing happens if it's loaded already.
        AdSlots.load(SampleAds.AppOpenAd)
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.commit { replace(R.id.fragment_container, fragment) }
    }
}
