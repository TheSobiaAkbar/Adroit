package com.company.admob.consumersmoke

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import com.company.admob.BannerPlacement
import com.company.admob.CompanyAdMob
import com.company.admob.compose.AdBanner
import com.company.admob.config.AdEnvironment
import com.company.admob.config.adMobConfig

private val SmokeBanner = BannerPlacement("smoke_banner")

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CompanyAdMob.start(this)
        setContent {
            Column {
                BasicText("CompanyAdMob consumer smoke")
                AdBanner(SmokeBanner, Modifier.fillMaxWidth())
            }
        }
    }
}
