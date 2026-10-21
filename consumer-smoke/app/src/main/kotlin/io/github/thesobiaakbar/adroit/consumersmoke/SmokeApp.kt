package io.github.thesobiaakbar.adroit.consumersmoke

import android.app.Application
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.adMobConfig

internal val SmokeBanner = BannerPlacement("smoke_banner")

class SmokeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Adroit.initialize(
            this,
            adMobConfig {
                environment = AdEnvironment.DEVELOPMENT
                testing { useDemoAdUnits = true }
                placements {
                    // A placeholder, replaced by Google's demo banner unit (useDemoAdUnits).
                    banner(SmokeBanner, "ca-app-pub-0000000000000000/0000000000")
                }
            },
        )
        SmokeBilling.initialize(this)
    }
}
