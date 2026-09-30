package io.github.thesobiaakbar.adroit.testing

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.thesobiaakbar.adroit.BannerAdHandle
import io.github.thesobiaakbar.adroit.BannerOptions
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.BannerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A banner from [FakeAdsClient]: [view] is an empty `FrameLayout` made with the Activity passed to `banner.create`,
 * and [state] is whatever the test sets with [setState]. Like the real handle, [bindTo] destroys it when the owner is
 * destroyed, and [destroy] removes the view and moves to `Hidden(null)`. Main thread only (it's a View).
 */
public class FakeBannerAdHandle internal constructor(
    activity: Activity,
    public val placement: BannerPlacement,
    public val options: BannerOptions,
    initialState: BannerState,
) : BannerAdHandle {
    private val mutableState = MutableStateFlow(initialState)

    override val view: View = FrameLayout(activity)

    override val state: StateFlow<BannerState> = mutableState.asStateFlow()

    override var expectedHeightPx: Int = 0

    /** The owner passed to [bindTo], if any. */
    public var boundOwner: LifecycleOwner? = null
        private set

    /** True once [destroy] ran. */
    public var isDestroyed: Boolean = false
        private set

    /** Moves the banner to [state], for example `Loaded` or `Failed(...)`. */
    public fun setState(state: BannerState) {
        mutableState.value = state
    }

    override fun bindTo(owner: LifecycleOwner) {
        boundOwner = owner
        owner.lifecycle.addObserver(
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroy() },
        )
    }

    override fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        (view.parent as? ViewGroup)?.removeView(view)
        mutableState.value = BannerState.Hidden(null)
    }
}
