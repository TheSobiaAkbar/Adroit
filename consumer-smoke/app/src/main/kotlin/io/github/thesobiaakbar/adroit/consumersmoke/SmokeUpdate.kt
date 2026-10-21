package io.github.thesobiaakbar.adroit.consumersmoke

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.thesobiaakbar.adroit.update.AdroitUpdate
import kotlinx.coroutines.launch

/** adroit-update from mavenLocal: one real call, so R8 keeps and shrinks the published artifact in a release build. */
internal fun checkForUpdate(activity: ComponentActivity) {
    activity.lifecycleScope.launch { AdroitUpdate.check(activity) }
}
