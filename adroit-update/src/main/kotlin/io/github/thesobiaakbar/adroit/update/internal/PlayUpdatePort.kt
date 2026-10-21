package io.github.thesobiaakbar.adroit.update.internal

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [UpdatePort] over Play's `AppUpdateManager`. */
internal class PlayUpdatePort(context: Context) : UpdatePort {
    private val manager = AppUpdateManagerFactory.create(context.applicationContext)

    override suspend fun info(): UpdateInfo {
        val info = manager.appUpdateInfo.await()
        val availability = info.updateAvailability()
        return UpdateInfo(
            available = availability == UpdateAvailability.UPDATE_AVAILABLE,
            inProgress = availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
            immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
            flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            downloaded = info.installStatus() == InstallStatus.DOWNLOADED,
            handle = info,
        )
    }

    override suspend fun startFlow(info: UpdateInfo, activity: Activity, immediate: Boolean): Int {
        val type = if (immediate) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
        // Each AppUpdateInfo is good for one start: every check gets a fresh one.
        val options = AppUpdateOptions.defaultOptions(type)
        return manager.startUpdateFlow(info.handle as AppUpdateInfo, activity, options).await()
    }

    override fun listen(onProgress: (InstallProgress) -> Unit): () -> Unit {
        val listener = InstallStateUpdatedListener { state ->
            val progress = when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> InstallProgress.DOWNLOADED
                InstallStatus.INSTALLED, InstallStatus.FAILED, InstallStatus.CANCELED -> InstallProgress.ENDED
                else -> InstallProgress.OTHER
            }
            onProgress(progress)
        }
        manager.registerListener(listener)
        return { manager.unregisterListener(listener) }
    }

    override fun complete() {
        manager.completeUpdate().addOnFailureListener { logWarning("Installing the update failed: ${it.message}") }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(error ?: IllegalStateException("Play task failed"))
        }
    }
}

internal fun logWarning(message: String) {
    Log.w(TAG, message)
}

private const val TAG = "AdroitUpdate"
