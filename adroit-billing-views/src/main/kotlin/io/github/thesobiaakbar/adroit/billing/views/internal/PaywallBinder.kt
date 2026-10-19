package io.github.thesobiaakbar.adroit.billing.views.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import java.text.NumberFormat
import io.github.thesobiaakbar.adroit.billing.Busy
import io.github.thesobiaakbar.adroit.billing.CloseButtonPosition
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallPlan
import io.github.thesobiaakbar.adroit.billing.PaywallStyle
import io.github.thesobiaakbar.adroit.billing.PaywallTemplate
import io.github.thesobiaakbar.adroit.billing.PaywallUiState
import io.github.thesobiaakbar.adroit.billing.PriceText
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.SuccessAction
import io.github.thesobiaakbar.adroit.billing.views.R
import io.github.thesobiaakbar.adroit.billing.R as BillingR

/**
 * Renders a [PaywallController]'s states into the template layout of its `template` (views found by id, so an app's
 * override of the layout keeps working), with the Firebase `style` colors applied in code. [windowed] lays it out as
 * a card (dialog, bottom sheet) instead of filling its parent. Main thread.
 */
internal class PaywallBinder(
    parent: ViewGroup,
    private val controller: PaywallController,
    private val windowed: Boolean,
    private val imageLoader: ((ImageView, String) -> Unit)?,
) {
    private val context: Context = parent.context
    private val spec = controller.spec
    val root: View = LayoutInflater.from(context).inflate(
        if (spec.template == PaywallTemplate.SINGLE) R.layout.adroit_billing_paywall_single else R.layout.adroit_billing_paywall_plans,
        parent,
        false,
    )
    val palette = PaywallColors(context, spec.style)

    private val content: LinearLayout = root.findViewById(R.id.adroit_billing_content)
    private val headerImage: ImageView? = root.findViewById(R.id.adroit_billing_header_image)
    private val title: TextView? = root.findViewById(R.id.adroit_billing_title)
    private val subtitle: TextView? = root.findViewById(R.id.adroit_billing_subtitle)
    private val features: LinearLayout? = root.findViewById(R.id.adroit_billing_features)
    private val plans: LinearLayout? = root.findViewById(R.id.adroit_billing_plans)
    private val price: TextView? = root.findViewById(R.id.adroit_billing_price)
    private val trial: TextView? = root.findViewById(R.id.adroit_billing_trial)
    private val message: TextView? = root.findViewById(R.id.adroit_billing_message)
    private val cta: Button = root.findViewById(R.id.adroit_billing_cta)
    private val ctaProgress: ProgressBar? = root.findViewById(R.id.adroit_billing_cta_progress)
    private val disclosure: TextView = root.findViewById(R.id.adroit_billing_disclosure)
    private val footer: TextView? = root.findViewById(R.id.adroit_billing_footer)
    private val restore: Button? = root.findViewById(R.id.adroit_billing_restore)
    private val manage: Button? = root.findViewById(R.id.adroit_billing_manage)
    private val loading: ProgressBar? = root.findViewById(R.id.adroit_billing_loading)
    private val status: View? = root.findViewById(R.id.adroit_billing_status)
    private val statusTitle: TextView? = root.findViewById(R.id.adroit_billing_status_title)
    private val statusText: TextView? = root.findViewById(R.id.adroit_billing_status_text)
    private val done: Button? = root.findViewById(R.id.adroit_billing_done)
    private val close: View? = root.findViewById(R.id.adroit_billing_close)
    private val closeIcon: ImageView? = root.findViewById(R.id.adroit_billing_close_icon)
    private val closeRing: ProgressBar? = root.findViewById(R.id.adroit_billing_close_ring)
    private val closeCount: TextView? = root.findViewById(R.id.adroit_billing_close_count)

    private var planViews: List<View> = emptyList()

    init {
        style()
        cta.setOnClickListener { context.findActivity()?.let(controller::purchase) }
        restore?.setOnClickListener { controller.restore() }
        manage?.setOnClickListener { context.findActivity()?.let(controller::manage) }
        done?.setOnClickListener { controller.onCloseClicked() }
        close?.setOnClickListener { controller.onCloseClicked() }
        (close?.layoutParams as? android.widget.FrameLayout.LayoutParams)?.gravity =
            if (spec.close.position == CloseButtonPosition.TOP_START) android.view.Gravity.TOP or android.view.Gravity.START
            else android.view.Gravity.TOP or android.view.Gravity.END
        if (windowed) {
            root.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            content.layoutParams = content.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            root.minimumHeight = dp(200)
        }
        spec.style.headerImage?.let { url -> headerImage?.let { image -> imageLoader?.let { load -> image.visibility = View.VISIBLE; load(image, url) } } }
    }

    fun render(state: PaywallUiState) {
        when (state) {
            is PaywallUiState.Content -> content(state)
            PaywallUiState.Loading -> overlay(loading = true)
            is PaywallUiState.Unavailable -> overlay(statusText = context.getString(BillingR.string.adroit_billing_paywall_unavailable))
            is PaywallUiState.Purchased -> if (spec.successAction == SuccessAction.STAY_WITH_MESSAGE) {
                overlay(
                    statusTitle = context.getString(BillingR.string.adroit_billing_paywall_premium_title),
                    statusText = context.getString(BillingR.string.adroit_billing_paywall_premium_text),
                    done = true,
                )
            } else {
                // successAction CLOSE: the paywall closes itself after a purchase.
                controller.onCloseClicked()
            }
            is PaywallUiState.Closed -> Unit
        }
    }

    private fun content(state: PaywallUiState.Content) {
        val plan = state.selectedPlan
        val texts = plan.texts
        content.visibility = View.VISIBLE
        loading?.visibility = View.GONE
        status?.visibility = View.GONE
        title.show(texts.title)
        subtitle.show(texts.subtitle)
        footer.show(texts.footer)
        features?.let { bindFeatures(it, texts.features) }
        plans?.let { bindPlans(it, state) }
        price?.text = PriceText.planPrice(context.resources, plan) ?: plan.product.displayName
        trial.show(plan.offer?.freeTrial?.let { context.getString(BillingR.string.adroit_billing_paywall_trial_badge, PriceText.duration(context.resources, it)) })
        message.show(state.message?.let { context.getString(it.text) })
        cta.text = if (state.busy == Busy.PURCHASING) "" else PriceText.cta(context.resources, plan)
        cta.contentDescription = PriceText.cta(context.resources, plan)
        cta.isEnabled = state.busy == null
        ctaProgress?.visibility = if (state.busy == Busy.PURCHASING) View.VISIBLE else View.GONE
        disclosure.show(PriceText.disclosure(context.resources, plan))
        restore?.visibility = if (spec.showRestore) View.VISIBLE else View.GONE
        restore?.isEnabled = state.busy == null
        manage?.visibility = if (spec.showManage && plan.product.type == ProductType.SUBSCRIPTION) View.VISIBLE else View.GONE
        closeButton(state.closeVisible, state.closeCountdownSeconds, state.busy == null)
    }

    private fun overlay(loading: Boolean = false, statusTitle: String? = null, statusText: String? = null, done: Boolean = false) {
        content.visibility = View.GONE
        this.loading?.visibility = if (loading) View.VISIBLE else View.GONE
        status?.visibility = if (loading) View.GONE else View.VISIBLE
        this.statusTitle.show(statusTitle)
        this.statusText.show(statusText)
        this.done?.visibility = if (done) View.VISIBLE else View.GONE
        // Loading, unavailable and purchased can always be closed.
        closeButton(visible = true, countdown = null, enabled = true)
    }

    private fun closeButton(visible: Boolean, countdown: Int?, enabled: Boolean) {
        val close = close ?: return
        when {
            visible -> {
                close.visibility = View.VISIBLE
                closeIcon?.visibility = View.VISIBLE
                closeRing?.visibility = View.GONE
                closeCount?.visibility = View.GONE
                close.isClickable = enabled
                close.isEnabled = enabled
                close.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                close.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            countdown != null -> {
                close.visibility = View.VISIBLE
                closeIcon?.visibility = View.GONE
                closeRing?.visibility = View.VISIBLE
                closeRing?.progress = countdown * 1000 / spec.close.delaySeconds.coerceAtLeast(1)
                closeCount?.visibility = View.VISIBLE
                closeCount?.text = NumberFormat.getIntegerInstance().format(countdown)
                close.isClickable = false
                // Silent until the X appears.
                close.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            else -> {
                close.visibility = View.INVISIBLE
                close.isClickable = false
                close.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        }
    }

    private fun bindFeatures(container: LinearLayout, items: List<String>) {
        if (container.childCount == items.size && container.tag == items) return
        container.removeAllViews()
        container.tag = items
        items.forEach { feature ->
            container.addView(
                TextView(context).apply {
                    text = feature
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.adroit_billing_body_text))
                    setTextColor(palette.text)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.adroit_billing_ic_check, 0, 0, 0)
                    compoundDrawableTintList = ColorStateList.valueOf(palette.accent)
                    compoundDrawablePadding = dp(12)
                    setPadding(0, dp(4), 0, dp(4))
                },
            )
        }
    }

    private fun bindPlans(container: LinearLayout, state: PaywallUiState.Content) {
        val model = state.model.plans
        if (planViews.size != model.size) {
            container.removeAllViews()
            planViews = model.mapIndexed { index, plan ->
                LayoutInflater.from(context).inflate(R.layout.adroit_billing_plan_item, container, false).also { item ->
                    bindPlan(item, plan)
                    item.setOnClickListener { controller.select(index) }
                    container.addView(item)
                }
            }
        }
        planViews.forEachIndexed { index, item ->
            val selected = index == state.selectedIndex
            item.isSelected = selected
            item.isEnabled = state.busy == null
            item.findViewById<RadioButton>(R.id.adroit_billing_plan_radio)?.isChecked = selected
            item.background = GradientDrawable().apply {
                cornerRadius = context.resources.getDimension(R.dimen.adroit_billing_plan_corner)
                setStroke(dp(if (selected) 2 else 1), if (selected) palette.accent else palette.outline)
            }
        }
    }

    private fun bindPlan(item: View, plan: PaywallPlan) {
        val resources = context.resources
        item.findViewById<TextView>(R.id.adroit_billing_plan_name)?.apply {
            text = plan.product.displayName
            setTextColor(palette.text)
        }
        item.findViewById<TextView>(R.id.adroit_billing_plan_price)?.apply {
            text = PriceText.planPrice(resources, plan) ?: plan.product.displayName
            setTextColor(palette.text)
        }
        item.findViewById<TextView>(R.id.adroit_billing_plan_trial)
            .show(plan.offer?.freeTrial?.let { context.getString(BillingR.string.adroit_billing_paywall_trial_badge, PriceText.duration(resources, it)) })
        item.findViewById<TextView>(R.id.adroit_billing_plan_trial)?.setTextColor(palette.accent)
        item.findViewById<TextView>(R.id.adroit_billing_plan_badge)?.apply {
            show(plan.spec.badge?.replace('_', ' ') ?: plan.savingsPercent?.let { context.getString(BillingR.string.adroit_billing_paywall_save, "$it%") })
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(palette.accent)
            }
            setTextColor(palette.onAccent)
        }
        item.findViewById<RadioButton>(R.id.adroit_billing_plan_radio)?.buttonTintList = ColorStateList.valueOf(palette.accent)
        // A radio button for accessibility services: checkable, checked when selected.
        item.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = RadioButton::class.java.name
                info.isCheckable = true
                // The Int overload replacing it is API 36; minSdk is 24.
                @Suppress("DEPRECATION")
                info.isChecked = host.isSelected
            }
        }
    }

    private fun style() {
        if (windowed) {
            root.background = GradientDrawable().apply {
                setColor(palette.background)
                val radius = context.resources.getDimension(R.dimen.adroit_billing_corner)
                cornerRadii = if (spec.template == PaywallTemplate.BOTTOM_SHEET) {
                    floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
                } else {
                    FloatArray(8) { radius }
                }
            }
        }
        listOfNotNull(title, subtitle, statusTitle, statusText, price).forEach { it.setTextColor(palette.text) }
        listOfNotNull(disclosure, footer).forEach { it.setTextColor(palette.secondaryText) }
        trial?.setTextColor(palette.accent)
        cta.background = GradientDrawable().apply {
            cornerRadius = context.resources.getDimension(R.dimen.adroit_billing_cta_height) / 2
            setColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(palette.accent and 0x61FFFFFF, palette.accent)))
        }
        cta.setTextColor(palette.onAccent)
        ctaProgress?.indeterminateTintList = ColorStateList.valueOf(palette.onAccent)
        listOfNotNull(restore, manage, done).forEach { it.setTextColor(palette.accent) }
        closeIcon?.imageTintList = ColorStateList.valueOf(palette.text)
        closeCount?.setTextColor(palette.text)
        closeRing?.progressTintList = ColorStateList.valueOf(palette.accent)
        loading?.indeterminateTintList = ColorStateList.valueOf(palette.accent)
    }

    private fun TextView?.show(text: String?) {
        this ?: return
        this.text = text
        visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

/** The paywall's colors: the Firebase `style` over the theme; text follows the background's luminance when styled. */
internal class PaywallColors(context: Context, style: PaywallStyle) {
    val accent: Int = style.accent ?: context.themeColor(android.R.attr.colorAccent, 0xFF6750A4.toInt())
    val background: Int = style.background ?: when (style.dark) {
        true -> 0xFF121212.toInt()
        false -> Color.WHITE
        null -> context.themeColor(android.R.attr.colorBackground, Color.WHITE)
    }
    val text: Int = if (style.background != null || style.dark != null) contentColor(background) else
        context.themeColor(android.R.attr.textColorPrimary, contentColor(background))
    val secondaryText: Int = (text and 0x00FFFFFF) or (0xB8 shl 24)
    val outline: Int = (text and 0x00FFFFFF) or (0x33 shl 24)
    val onAccent: Int = contentColor(accent)

    private fun contentColor(color: Int): Int {
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return if (luminance > 0.6) Color.BLACK else Color.WHITE
    }
}

private fun Context.themeColor(attr: Int, fallback: Int): Int {
    val value = TypedValue()
    if (!theme.resolveAttribute(attr, value, true)) return fallback
    return if (value.resourceId != 0) {
        runCatching { getColorStateList(value.resourceId).defaultColor }.getOrDefault(fallback)
    } else {
        value.data
    }
}

/** The Activity behind this Context, unwrapping `ContextWrapper`s; null if none. Only for the duration of a click. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}

/** Whether back closes the paywall now: per `close.back` while the plans show; always while it loads or failed. */
internal fun PaywallUiState.backAllowed(): Boolean = when (this) {
    is PaywallUiState.Content -> backAllowed
    is PaywallUiState.Closed -> false
    else -> true
}

/** Whether a tap outside a dialog or sheet closes it: its close button is visible. */
internal fun PaywallUiState.dismissible(): Boolean = when (this) {
    is PaywallUiState.Content -> closeVisible && busy == null
    is PaywallUiState.Closed -> false
    else -> true
}
