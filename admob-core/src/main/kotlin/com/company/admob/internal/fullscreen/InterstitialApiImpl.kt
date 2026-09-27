package com.company.admob.internal.fullscreen

import android.app.Activity
import com.company.admob.AdAvailability
import com.company.admob.FullScreenResult
import com.company.admob.InterstitialApi
import com.company.admob.InterstitialPlacement
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.threading.runOnMain
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** [InterstitialApi] over [FullScreenAdEngine]. Callable from any thread; engine work runs on the main thread. */
internal class InterstitialApiImpl(
    private val engine: FullScreenAdEngine,
    private val log: AdLog,
) : InterstitialApi {
    override fun preload(placement: InterstitialPlacement) = runOnMain { engine.preload(placement) }

    override fun availability(placement: InterstitialPlacement): StateFlow<AdAvailability> =
        engine.availability(placement)

    override fun isReady(placement: InterstitialPlacement): Boolean = engine.isReady(placement)

    override fun clear(placement: InterstitialPlacement) = runOnMain { engine.clear(placement) }

    /** [onResult] is never invoked synchronously inside this call: it's always posted to the main thread. */
    override fun show(activity: Activity, placement: InterstitialPlacement, onResult: (FullScreenResult) -> Unit) =
        runOnMain { engine.show(activity, placement, onResult = onResult) }

    /** [onResult] is never invoked synchronously inside this call: it's always posted to the main thread. */
    override fun showOnSplash(activity: Activity, placement: InterstitialPlacement, onResult: (FullScreenResult) -> Unit) =
        runOnMain { engine.showOnSplash(activity, placement, onResult) }

    /**
     * Cancelling the caller doesn't cancel an ad that is already showing: the engine still finishes the show and
     * releases the full-screen slot on dismiss. The result is dropped, which is safe because interstitials carry no
     * reward.
     */
    override suspend fun showAndAwait(activity: Activity, placement: InterstitialPlacement): FullScreenResult =
        suspendCancellableCoroutine { continuation ->
            show(activity, placement) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                } else {
                    log.d { "showAndAwait(${placement.key}) was cancelled; dropping $result" }
                }
            }
        }
}
