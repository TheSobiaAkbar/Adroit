package io.github.thesobiaakbar.adroit.internal.runtime

import android.app.Application
import android.content.pm.ApplicationInfo
import io.github.thesobiaakbar.adroit.internal.config.ManifestAppIdReader
import io.github.thesobiaakbar.adroit.internal.google.NextGenAdsSdk
import io.github.thesobiaakbar.adroit.internal.google.NextGenNativeAdBinder
import io.github.thesobiaakbar.adroit.internal.google.UmpConsentSdk
import io.github.thesobiaakbar.adroit.internal.lifecycle.LifecycleActivityTracker
import io.github.thesobiaakbar.adroit.internal.lifecycle.ProcessLifecycleForegroundSource
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.port.ActivityTracker
import io.github.thesobiaakbar.adroit.internal.port.AdsSdk
import io.github.thesobiaakbar.adroit.internal.port.AppForegroundSource
import io.github.thesobiaakbar.adroit.internal.port.ConsentSdk
import io.github.thesobiaakbar.adroit.internal.port.KeyValueStore
import io.github.thesobiaakbar.adroit.internal.port.NativeRenderPort
import io.github.thesobiaakbar.adroit.internal.storage.SharedPrefsKeyValueStore
import io.github.thesobiaakbar.adroit.internal.time.Clock
import io.github.thesobiaakbar.adroit.internal.time.SystemClockImpl
import io.github.thesobiaakbar.adroit.network.NetworkMonitor
import io.github.thesobiaakbar.adroit.network.NetworkState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * The port instances and host facts [AdsRuntime] is built from. [production] builds the real ones; tests pass fakes.
 * Building must not call Google: the adapters only touch the SDKs when used.
 *
 * @property manifestAppId the manifest's AdMob app ID, or null.
 * @property isDebuggable whether the host app is debuggable.
 * @property ioDispatcher where the library's own disk reads and writes run, never the main thread in production.
 * @property network the device's network, created on first use (only when a load fails); null in tests that don't
 *   simulate it.
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
    val network: Lazy<StateFlow<NetworkState>>? = null,
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
                network = lazy { NetworkMonitor.getInstance(app).state },
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
