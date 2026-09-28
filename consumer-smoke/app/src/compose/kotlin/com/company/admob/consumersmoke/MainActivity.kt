package com.company.admob.consumersmoke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import com.company.admob.CompanyAdMob
import com.company.admob.compose.AdBanner

/** The Compose host (the default, `-Psmoke.compose=true`): depends on admob-compose. */
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
