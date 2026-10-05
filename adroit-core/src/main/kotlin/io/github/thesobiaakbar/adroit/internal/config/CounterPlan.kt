package io.github.thesobiaakbar.adroit.internal.config

import kotlin.math.ceil

/**
 * An ad's counter (docs/AD_PROFILES.md, "Counter"): each field comes from the ad's Firebase value, then the ad's
 * definition, then its default. Null means "not set here". Immutable.
 *
 * The counter starts a session at [start]; every `show` call adds [step]; the ad is due once the counter reaches the
 * current entry of [maxes]. A real showing puts the counter back to 0 and moves to the next entry of [maxes] (after
 * the last, the first again).
 *
 * @property maxes the max counters, in order; none above 0 turns the counter off.
 * @property start the counter's value when the session starts (default 0).
 * @property step what each `show` call adds (default 1).
 * @property resetOnFail what a due ad that isn't shown does: false (the default) keeps the counter, so the very next
 *   call tries again; true puts it back to 0.
 * @property loadAhead how many calls before it's due the ad is loaded: 0 = on the call it's due, 1 (the default) = one
 *   call earlier, and so on.
 */
internal data class CounterPlan(
    val maxes: List<Double>? = null,
    val start: Double? = null,
    val step: Double? = null,
    val resetOnFail: Boolean? = null,
    val loadAhead: Int? = null,
) {
    /** This, with each unset field taken from [fallback]. */
    fun orElse(fallback: CounterPlan?): CounterPlan = if (fallback == null) this else CounterPlan(
        maxes ?: fallback.maxes,
        start ?: fallback.start,
        step ?: fallback.step,
        resetOnFail ?: fallback.resetOnFail,
        loadAhead ?: fallback.loadAhead,
    )

    /** Null when nothing is set, so a value without counter fields changes nothing. */
    fun takeIfSet(): CounterPlan? = takeUnless { it == CounterPlan() }

    /** For an ad's own definition, under which there's nothing: a field at its default is the same as unset. */
    fun withoutDefaults(): CounterPlan = CounterPlan(
        maxes,
        start?.takeIf { it != 0.0 },
        step?.takeIf { it != 1.0 },
        resetOnFail?.takeIf { it },
        loadAhead?.takeIf { it != DEFAULT_LOAD_AHEAD },
    )

    /** Null when it says no more than a plain `counter` number does (null: no counter set), which then carries it. */
    fun beyond(counter: Int?): CounterPlan? =
        takeUnless { it == CounterPlan(maxes = counter?.let { n -> listOf(n.toDouble()) }) }

    /** The max counters in force. Empty: the counter is off. */
    val thresholds: List<Double> = maxes.orEmpty().filter { it > 0 }

    val isOn: Boolean get() = thresholds.isNotEmpty()

    val startValue: Double get() = start ?: 0.0

    val stepValue: Double get() = step ?: 1.0

    val loadAheadCalls: Int get() = loadAhead ?: DEFAULT_LOAD_AHEAD

    /** The first max counter as the whole number `FrequencyRule.counter` holds. */
    val legacyCounter: Int get() = thresholds.firstOrNull()?.let { ceil(it).toInt() } ?: 0

    companion object {
        const val DEFAULT_LOAD_AHEAD: Int = 1
    }
}
