package com.company.admob.sample.xml

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.company.admob.CompanyAdMob
import com.company.admob.FullScreenResult
import com.company.admob.sample.xml.ads.Placements
import com.company.admob.sample.xml.databinding.ActivitySplashBinding
import kotlin.time.Duration.Companion.seconds

/**
 * The launcher. Gathers consent and initializes the SDK (`start`), then shows the splash interstitial if it's
 * turned on (`SampleAds.SplashAd`; off by default), otherwise offers a cold-start
 * app open ad for up to 4 seconds. It continues to [MainActivity] whatever happened: consent refused, no ad,
 * offline, or ad dismissed. Excluded from automatic app open ads in the ads file.
 *
 * A loading screen by design (hence the lint suppression): it hosts consent and the cold-start app open ad, which
 * the system splash screen can't show. The sample doesn't add the SplashScreen library for this.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivitySplashBinding.inflate(layoutInflater).root)

        // Called again after a rotation: start() joins the work in progress, and the new Activity waits again.
        CompanyAdMob.start(this) {
            if (isFinishing || isDestroyed) return@start
            CompanyAdMob.interstitial.showOnSplash(this, Placements.SplashInterstitial) { result ->
                when {
                    result is FullScreenResult.Dismissed -> goToMain()
                    isFinishing || isDestroyed -> Unit
                    else -> CompanyAdMob.appOpen.showOnColdStart(this, maxWait = 4.seconds) { goToMain() }
                }
            }
        }
    }

    private fun goToMain() {
        if (navigated || isFinishing || isDestroyed) return
        navigated = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
