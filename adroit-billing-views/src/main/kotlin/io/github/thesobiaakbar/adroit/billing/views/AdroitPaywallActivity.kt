package io.github.thesobiaakbar.adroit.billing.views

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.thesobiaakbar.adroit.billing.PaywallCloseReason
import io.github.thesobiaakbar.adroit.billing.PaywallTemplate
import io.github.thesobiaakbar.adroit.billing.PaywallUiState
import io.github.thesobiaakbar.adroit.billing.views.internal.PaywallBinder
import io.github.thesobiaakbar.adroit.billing.views.internal.PaywallHostModel
import io.github.thesobiaakbar.adroit.billing.views.internal.backAllowed
import io.github.thesobiaakbar.adroit.billing.views.internal.dismissible
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The paywall screen of [AdroitPaywall]: every template, edge-to-edge, in theme `Adroit.Billing.Theme.Paywall`. The
 * full-screen templates fill it; `dialog` and `bottom_sheet` show a card over a scrim (a tap on the scrim closes it once
 * the close button shows). Back is consumed while `close.back` forbids it. When the user closes it and an exit paywall
 * is configured and allowed, the exit paywall replaces it here. Its result is the close reason ([AdroitPaywall.contract]).
 *
 * Open it with [AdroitPaywall.show] or [AdroitPaywall.intent]; it isn't exported.
 */
public class AdroitPaywallActivity : ComponentActivity() {
    private lateinit var host: PaywallHostModel
    private lateinit var container: FrameLayout
    private var binder: PaywallBinder? = null
    private var render: Job? = null

    // Enabled while back must not close the paywall, or must close it here (to show an exit paywall). Otherwise the
    // system handles back (with its predictive animation) and onDestroy records the close.
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            host.controller?.onBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra(EXTRA_PAYWALL).orEmpty()
        val placement = intent.getStringExtra(EXTRA_PLACEMENT).orEmpty()
        host = ViewModelProvider(this, PaywallHostModel.factory(name, placement))[PaywallHostModel::class.java]
        setResult(RESULT_OK, result(PaywallCloseReason.BACK))
        container = FrameLayout(this)
        setContentView(container)
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, back)
        bind()
    }

    private fun bind() {
        render?.cancel()
        container.removeAllViews()
        val controller = host.controller ?: return finishWith(PaywallCloseReason.ERROR)
        val windowed = controller.spec.template == PaywallTemplate.DIALOG || controller.spec.template == PaywallTemplate.BOTTOM_SHEET
        val binder = PaywallBinder(container, controller, windowed, AdroitPaywall.imageLoader)
        this.binder = binder
        if (windowed) {
            container.setBackgroundColor(getColor(R.color.adroit_billing_scrim))
            container.setOnClickListener { if (controller.state.value.dismissible()) controller.onCloseClicked() }
            binder.root.isClickable = true
            val sheet = controller.spec.template == PaywallTemplate.BOTTOM_SHEET
            val margin = if (sheet) 0 else resources.getDimensionPixelSize(R.dimen.adroit_billing_padding)
            val width = minOf(resources.displayMetrics.widthPixels - 2 * margin, resources.getDimensionPixelSize(R.dimen.adroit_billing_max_width))
            container.addView(
                binder.root,
                FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, if (sheet) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.CENTER).apply {
                    topMargin = resources.getDimensionPixelSize(R.dimen.adroit_billing_touch_target)
                },
            )
        } else {
            container.setBackgroundColor(binder.palette.background)
            container.setOnClickListener(null)
            container.isClickable = false
            container.addView(binder.root)
        }
        render = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.state.collect { state ->
                    if (state is PaywallUiState.Closed) {
                        closed(state)
                    } else {
                        binder.render(state)
                        back.isEnabled = !state.backAllowed() || (state is PaywallUiState.Content && controller.spec.exitPaywall != null)
                    }
                }
            }
        }
        // After the first frame: the paywall is on screen, so its close delay and its showing start now.
        binder.root.post { controller.onShown() }
    }

    private fun closed(state: PaywallUiState.Closed) {
        val exit = state.exitPaywall
        if (exit != null) {
            host.replace(exit)
            bind()
        } else {
            finishWith(state.reason)
        }
    }

    private fun finishWith(reason: PaywallCloseReason) {
        setResult(RESULT_OK, result(reason))
        finish()
    }

    override fun onDestroy() {
        // Closed by the system back gesture (the callback was off): record it as a back close.
        if (isFinishing) host.controller?.takeIf { it.state.value !is PaywallUiState.Closed }?.onBackPressed()
        super.onDestroy()
    }

    private fun result(reason: PaywallCloseReason) = Intent().putExtra(EXTRA_REASON, reason.name)

    internal companion object {
        const val EXTRA_PAYWALL = "io.github.thesobiaakbar.adroit.billing.PAYWALL"
        const val EXTRA_PLACEMENT = "io.github.thesobiaakbar.adroit.billing.PLACEMENT"
        const val EXTRA_REASON = "io.github.thesobiaakbar.adroit.billing.CLOSE_REASON"
    }
}
