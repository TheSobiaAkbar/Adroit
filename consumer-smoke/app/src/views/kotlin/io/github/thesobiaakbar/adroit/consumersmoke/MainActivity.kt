package io.github.thesobiaakbar.adroit.consumersmoke

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.views.attachBanner

/** The View-based host (`-Psmoke.compose=false`): depends on adroit-views only, with no Compose on the classpath. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this)
        setContentView(container)
        Adroit.start(this)
        checkForUpdate(this)
        container.attachBanner(this, SmokeBanner)
    }
}
