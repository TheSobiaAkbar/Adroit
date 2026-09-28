package com.company.admob.consumersmoke

import android.app.Application
import com.company.admob.BannerPlacement
import com.company.admob.CompanyAdMob
import com.company.admob.config.AdEnvironment
import com.company.admob.config.adMobConfig

internal val SmokeBanner = BannerPlacement("smoke_banner")

class SmokeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CompanyAdMob.initialize(
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
    }
}
