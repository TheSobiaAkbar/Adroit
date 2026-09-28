package com.company.admob.consumersmoke

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.company.admob.CompanyAdMob
import com.company.admob.views.attachBanner

/** The View-based host (`-Psmoke.compose=false`): depends on admob-views only, with no Compose on the classpath. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this)
        setContentView(container)
        CompanyAdMob.start(this)
        container.attachBanner(this, SmokeBanner)
    }
}
