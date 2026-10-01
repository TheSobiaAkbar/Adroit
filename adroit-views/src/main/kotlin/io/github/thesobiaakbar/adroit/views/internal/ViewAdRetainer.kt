package io.github.thesobiaakbar.adroit.views.internal

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.thesobiaakbar.adroit.BannerAdHandle
import io.github.thesobiaakbar.adroit.views.NativeAdHolder

/**
 * The ads of [io.github.thesobiaakbar.adroit.views.AdBannerView] and
 * [io.github.thesobiaakbar.adroit.views.AdNativeView] on one screen: a ViewModel of their view tree's
 * `ViewModelStoreOwner` (the Fragment inside a Fragment, otherwise the Activity), one ad per slot. A screen's views
 * can be destroyed and created again (another tab, rotation) and show the same ads, with no new request; the ads are
 * destroyed when the screen is cleared. Between views it keeps no View or Activity. Main thread only.
 */
internal class ViewAdRetainer : ViewModel() {
    val natives = HashMap<String, NativeAdHolder>()
    val banners = HashMap<String, BannerAdHandle>()

    override fun onCleared() {
        natives.values.forEach(NativeAdHolder::clear)
        natives.clear()
        banners.values.forEach(BannerAdHandle::destroy)
        banners.clear()
    }

    companion object {
        private const val KEY = "adroit_view_ads"
        private val factory = viewModelFactory { initializer { ViewAdRetainer() } }

        /** [view]'s screen retainer, or null without a `ViewModelStoreOwner` in its view tree. */
        fun of(view: View): ViewAdRetainer? =
            view.findViewTreeViewModelStoreOwner()?.let { ViewModelProvider(it, factory)[KEY, ViewAdRetainer::class.java] }

        /** A slot: the placement, and the view's id (or its [io.github.thesobiaakbar.adroit.views.AdSlotView]'s). */
        fun slot(placementKey: String, viewId: Int): String = "$placementKey#$viewId"
    }
}
