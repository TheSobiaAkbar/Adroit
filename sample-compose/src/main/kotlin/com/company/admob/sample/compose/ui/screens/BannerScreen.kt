package com.company.admob.sample.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.company.admob.BannerOptions
import com.company.admob.BannerPlacement
import com.company.admob.BannerSize
import com.company.admob.BannerState
import com.company.admob.compose.AdBanner
import com.company.admob.sample.compose.ads.Placements
import com.company.admob.sample.compose.ui.components.Hint
import com.company.admob.sample.compose.ui.components.LabeledValue
import com.company.admob.sample.compose.ui.components.ScreenColumn
import com.company.admob.sample.compose.ui.components.Section
import com.company.admob.sample.compose.ui.components.label

@Composable
fun BannerScreen() {
    var showBanners by rememberSaveable { mutableStateOf(true) }
    var recompositions by remember { mutableIntStateOf(0) }

    ScreenColumn {
        // The performance audit's recomposition check (docs/audits/PERFORMANCE_AUDIT.md, measurement 6): each tap
        // recomposes this screen, and the banners below must skip.
        OutlinedButton(onClick = { recompositions++ }, Modifier.testTag("recompose")) {
            Text("Recompose screen ($recompositions)")
        }
        Section("Disposal") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Show banners", Modifier.weight(1f))
                Switch(
                    checked = showBanners,
                    onCheckedChange = { showBanners = it },
                    modifier = Modifier.testTag("toggle_banners"),
                )
            }
            Hint(
                "Turning this off removes the banners from the composition: each one is destroyed (a Destroyed " +
                    "event on the Events screen). Turning it on creates new ones. A banner belongs to its Activity, so a " +
                    "rotation replaces each one: the old one is destroyed, never duplicated.",
            )
        }
        BannerDemo("Anchored adaptive", Placements.AnchoredBanner, BannerOptions(BannerSize.AnchoredAdaptive), showBanners)
        BannerDemo(
            "Inline adaptive (max 250 dp)",
            Placements.InlineBanner,
            BannerOptions(BannerSize.InlineAdaptive(maxHeightDp = 250)),
            showBanners,
        )
        BannerDemo("Medium rectangle", Placements.RectangleBanner, BannerOptions(BannerSize.MediumRectangle), showBanners)
    }
}

@Composable
private fun BannerDemo(title: String, placement: BannerPlacement, options: BannerOptions, show: Boolean) {
    // Not saved: a new banner after the toggle, or after a rotation, reports its own states.
    var state by remember { mutableStateOf<BannerState?>(null) }
    Section(title) {
        LabeledValue("State", if (show) state.label() else "removed")
        if (show) {
            AdBanner(
                placement = placement,
                modifier = Modifier.fillMaxWidth(),
                options = options,
                onStateChange = { state = it },
            )
        }
    }
}
