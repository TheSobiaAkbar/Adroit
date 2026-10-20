package io.github.thesobiaakbar.adroit.billing.compose

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.thesobiaakbar.adroit.billing.Busy
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallPlan
import io.github.thesobiaakbar.adroit.billing.PaywallUiState
import io.github.thesobiaakbar.adroit.billing.PriceText
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.R as BillingR

/**
 * The building blocks of a paywall, for a custom layout passed as `content` to [Paywall]. Each block reads [state]
 * and calls [controller]; the built-in templates are made of the same blocks.
 *
 * ```
 * Paywall(AppPaywalls.Onboarding, "onboarding", onClose = { navController.popBackStack() }) {
 *     Column {
 *         CloseButton(Modifier.align(Alignment.End))
 *         Title()
 *         PlanList()
 *         CtaButton()
 *         Disclosure()
 *     }
 * }
 * ```
 *
 * @property controller the paywall's controller.
 * @property state what to show now.
 */
@Stable
public class PaywallScope internal constructor(
    public val controller: PaywallController,
    public val state: PaywallUiState.Content,
    private val imageLoader: (@Composable (url: String) -> Unit)?,
) {
    private val spec get() = state.model.spec

    /** The header image (`style.headerImage`), drawn by the `imageLoader` given to [Paywall]; nothing without one. */
    @Composable
    public fun HeaderImage(modifier: Modifier = Modifier) {
        val url = spec.style.headerImage ?: return
        val loader = imageLoader ?: return
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { loader(url) }
    }

    /** The title and subtitle (`texts.title`, `texts.subtitle`); nothing for a text that isn't set. */
    @Composable
    public fun Title(modifier: Modifier = Modifier) {
        val texts = state.selectedPlan.texts
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            texts.title?.let {
                Text(
                    it,
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            texts.subtitle?.let {
                Text(
                    it,
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    /** The benefits (`texts.features`), one per line with a check mark. */
    @Composable
    public fun Features(modifier: Modifier = Modifier) {
        val features = state.selectedPlan.texts.features
        if (features.isEmpty()) return
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckMark(Modifier.size(20.dp))
                    Text(feature, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    /** Every plan, as a single-choice group of [PlanCard]s. */
    @Composable
    public fun PlanList(modifier: Modifier = Modifier) {
        Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.model.plans.forEach { plan -> PlanCard(plan) }
        }
    }

    /**
     * One plan: its price as Google Play formats it, its free trial, badge and savings. Selecting it selects the plan
     * (a radio button for accessibility services).
     */
    @Composable
    public fun PlanCard(plan: PaywallPlan, modifier: Modifier = Modifier) {
        val index = state.model.plans.indexOf(plan)
        val selected = index == state.selectedIndex
        val resources = LocalResources.current
        val price = PriceText.planPrice(resources, plan) ?: plan.product.displayName
        val trial = plan.offer?.freeTrial?.let {
            stringResource(BillingR.string.adroit_billing_paywall_trial_badge, PriceText.duration(resources, it))
        }
        val savings = plan.savingsPercent?.let { stringResource(BillingR.string.adroit_billing_paywall_save, "$it%") }
        val colors = MaterialTheme.colorScheme
        OutlinedCard(
            modifier = modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    enabled = state.busy == null,
                    role = Role.RadioButton,
                    onClick = { controller.select(index) },
                ),
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(plan.product.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(price, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    trial?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.primary) }
                }
                val badge = plan.spec.badge?.replace('_', ' ') ?: savings
                badge?.let {
                    Surface(color = colors.primary, contentColor = colors.onPrimary, shape = MaterialTheme.shapes.small) {
                        Text(it, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    /** The purchase button: `texts.cta` (or `ctaTrial` for a trial), a progress indicator while buying. */
    @Composable
    public fun CtaButton(modifier: Modifier = Modifier) {
        val activity = LocalActivity.current
        val text = PriceText.cta(LocalResources.current, state.selectedPlan)
        Button(
            onClick = { activity?.let(controller::purchase) },
            enabled = state.busy == null && activity != null,
            // At least 56dp: a large font needs more, and a fixed height would cut the label.
            modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            if (state.busy == Busy.PURCHASING) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text(text, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    /**
     * The renewal terms of the selected plan, then `texts.footer`. The built-in templates always show it under the
     * purchase button.
     */
    @Composable
    public fun Disclosure(modifier: Modifier = Modifier) {
        val disclosure = PriceText.disclosure(LocalResources.current, state.selectedPlan)
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            listOfNotNull(disclosure, state.selectedPlan.texts.footer).forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    /** The message after a purchase or restore attempt (for example "Payment declined"); nothing without one. */
    @Composable
    public fun Message(modifier: Modifier = Modifier) {
        val message = state.message ?: return
        Text(
            stringResource(message.text),
            modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }

    /**
     * The close button, per `close`: hidden until the delay has passed, with a countdown ring meanwhile when
     * `showCountdown` is on, never shown for a delay of -1. Announced politely when it appears.
     */
    @Composable
    public fun CloseButton(modifier: Modifier = Modifier) {
        CloseControl(
            visible = state.closeVisible,
            countdown = state.closeCountdownSeconds,
            delaySeconds = spec.close.delaySeconds,
            enabled = state.busy == null,
            onClick = controller::onCloseClicked,
            modifier = modifier,
        )
    }

    /** "Restore purchases", when `showRestore` is on. */
    @Composable
    public fun RestoreButton(modifier: Modifier = Modifier) {
        if (!spec.showRestore) return
        TextButton(onClick = controller::restore, enabled = state.busy == null, modifier = modifier) {
            if (state.busy == Busy.RESTORING) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(BillingR.string.adroit_billing_paywall_restore))
            }
        }
    }

    /** "Manage subscription" (Google Play), when `showManage` is on and the selected plan is a subscription. */
    @Composable
    public fun ManageButton(modifier: Modifier = Modifier) {
        if (!spec.showManage || state.selectedPlan.product.type != ProductType.SUBSCRIPTION) return
        val activity = LocalActivity.current ?: return
        TextButton(onClick = { controller.manage(activity) }, enabled = state.busy == null, modifier = modifier) {
            Text(stringResource(BillingR.string.adroit_billing_paywall_manage))
        }
    }
}

/**
 * The close button, or its countdown ring, in a 48dp touch target. [visible] shows the X; otherwise [countdown] (when
 * set) shows the seconds left, silent for accessibility services until the X appears.
 */
@Composable
internal fun CloseControl(
    visible: Boolean,
    countdown: Int?,
    delaySeconds: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(BillingR.string.adroit_billing_paywall_close)
    Box(modifier.sizeIn(minWidth = PaywallDefaults.CloseButtonSize, minHeight = PaywallDefaults.CloseButtonSize), contentAlignment = Alignment.Center) {
        when {
            visible -> Box(
                Modifier
                    .size(PaywallDefaults.CloseButtonSize)
                    .semantics {
                        contentDescription = description
                        liveRegion = LiveRegionMode.Polite
                    }
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                CrossMark(Modifier.size(16.dp))
            }
            countdown != null -> Box(Modifier.size(32.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { countdown.toFloat() / delaySeconds.coerceAtLeast(1) },
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp,
                )
                Text(countdown.toString(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CrossMark(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        drawLine(color, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
    }
}

@Composable
private fun CheckMark(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        drawLine(color, Offset(size.width * 0.15f, size.height * 0.55f), Offset(size.width * 0.4f, size.height * 0.8f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.4f, size.height * 0.8f), Offset(size.width * 0.85f, size.height * 0.25f), stroke, StrokeCap.Round)
    }
}
