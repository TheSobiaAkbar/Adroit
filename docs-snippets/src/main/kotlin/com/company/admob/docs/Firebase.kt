@file:Suppress("unused")

package com.company.admob.docs

import android.app.Application
import com.company.admob.CompanyAdMob
import com.company.admob.config.AdEnvironment
import com.company.admob.config.adMobConfig
import com.company.admob.firebase.FirebaseAdEvent
import com.company.admob.firebase.FirebaseAnalyticsEventListener
import com.company.admob.firebase.FirebaseAnalyticsOptions
import com.company.admob.firebase.FirebaseRemoteConfigProvider

// region firebase-setup
class FirebaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase itself is initialized by the google-services plugin, before this runs.
        CompanyAdMob.initialize(
            this,
            adMobConfig {
                environment = AdEnvironment.PRODUCTION
                remoteConfig = FirebaseRemoteConfigProvider() // reads the company_admob_* keys
                eventListeners += FirebaseAnalyticsEventListener(
                    context = this@FirebaseApp,
                    options = FirebaseAnalyticsOptions(
                        logAdImpressionRevenue = true, // false if AdMob is linked to Firebase (see below)
                        customEvents = setOf(FirebaseAdEvent.CLICK, FirebaseAdEvent.REWARD),
                    ),
                )
                placements { /* … */ }
            },
        )
    }
}
// endregion
