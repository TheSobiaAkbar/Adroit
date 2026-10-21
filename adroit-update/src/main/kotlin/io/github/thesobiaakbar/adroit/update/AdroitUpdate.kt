package io.github.thesobiaakbar.adroit.update

import android.app.Activity
import android.content.Context
import android.os.Build
import io.github.thesobiaakbar.adroit.firebase.RemoteConfigSession
import io.github.thesobiaakbar.adroit.update.internal.PlayUpdatePort
import io.github.thesobiaakbar.adroit.update.internal.UpdateCoordinator
import io.github.thesobiaakbar.adroit.update.internal.logWarning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Google Play in-app updates, controlled from the Firebase `AppUpdate` parameter (docs/APP_UPDATE.md):
 *
 * ```kotlin
 * lifecycleScope.launch {
 *     AdroitUpdate.check(this@SplashActivity)
 *     viewModel.flow.start(this@SplashActivity)
 * }
 * ```
 *
 * Main thread. Without Google Play (a sideloaded or debug build, an emulator without Play, offline), [check] returns
 * at once.
 */
public object AdroitUpdate {
    /** The Firebase Remote Config parameter. */
    public const val KEY: String = "AppUpdate"

    private val ready = MutableStateFlow(false)
    private var coordinator: UpdateCoordinator? = null

    /** True once a flexible update finished downloading: offer "Restart to update", which calls [install]. */
    @JvmStatic
    public val readyToInstall: StateFlow<Boolean> = ready.asStateFlow()

    /**
     * Checks Google Play for a newer version and offers it as Firebase `AppUpdate` says, or as [default] without
     * Firebase or before its first fetch. When Play has an update, waits up to 2 seconds for this session's Remote
     * Config fetch; with none, returns as soon as Play answers.
     *
     * Returns when the app may continue: no update, a flexible prompt the user answered either way, or a forced update
     * that couldn't start. Never returns when the user backs out of a forced update: the app closes
     * (`finishAffinity`), and the next launch asks again. [activity] is used only during the call.
     */
    public suspend fun check(activity: Activity, default: UpdateType = UpdateType.FLEXIBLE) {
        coordinatorFor(activity.applicationContext).check(activity, default)
    }

    /** Installs a downloaded flexible update ([readyToInstall]); Google Play restarts the app. No-op before that. */
    @JvmStatic
    public fun install() {
        coordinator?.install()
    }

    private fun coordinatorFor(context: Context): UpdateCoordinator = coordinator ?: UpdateCoordinator(
        port = PlayUpdatePort(context),
        readConfig = { RemoteConfigSession.startDefault()?.string(KEY) },
        awaitFetched = { RemoteConfigSession.startDefault()?.isFetched?.first { it } },
        installedVersionCode = installedVersionCode(context),
        ready = ready,
        warn = ::logWarning,
    ).also { coordinator = it }

    @Suppress("DEPRECATION") // versionCode below API 28.
    private fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }
}
