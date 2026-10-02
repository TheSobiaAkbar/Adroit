package io.github.thesobiaakbar.adroit.sample.compose.details

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
import io.github.thesobiaakbar.adroit.compose.AdSlot
import io.github.thesobiaakbar.adroit.sample.compose.R
import io.github.thesobiaakbar.adroit.sample.compose.ads.SampleAds

/**
 * A banner and a native ad, each an [AdSlot]: it shows a banner or a native ad, whichever the ad is in SampleAds (or
 * Firebase), so the code never has to match its format. Both are kept across rotation by this destination and
 * destroyed when it's popped. Their looks come from SampleAds: a collapsible
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
            AdSlot(SampleAds.ArticleNative, Modifier.fillMaxWidth())
        }
        AdSlot(SampleAds.DetailsBanner)
    }
}
