package io.github.thesobiaakbar.adroit.billing.internal.config

import io.github.thesobiaakbar.adroit.billing.BackBehavior
import io.github.thesobiaakbar.adroit.billing.CloseBehavior
import io.github.thesobiaakbar.adroit.billing.PaywallSpec

/**
 * One paywall's settings after the layers (app overrides > remote > enum defaults) and validation. Immutable: a
 * paywall keeps the instance it opened with, and a config update only affects the next opening.
 */
internal class ResolvedPaywall(val name: String, val spec: PaywallSpec) {
    /** No close button and no back: the user can only purchase or leave the app. */
    val hard: Boolean
        get() = spec.close.delaySeconds == CloseBehavior.NO_CLOSE_BUTTON && spec.close.back == BackBehavior.NEVER

    override fun equals(other: Any?): Boolean = other is ResolvedPaywall && name == other.name && spec == other.spec

    override fun hashCode(): Int = 31 * name.hashCode() + spec.hashCode()

    override fun toString(): String = "ResolvedPaywall($name, $spec)"
}
