package io.github.thesobiaakbar.adroit.docs.guides.ads.xml

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.thesobiaakbar.adroit.AdSlots
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.RewardResult
import io.github.thesobiaakbar.adroit.config.rewardedPlacement
import io.github.thesobiaakbar.adroit.docs.R
import io.github.thesobiaakbar.adroit.docs.guides.AppAds
import io.github.thesobiaakbar.adroit.docs.guides.offerDownloadedUpdate
import kotlinx.coroutines.launch

// The main screen of docs/guides/ADS_XML.md and ADS_IAP_XML.md, step 7.

// region guide-ads-main-xml
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Interstitial: the callback runs whether an ad showed or not. Always continue the app's flow in it.
        findViewById<Button>(R.id.level_complete).setOnClickListener {
            AdSlots.showFullScreen(this, AppAds.LevelCompleteAd) { openNextLevel() }
        }

        // Rewarded: give the reward only for Earned.
        findViewById<Button>(R.id.extra_life).setOnClickListener {
            Adroit.showRewarded(this, AppAds.ExtraLifeRewarded.rewardedPlacement()) { result ->
                when (result) {
                    is RewardResult.Earned -> giveLives(result.reward.amount)
                    RewardResult.NotEarned -> toast("Watch the whole ad to get the reward")
                    is RewardResult.Failed -> toast("No ad right now, try again later")
                }
            }
        }

        // Privacy options: Google requires a way to change consent, where consent applies.
        val privacy = findViewById<Button>(R.id.privacy_options)
        privacy.setOnClickListener { Adroit.privacy.showPrivacyOptions(this) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Adroit.privacy.isPrivacyOptionsRequired.collect { privacy.isVisible = it }
            }
        }

        // A flexible update downloaded in the background: offer "Restart".
        offerDownloadedUpdate(findViewById<View>(R.id.root))

        // The app open ad shows by itself when the user comes back to the app. Load it once.
        AdSlots.load(AppAds.AppOpenAd)
    }

    override fun onResume() {
        super.onResume()
        // Ads load only when asked: this screen's full-screen ads, so they're ready when a button is tapped, and the
        // next screen's banner, so it shows the moment that screen opens. Nothing happens if one is already loaded.
        AdSlots.load(AppAds.LevelCompleteAd)
        AdSlots.load(AppAds.ExtraLifeRewarded)
        AdSlots.load(AppAds.DetailsBanner)
    }

    private fun openNextLevel() = Unit // your app's next screen

    private fun giveLives(amount: Int) = Unit // your app's reward

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
// endregion
