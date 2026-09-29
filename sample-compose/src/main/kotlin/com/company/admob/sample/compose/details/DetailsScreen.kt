package com.company.admob.sample.compose.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.company.admob.compose.AdBanner
import com.company.admob.compose.AdNative
import com.company.admob.config.bannerPlacement
import com.company.admob.config.nativePlacement
import com.company.admob.sample.compose.R
import com.company.admob.sample.compose.ads.SampleAds

/**
 * A banner ([AdBanner], destroyed when this destination leaves the screen) and a native ad ([AdNative], kept across
 * rotation by this destination and destroyed when it's popped). Their looks come from SampleAds: a collapsible
 * banner and the large compact native template. Home loads both ahead, so they show as soon as this screen opens.
 */
@Composable
fun DetailsScreen() {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.details_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.details_body))
            Spacer(Modifier.height(16.dp))
            AdNative(SampleAds.ArticleNative.nativePlacement(), Modifier.fillMaxWidth())
        }
        AdBanner(SampleAds.DetailsBanner.bannerPlacement())
    }
}
