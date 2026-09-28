package com.company.admob.compose.internal

import androidx.lifecycle.ViewModel
import com.company.admob.NativeAdHandle
import com.company.admob.NativeAdResult
import com.company.admob.NativePlacement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps the native ads of one screen (one `ViewModelStoreOwner`) across configuration changes, one per
 * (placement, slotKey). Loads a slot's ad on its first request only, and destroys every ad in [onCleared].
 *
 * At most [maxRetained] ads are kept (loaded or loading): a new slot beyond that destroys the least recently used
 * one, preferring slots that aren't on screen. A slot whose load failed isn't kept, so a later request retries it.
 * Main thread only.
 */
internal class NativeAdRetainer(private val maxRetained: Int = MAX_RETAINED_PER_SCREEN) : ViewModel() {
    private data class Key(val placement: NativePlacement, val slotKey: Any)

    private class Slot {
        val result = MutableStateFlow<NativeAdResult?>(null)
        var users = 0
        var released = false

        /** A refresh request is under way; the current ad stays until it answers. */
        var refreshing = false

        /** Ads replaced by a refresh, destroyed once their successor is drawn ([onReplaced]). */
        val retired = mutableListOf<NativeAdHandle>()

        fun release() {
            released = true
            (result.value as? NativeAdResult.Loaded)?.handle?.destroy()
            retired.forEach { it.destroy() }
            retired.clear()
            result.value = null
        }
    }

    /** In least recently used order: the first entry is the next to evict. */
    private val slots = LinkedHashMap<Key, Slot>()

    /** The number of slots holding or loading an ad. */
    val retainedCount: Int get() = slots.size

    /** The slot's current result without loading or touching it, or null. */
    fun peek(placement: NativePlacement, slotKey: Any): NativeAdResult? = slots[Key(placement, slotKey)]?.result?.value

    /** Marks the slot as on screen and most recently used, loading its ad if it has none. Pair with [release]. */
    fun acquire(placement: NativePlacement, slotKey: Any): StateFlow<NativeAdResult?> {
        val key = Key(placement, slotKey)
        val slot = slots.remove(key) ?: Slot().also { load(key, it) }
        slots[key] = slot
        slot.users++
        trim()
        return slot.result
    }

    /** The slot [acquired] from [acquire] left the screen; its ad stays retained. */
    fun release(placement: NativePlacement, slotKey: Any, acquired: StateFlow<NativeAdResult?>) {
        slots[Key(placement, slotKey)]?.takeIf { it.result === acquired }?.let { it.users = maxOf(0, it.users - 1) }
    }

    /**
     * Replaces the slot's shown ad with a new one (PROMPT 29D): the new ad loads while the current one stays, and
     * takes its place when it arrives; if it fails, the current one stays. Returns false when the slot has no ad and
     * isn't loading (its load failed, or it was evicted): the caller then acquires it again, which loads anew. A slot
     * that is loading or refreshing already is left alone.
     */
    fun refresh(placement: NativePlacement, slotKey: Any): Boolean {
        val slot = slots[Key(placement, slotKey)] ?: return false
        if (slot.refreshing || slot.result.value == null) return true
        val old = (slot.result.value as? NativeAdResult.Loaded)?.handle ?: return false
        slot.refreshing = true
        ComposeRuntime.client.native.load(placement) { result ->
            slot.refreshing = false
            when {
                slot.released -> (result as? NativeAdResult.Loaded)?.handle?.destroy()
                result is NativeAdResult.Loaded -> {
                    slot.result.value = result
                    // On screen, the old ad goes once the new one is drawn; off screen, now.
                    if (slot.users > 0) slot.retired += old else old.destroy()
                }
                else -> ComposeRuntime.debug("Native slot ${placement.key}/$slotKey: refresh not loaded; keeping the current ad")
            }
        }
        return true
    }

    /** [replaced] is no longer drawn: destroys it if a refresh retired it. */
    fun onReplaced(replaced: NativeAdHandle) {
        for (slot in slots.values) {
            if (slot.retired.remove(replaced)) replaced.destroy()
        }
    }

    private fun load(key: Key, slot: Slot) {
        ComposeRuntime.client.native.load(key.placement) { result ->
            when {
                // Evicted or cleared while loading: nobody will show or destroy it.
                slot.released -> (result as? NativeAdResult.Loaded)?.handle?.destroy()
                result is NativeAdResult.Failed -> {
                    if (slots[key] === slot) slots.remove(key)
                    slot.result.value = result
                }
                else -> slot.result.value = result
            }
        }
    }

    private fun trim() {
        while (slots.size > maxRetained) {
            val victim = slots.entries.firstOrNull { it.value.users == 0 } ?: slots.entries.first()
            ComposeRuntime.debug("Native slot ${victim.key.placement.key}/${victim.key.slotKey} evicted (max $maxRetained)")
            slots.remove(victim.key)
            victim.value.release()
        }
    }

    override fun onCleared() {
        slots.values.forEach { it.release() }
        slots.clear()
    }

    companion object {
        const val VIEW_MODEL_KEY: String = "company_admob_native"
        const val MAX_RETAINED_PER_SCREEN: Int = 3
    }
}
