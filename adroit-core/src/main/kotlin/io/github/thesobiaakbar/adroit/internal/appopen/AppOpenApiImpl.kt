package io.github.thesobiaakbar.adroit.internal.appopen

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import io.github.thesobiaakbar.adroit.AdAvailability
import io.github.thesobiaakbar.adroit.AppOpenApi
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.internal.threading.runOnMain
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/** [AppOpenApi] over [AppOpenController]. Callable from any thread; the controller runs on the main thread. */
internal class AppOpenApiImpl(private val controller: AppOpenController) : AppOpenApi {
    override val availability: StateFlow<AdAvailability> get() = controller.availability()

    override fun showOnColdStart(activity: Activity, maxWait: Duration, onResult: (FullScreenResult) -> Unit) =
        runOnMain { controller.showOnColdStart(activity, maxWait, onResult) }

    override fun skipNextForeground() = runOnMain { controller.skipNextForeground() }

    override fun suppressWhile(owner: LifecycleOwner) = runOnMain { controller.suppressWhile(owner) }

    override fun setEnabled(enabled: Boolean) = runOnMain { controller.setEnabled(enabled) }
}
