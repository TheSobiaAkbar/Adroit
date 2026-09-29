package com.company.admob.sample.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.company.admob.AdSlots
import com.company.admob.sample.compose.ads.SampleAds
import com.company.admob.sample.compose.ui.SampleApp
import com.company.admob.sample.compose.ui.SampleTheme

/**
 * Every screen after the splash, as navigation destinations ([SampleApp]): Home, Feed and Settings in a bottom bar,
 * then Details and Native styles. Each destination's banners and native ads follow its own lifecycle.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SampleTheme { SampleApp() } }
        // Ads load only when asked: the app open ad now, for the user's first return to the app. It has loadAgain,
        // so each later return finds a new one. Nothing happens if it's loaded already.
        AdSlots.load(SampleAds.AppOpenAd)
    }
}
