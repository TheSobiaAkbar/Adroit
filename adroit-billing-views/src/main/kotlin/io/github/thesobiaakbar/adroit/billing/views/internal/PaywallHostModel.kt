package io.github.thesobiaakbar.adroit.billing.views.internal

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.views.AdroitPaywall

/**
 * The paywall Activity's controller, kept across rotation; its state (and the paywall shown, after an exit paywall)
 * comes back after process death through the [SavedStateHandle].
 */
internal class PaywallHostModel(private val handle: SavedStateHandle, name: String, private val placement: String) : ViewModel() {
    /** Null when no paywall of that name is registered. */
    var controller: PaywallController? = null
        private set

    init {
        controller = AdroitPaywall.definitions(handle[KEY_NAME] ?: name)?.let(::create)
        handle.get<Bundle>(KEY_STATE)?.let { saved -> controller?.restore(saved.toMap()) }
        handle.setSavedStateProvider(KEY_STATE) { controller?.saveState()?.toBundle() ?: Bundle() }
    }

    /** Shows [exit] instead (the exit paywall), from a fresh state. */
    fun replace(exit: PaywallDefinition) {
        controller?.dispose()
        handle[KEY_NAME] = exit.name
        controller = create(exit)
    }

    private fun create(definition: PaywallDefinition) = AdroitPaywall.controllers(definition, placement, viewModelScope)

    override fun onCleared() {
        controller?.dispose()
    }

    companion object {
        private const val KEY_NAME = "adroit_paywall_name"
        private const val KEY_STATE = "adroit_paywall_state"

        fun factory(name: String, placement: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { PaywallHostModel(createSavedStateHandle(), name, placement) } }
    }
}

internal fun Map<String, Any>.toBundle(): Bundle = Bundle().also { bundle ->
    forEach { (key, value) ->
        when (value) {
            is Int -> bundle.putInt(key, value)
            is Long -> bundle.putLong(key, value)
            is Boolean -> bundle.putBoolean(key, value)
        }
    }
}

internal fun Bundle.toMap(): Map<String, Any> = keySet().mapNotNull { key ->
    @Suppress("DEPRECATION")
    get(key)?.let { key to it }
}.toMap()
