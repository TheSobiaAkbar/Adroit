package io.github.thesobiaakbar.adroit.consumersmoke

import android.app.Activity
import io.github.thesobiaakbar.adroit.billing.ShowDecision
import io.github.thesobiaakbar.adroit.billing.views.AdroitPaywall

/** Compiles the View paywall call against the published adroit-billing-views. */
internal fun showSmokePaywall(activity: Activity): ShowDecision = AdroitPaywall.show(activity, SmokePaywalls.Upgrade, "smoke")
