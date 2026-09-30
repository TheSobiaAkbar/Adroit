package io.github.thesobiaakbar.adroit.internal.native

import android.view.View
import android.view.ViewGroup
import io.github.thesobiaakbar.adroit.NativeAdHandle
import io.github.thesobiaakbar.adroit.NativeAdLayout
import io.github.thesobiaakbar.adroit.NativeAdRenderer
import io.github.thesobiaakbar.adroit.NativeAdResult
import io.github.thesobiaakbar.adroit.NativeApi
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.internal.HardwareAccelerationCheck
import io.github.thesobiaakbar.adroit.internal.threading.MainDispatcher
import io.github.thesobiaakbar.adroit.internal.threading.isMainThread
import io.github.thesobiaakbar.adroit.internal.threading.runOnMain
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** [NativeApi] over [NativeAdLoaderEngine] and [NativeAdBinder]. Loads are callable from any thread; render isn't. */
internal class NativeApiImpl(
    private val engine: NativeAdLoaderEngine,
    private val binder: NativeAdBinder,
    private val hardwareAcceleration: HardwareAccelerationCheck,
    private val styleOf: (NativePlacement) -> NativeStyle? = { null },
) : NativeApi {
    override fun style(placement: NativePlacement): NativeStyle = styleOf(placement) ?: NativeStyle.DEFAULT

    override fun preload(placement: NativePlacement) = runOnMain { engine.preload(placement) }

    override fun load(placement: NativePlacement, onResult: (NativeAdResult) -> Unit) =
        runOnMain { engine.load(placement, onResult) }

    /**
     * If the caller is cancelled while waiting, the request is withdrawn; a result that was already on its way is
     * destroyed rather than leaked, since nobody would own it.
     */
    override suspend fun load(placement: NativePlacement): NativeAdResult = withContext(MainDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val withdraw = engine.load(placement) { result ->
                continuation.resume(result) { _, value, _ ->
                    runOnMain { (value as? NativeAdResult.Loaded)?.handle?.destroy() }
                }
            }
            continuation.invokeOnCancellation { runOnMain(withdraw) }
        }
    }

    override fun render(
        handle: NativeAdHandle,
        container: ViewGroup,
        layout: NativeAdLayout,
        renderer: NativeAdRenderer?,
    ): View {
        check(isMainThread()) { "native.render() must be called on the main thread" }
        require(handle is NativeAdHandleImpl) { "render() takes a handle from native.load()" }
        hardwareAcceleration.warnIfDisabled(container.context)
        return binder.render(handle, container, layout, renderer)
    }
}
