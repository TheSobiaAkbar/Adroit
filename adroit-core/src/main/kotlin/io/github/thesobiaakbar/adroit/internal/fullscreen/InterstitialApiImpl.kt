package io.github.thesobiaakbar.adroit.internal.fullscreen

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdAvailability
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.InterstitialApi
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.threading.runOnMain
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
