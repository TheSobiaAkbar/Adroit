package com.company.admob.compose

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.company.admob.BannerAdHandle
import com.company.admob.BannerOptions
import com.company.admob.BannerPlacement
import com.company.admob.BannerState
import com.company.admob.CompanyAdMobInternal
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.compose.internal.BannerShimmer
import com.company.admob.compose.internal.ComposeRuntime
import com.company.admob.views.AdShimmerView
import com.company.admob.compose.internal.findActivity

/**
 * A banner ad for [placement].
 *
 * The banner is kept by the screen, like [AdNative]'s ads: in a ViewModel of the current `LocalViewModelStoreOwner`
 * (a navigation destination, a Fragment, or the Activity), one per ([placement], [options], [slotKey]). Recomposition,
 * leaving and coming back to the composition (another tab, scrolling out of a lazy list) and configuration changes
 * show the same ad again, with no new request; it's destroyed when that owner is cleared (the destination is popped,
 * the Fragment or Activity finishes). While it's off screen it keeps no View or Activity.
 *
 * @param modifier applied to the banner's slot, which fills the available width.
 * @param options the banner size, and whether to reserve its expected height while it loads.
 * @param placeholder shown over the reserved space while the banner is loading. Without one, an ad profile with
 *   `showLoading` (the default) shows a shimmer shaped like its banner, in the banner's height.
 * @param slotKey distinct and stable for each banner of the same placement on a screen, such as a lazy list item's key.
 * @param onStateChange called on the main thread with each new [BannerState].
 *
 * Without an Activity in `LocalContext`, logs an error and shows nothing.
 */
@Composable
public fun AdBanner(
    placement: BannerPlacement,
    modifier: Modifier = Modifier,
    options: BannerOptions = BannerOptions(),
    placeholder: (@Composable () -> Unit)? = null,
    slotKey: Any = Unit,
    onStateChange: (BannerState) -> Unit = {},
) {
    AdBanner(placement, modifier, options, placeholder, slotKey, refreshKey = null, onStateChange)
}

/**
 * [AdBanner], with [refreshKey]: each time it changes after the first composition, the slot asks for a new banner.
 * A shown banner stays on screen (no shimmer) while the new one loads unseen, and is replaced once it's loaded; if
 * the new one fails, the shown one stays. A banner whose load failed is replaced by a new request, and one still
 * loading is left alone. Without a change of [refreshKey], the slot never requests a second banner.
 */
@OptIn(InternalCompanyAdMobApi::class)
@Composable
public fun AdBanner(
    placement: BannerPlacement,
    modifier: Modifier = Modifier,
    options: BannerOptions = BannerOptions(),
    placeholder: (@Composable () -> Unit)? = null,
    slotKey: Any = Unit,
    refreshKey: Any?,
    onStateChange: (BannerState) -> Unit = {},
) {
    val activity = LocalContext.current.findActivity()
    if (activity == null) {
        LaunchedEffect(placement) {
            ComposeRuntime.error("AdBanner(${placement.key}): no Activity in LocalContext; not showing a banner")
        }
        return
    }
    val retainer: BannerRetainer = viewModel(key = BannerRetainer.VIEW_MODEL_KEY) { BannerRetainer() }
    val banner = remember(retainer, placement, options, slotKey) { retainer.get(placement, options, slotKey, activity) }
    // On screen in this Activity while composed (idempotent, and before the views below are read); off screen, with
    // its ad kept, once it leaves.
    banner.showIn(activity)
    DisposableEffect(banner) { onDispose { banner.park() } }
    val lastRefreshKey = remember(banner) { arrayOf(refreshKey) }
    LaunchedEffect(banner, refreshKey) {
        if (lastRefreshKey[0] == refreshKey) return@LaunchedEffect
        lastRefreshKey[0] = refreshKey
        banner.refresh(activity)
    }
    val incoming = banner.incoming
    if (incoming != null) {
        LaunchedEffect(incoming) {
            incoming.state.collect { state ->
                when (state) {
                    BannerState.Loaded -> banner.promote(incoming)
                    is BannerState.Failed, is BannerState.Hidden -> banner.dropIncoming(incoming)
                    else -> Unit
                }
            }
        }
    }
    val handle = banner.handle
    val state by handle.state.collectAsStateWithLifecycle()
    val currentOnStateChange by rememberUpdatedState(onStateChange)
    LaunchedEffect(handle, state) { currentOnStateChange(state) }

    val loading = state == BannerState.Loading
    val showing = loading || state == BannerState.Loaded
    val shimmer = loading && placeholder == null && ComposeRuntime.showsLoading(placement.key)
    val density = LocalDensity.current
    // Before its first layout an adaptive banner doesn't know its height: a usual one until then.
    val shimmerHeightPx = handle.expectedHeightPx.takeIf { it > 0 }
        ?: with(density) { AdShimmerView.DEFAULT_BANNER_HEIGHT_DP.dp.roundToPx() }
    val reserved = when {
        shimmer -> with(density) { shimmerHeightPx.toDp() }
        options.reserveSpace && showing -> with(density) { handle.expectedHeightPx.toDp() }
        else -> 0.dp
    }
    val collapsed = !options.reserveSpace && !showing && !shimmer
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = reserved)
            .then(if (collapsed) Modifier.height(0.dp) else Modifier),
    ) {
        if (incoming != null) {
            // Loading unseen under the shown banner (laid out: it needs its width), until it replaces it.
            key(incoming) {
                AndroidView(
                    factory = { incoming.view.also { (it.parent as? ViewGroup)?.removeView(it) } },
                    modifier = Modifier.fillMaxWidth().alpha(0f),
                )
            }
        }
        key(handle) {
            AndroidView(
                // The handle's own view, never a new AdView: recomposition and re-attachment reuse the same banner.
                factory = { handle.view.also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (loading && placeholder != null) {
            Box(Modifier.matchParentSize()) { placeholder() }
        } else if (shimmer) {
            BannerShimmer(shimmerHeightPx, Modifier.matchParentSize())
        }
    }
}

/** The banners of one screen (one `ViewModelStoreOwner`), one per (placement, options, slotKey), destroyed with it. */
internal class BannerRetainer : ViewModel() {
    private data class Key(val placement: BannerPlacement, val options: BannerOptions, val slotKey: Any)

    private val banners = HashMap<Key, RetainedBanner>()

    fun get(placement: BannerPlacement, options: BannerOptions, slotKey: Any, activity: Activity): RetainedBanner =
        banners.getOrPut(Key(placement, options, slotKey)) { RetainedBanner(activity, placement, options) }

    override fun onCleared() {
        banners.values.forEach(RetainedBanner::destroyAll)
        banners.clear()
    }

    companion object {
        const val VIEW_MODEL_KEY: String = "company_admob_banner"
    }
}

/**
 * One banner slot: the banner shown ([handle]), and a refresh loading unseen ([incoming]). Keeps no Activity: its
 * handles are on screen in the composition's Activity ([showIn]) or off screen without one ([park]).
 */
@OptIn(InternalCompanyAdMobApi::class)
internal class RetainedBanner(
    activity: Activity,
    private val placement: BannerPlacement,
    private val options: BannerOptions,
) {
    /** The banner shown, or loading when there's none yet. */
    var handle: BannerAdHandle by mutableStateOf(ComposeRuntime.client.banner.create(activity, placement, options))
        private set

    /** A refresh loading unseen, which replaces [handle] once loaded. */
    var incoming: BannerAdHandle? by mutableStateOf(null)
        private set

    fun showIn(activity: Activity) {
        CompanyAdMobInternal.showBannerIn(handle, activity)
        incoming?.let { CompanyAdMobInternal.showBannerIn(it, activity) }
    }

    fun park() {
        CompanyAdMobInternal.parkBanner(handle)
        // A refresh doesn't outlive the screen it was asked on: a loaded ad would replace the shown one unseen.
        incoming?.destroy()
        incoming = null
    }

    /** A shown banner gets a background replacement; a failed one a new request; a loading one nothing. */
    fun refresh(activity: Activity) {
        if (incoming != null) return
        when (handle.state.value) {
            BannerState.Loaded -> incoming = ComposeRuntime.client.banner.create(activity, placement, options)
            is BannerState.Failed -> {
                handle.destroy()
                handle = ComposeRuntime.client.banner.create(activity, placement, options)
            }
            else -> Unit
        }
    }

    fun promote(next: BannerAdHandle) {
        if (incoming !== next) return
        val old = handle
        handle = next
        incoming = null
        old.destroy()
    }

    fun dropIncoming(next: BannerAdHandle) {
        if (incoming !== next) return
        ComposeRuntime.debug("AdBanner(${placement.key}): refresh not loaded; keeping the current banner")
        incoming = null
        next.destroy()
    }

    fun destroyAll() {
        incoming?.destroy()
        incoming = null
        handle.destroy()
    }
}
