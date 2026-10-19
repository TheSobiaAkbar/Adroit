package io.github.thesobiaakbar.adroit.billing.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.thesobiaakbar.adroit.billing.PaywallCloseReason
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.PaywallUiState
import io.github.thesobiaakbar.adroit.billing.views.internal.PaywallBinder
import io.github.thesobiaakbar.adroit.billing.views.internal.backAllowed
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A paywall inside a layout, for a screen or a sheet that is the paywall (not for list items):
 *
 * ```
 * <io.github.thesobiaakbar.adroit.billing.views.PaywallView
 *     android:id="@+id/paywall"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     app:adroit_billing_paywall="Onboarding"
 *     app:adroit_billing_placement="onboarding" />
 * ```
 *
 * Or [bind] it in code. Its controller lives in a ViewModel of the nearest `ViewModelStoreOwner`, keyed by the view's
 * id, so a rotation keeps the selection and the close countdown (give the view an id). It always uses the full-screen
 * layouts (`plans`, or `single`). While `close.back` forbids it, back is consumed; when back is allowed, the screen
 * handles it as usual. Set [onClose] to remove the paywall when it closes.
 */
public class PaywallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    /** Called once when the paywall closes (close button, purchase), with why. */
    public var onClose: ((PaywallCloseReason) -> Unit)? = null

    /** Called after [onClose] with the exit paywall to show next, when one is configured and allowed. */
    public var onExitPaywall: ((PaywallDefinition) -> Unit)? = null

    private var definition: PaywallDefinition? = null
    private var placement: String? = null
    private var job: Job? = null
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }

    init {
        readAttributes(context, attrs, defStyleAttr)
    }

    // The styleable has the library's resource prefix, so it isn't named after the class.
    @SuppressLint("CustomViewStyleable")
    private fun readAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        context.obtainStyledAttributes(attrs, R.styleable.AdroitBillingPaywallView, defStyleAttr, 0).apply {
            try {
                val name = getString(R.styleable.AdroitBillingPaywallView_adroit_billing_paywall)
                placement = getString(R.styleable.AdroitBillingPaywallView_adroit_billing_placement)
                if (name != null && !isInEditMode) definition = AdroitPaywall.definitions(name)
            } finally {
                recycle()
            }
        }
    }

    /** Shows [definition] opened at [placement]. Main thread. */
    public fun bind(definition: PaywallDefinition, placement: String) {
        this.definition = definition
        this.placement = placement
        if (isAttachedToWindow) start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun start() {
        stop()
        val definition = definition ?: return
        val placement = placement ?: return
        val owner = findViewTreeViewModelStoreOwner() ?: return
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        val retainer = ViewModelProvider(owner, PaywallViewRetainer.factory)[PaywallViewRetainer::class.java]
        val key = "${definition.name}:$placement:$id"
        val controller = retainer.controller(key) { AdroitPaywall.controllers(definition, placement, it) }
        val binder = PaywallBinder(this, controller, windowed = false, imageLoader = AdroitPaywall.imageLoader)
        removeAllViews()
        setBackgroundColor(binder.palette.background)
        addView(binder.root)
        findViewTreeOnBackPressedDispatcherOwner()?.onBackPressedDispatcher?.addCallback(lifecycleOwner, back)
        job = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.state.collect { state ->
                    back.isEnabled = !state.backAllowed()
                    if (state is PaywallUiState.Closed) {
                        retainer.remove(key)
                        back.isEnabled = false
                        onClose?.invoke(state.reason)
                        state.exitPaywall?.let { onExitPaywall?.invoke(it) }
                    } else {
                        binder.render(state)
                    }
                }
            }
        }
        post { controller.onShown() }
    }

    private fun stop() {
        job?.cancel()
        job = null
        back.remove()
        back.isEnabled = false
    }
}

/** The [PaywallView] controllers of one screen, by view: they survive rotation and are disposed with the screen. */
internal class PaywallViewRetainer : ViewModel() {
    private val controllers = HashMap<String, PaywallController>()

    fun controller(key: String, create: (kotlinx.coroutines.CoroutineScope) -> PaywallController): PaywallController =
        controllers.getOrPut(key) { create(viewModelScope) }

    /** Forgets a closed paywall, so the next one starts fresh. */
    fun remove(key: String) {
        controllers.remove(key)?.dispose()
    }

    override fun onCleared() {
        controllers.values.forEach(PaywallController::dispose)
        controllers.clear()
    }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory { initializer { PaywallViewRetainer() } }
    }
}
