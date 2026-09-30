@file:Suppress("unused")

package io.github.thesobiaakbar.adroit.docs

import android.app.Application
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.adMobConfig
import io.github.thesobiaakbar.adroit.firebase.FirebaseAdEvent
import io.github.thesobiaakbar.adroit.firebase.FirebaseAnalyticsEventListener
import io.github.thesobiaakbar.adroit.firebase.FirebaseAnalyticsOptions
import io.github.thesobiaakbar.adroit.firebase.FirebaseRemoteConfigProvider

// region firebase-setup
class FirebaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase itself is initialized by the google-services plugin, before this runs.
        Adroit.initialize(
            this,
            adMobConfig {
                environment = AdEnvironment.PRODUCTION
                remoteConfig = FirebaseRemoteConfigProvider() // one parameter per ad, plus AdSettings
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
