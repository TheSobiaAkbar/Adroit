package io.github.thesobiaakbar.adroit.docs.guides

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import io.github.thesobiaakbar.adroit.update.AdroitUpdate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// The update snackbar of every guide in docs/guides/, step 7.

// region guide-update-xml
/** Call from the main Activity's onCreate: once a flexible update has downloaded, offer the restart that installs it. */
fun AppCompatActivity.offerDownloadedUpdate(root: View) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            AdroitUpdate.readyToInstall.first { it }
            Snackbar.make(root, "An update is ready", Snackbar.LENGTH_INDEFINITE)
                .setAction("Restart") { AdroitUpdate.install() }
                .show()
        }
    }
}
// endregion

// region guide-update-compose
/** In the main screen, with the Scaffold's snackbar host: once a flexible update has downloaded, offers "Restart". */
@Composable
fun UpdateSnackbar(snackbars: SnackbarHostState) {
    LaunchedEffect(snackbars) {
        AdroitUpdate.readyToInstall.first { it }
        val result = snackbars.showSnackbar("An update is ready", actionLabel = "Restart", duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) AdroitUpdate.install()
    }
}
// endregion
