package com.company.admob.sample.mixed

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.company.admob.compose.AdBanner
import com.company.admob.config.bannerPlacement
import com.company.admob.sample.mixed.ads.SampleAds
import com.company.admob.sample.mixed.databinding.DetailsArticleBinding

/**
 * A Compose screen that still embeds an XML layout: the banner is a composable ([AdBanner]); the article and its native
 * ad come from `details_article.xml` through `AndroidView`, where the `AdNativeView` loads for the view tree's
 * lifecycle (this Activity). Home loads both ads ahead, so they show as soon as this screen opens.
 */
class DetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                            Text(stringResource(R.string.details_title), style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(8.dp))
                            // The XML part: its text and its AdNativeView (ArticleNative, large compact template).
                            AndroidView(
                                factory = { context -> DetailsArticleBinding.inflate(LayoutInflater.from(context)).root },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        AdBanner(SampleAds.DetailsBanner.bannerPlacement())
                    }
                }
            }
        }
    }
}
