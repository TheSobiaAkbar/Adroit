package io.github.thesobiaakbar.adroit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thesobiaakbar.adroit.NativeAdResult
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.compose.internal.NativeAdRetainer

/**
 * The native ad for ([placement], [slotKey]) on this screen: null while it loads, then the [NativeAdResult].
 *
 * The ad is kept in a ViewModel of the current `LocalViewModelStoreOwner` (the Activity, or a navigation destination),
 * so recomposition, configuration changes and scrolling out of a lazy list don't reload it. It's destroyed when that
 * owner is cleared; until then every slot keeps its ad. A failed load isn't kept: the slot retries the next time it
 * enters the composition.
 *
 * Use a distinct, stable [slotKey] for each ad of the same placement on a screen, such as a list item's key.
 * The handle belongs to the library: don't destroy it. Most apps use [AdNative], which renders it.
 */
@Composable
public fun rememberNativeAd(placement: NativePlacement, slotKey: Any = Unit): State<NativeAdResult?> =
    rememberNativeAd(placement, slotKey, rememberNativeAdRetainer(), attempt = 0)

/** The screen's native ad retainer, shared by every [rememberNativeAd] of the `ViewModelStoreOwner`. */
@Composable
internal fun rememberNativeAdRetainer(): NativeAdRetainer =
    viewModel(key = NativeAdRetainer.VIEW_MODEL_KEY) { NativeAdRetainer() }

/** [rememberNativeAd], acquiring the slot again (a new load, after a failure) whenever [attempt] changes. */
@Composable
internal fun rememberNativeAd(
    placement: NativePlacement,
    slotKey: Any,
    retainer: NativeAdRetainer,
    attempt: Int,
): State<NativeAdResult?> {
    return produceState(retainer.peek(placement, slotKey), retainer, placement, slotKey, attempt) {
        val acquired = retainer.acquire(placement, slotKey)
        try {
            acquired.collect { value = it }
        } finally {
            retainer.release(placement, slotKey, acquired)
        }
    }
}
