package com.company.admob.internal.native

import android.view.View
import android.view.ViewGroup
import com.company.admob.NativeAdHandle
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdRenderer
import com.company.admob.NativeAdResult
import com.company.admob.NativeApi
import com.company.admob.NativePlacement
import com.company.admob.NativeStyle
import com.company.admob.internal.HardwareAccelerationCheck
import com.company.admob.internal.threading.MainDispatcher
import com.company.admob.internal.threading.isMainThread
import com.company.admob.internal.threading.runOnMain
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** [NativeApi] over [NativeAdLoaderEngine] and [NativeAdBinder]. Loads are callable from any thread; render isn't. */
internal class NativeApiImpl(
    private val engine: NativeAdLoaderEngine,
    private val binder: NativeAdBinder,
    private val hardwareAcceleration: HardwareAccelerationCheck,
    private val styleOf: (NativePlacement) -> NativeStyle? = { null },
) : NativeApi {
    override fun style(placement: NativePlacement): NativeStyle = styleOf(placement) ?: NativeStyle.MEDIUM

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
