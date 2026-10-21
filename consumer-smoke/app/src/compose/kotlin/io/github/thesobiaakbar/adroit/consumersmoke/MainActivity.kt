package io.github.thesobiaakbar.adroit.consumersmoke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.compose.AdBanner

/** The Compose host (the default, `-Psmoke.compose=true`): depends on adroit-compose. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Adroit.start(this)
        checkForUpdate(this)
        setContent {
            Column {
                BasicText("Adroit consumer smoke")
                AdBanner(SmokeBanner, Modifier.fillMaxWidth())
            }
        }
    }
}
