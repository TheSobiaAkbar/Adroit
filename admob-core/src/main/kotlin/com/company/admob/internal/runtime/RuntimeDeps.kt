package com.company.admob.internal.runtime

import android.app.Application
import android.content.pm.ApplicationInfo
import com.company.admob.internal.config.ManifestAppIdReader
import com.company.admob.internal.google.NextGenAdsSdk
import com.company.admob.internal.google.NextGenNativeAdBinder
import com.company.admob.internal.google.UmpConsentSdk
import com.company.admob.internal.lifecycle.LifecycleActivityTracker
import com.company.admob.internal.lifecycle.ProcessLifecycleForegroundSource
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.ActivityTracker
import com.company.admob.internal.port.AdsSdk
import com.company.admob.internal.port.AppForegroundSource
import com.company.admob.internal.port.ConsentSdk
import com.company.admob.internal.port.KeyValueStore
import com.company.admob.internal.port.NativeRenderPort
import com.company.admob.internal.storage.SharedPrefsKeyValueStore
import com.company.admob.internal.time.Clock
import com.company.admob.internal.time.SystemClockImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * The port instances and host facts [AdsRuntime] is built from. [production] builds the real ones; tests pass fakes.
 * Building must not call Google: the adapters only touch the SDKs when used.
 *
 * @property manifestAppId the manifest's AdMob app ID, or null.
 * @property isDebuggable whether the host app is debuggable.
 * @property ioDispatcher where the library's own disk reads and writes run, never the main thread in production.
 */
internal class RuntimeDeps(
    val adsSdk: AdsSdk,
    val consentSdk: ConsentSdk,
    val activityTracker: ActivityTracker,
    val foregroundSource: AppForegroundSource,
    val clock: Clock,
    val keyValueStore: KeyValueStore,
    val nativeRenderPort: NativeRenderPort,
    val manifestAppId: String?,
    val isDebuggable: Boolean,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onRegister: () -> Unit = {},
    private val onUnregister: () -> Unit = {},
) {
    /** Attaches the lifecycle observers. Called once, for a valid configuration. */
    fun register() = onRegister()

    fun unregister() = onUnregister()

    companion object {
        fun production(app: Application, log: AdLog, scope: CoroutineScope): RuntimeDeps {
            val tracker = LifecycleActivityTracker()
            val foreground = ProcessLifecycleForegroundSource()
            return RuntimeDeps(
                adsSdk = NextGenAdsSdk(scope, log),
                consentSdk = UmpConsentSdk(app, log),
                activityTracker = tracker,
                foregroundSource = foreground,
                clock = SystemClockImpl,
                keyValueStore = SharedPrefsKeyValueStore(app),
                nativeRenderPort = NextGenNativeAdBinder(log),
                manifestAppId = ManifestAppIdReader.read(app),
                isDebuggable = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                onRegister = {
                    tracker.register(app)
                    foreground.register()
                },
                onUnregister = {
                    tracker.unregister(app)
                    foreground.unregister()
                },
            )
        }
    }
}
