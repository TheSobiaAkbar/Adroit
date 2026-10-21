package io.github.thesobiaakbar.adroit.docs.guides

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thesobiaakbar.adroit.splash.SplashState

// The splash look shared by the Compose guides in docs/guides/, step 6.

// region guide-splash-screen-compose
/** The splash's look: the app's name, and the timer's progress (0..100) once there is one. */
@Composable
fun SplashScreen(percent: Int?) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Text("MyApp", Modifier.align(Alignment.Center), style = MaterialTheme.typography.headlineMedium)
            if (percent != null) {
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(start = 48.dp, end = 48.dp, bottom = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("$percent%")
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The progress bar for a [SplashState]: none while the update check and consent run, then 0..100. */
fun SplashState.percent(): Int? = when (this) {
    SplashState.Preparing -> null
    is SplashState.Loading -> progress
    SplashState.ShowingAd, is SplashState.Finished -> 100
}
// endregion
