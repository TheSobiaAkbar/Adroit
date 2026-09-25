package com.company.admob.internal.appopen

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import com.company.admob.AdAvailability
import com.company.admob.AppOpenApi
import com.company.admob.FullScreenResult
import com.company.admob.internal.threading.runOnMain
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/** [AppOpenApi] over [AppOpenController]. Callable from any thread; the controller runs on the main thread. */
internal class AppOpenApiImpl(private val controller: AppOpenController) : AppOpenApi {
    override val availability: StateFlow<AdAvailability> get() = controller.availability()

    override fun showOnColdStart(activity: Activity, maxWait: Duration, onComplete: (FullScreenResult) -> Unit) =
        runOnMain { controller.showOnColdStart(activity, maxWait, onComplete) }

    override fun skipNextForeground() = runOnMain { controller.skipNextForeground() }

    override fun suppressWhile(owner: LifecycleOwner) = runOnMain { controller.suppressWhile(owner) }

    override fun setEnabled(enabled: Boolean) = runOnMain { controller.setEnabled(enabled) }
}
