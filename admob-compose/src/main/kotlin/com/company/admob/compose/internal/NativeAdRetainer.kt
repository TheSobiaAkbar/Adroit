package com.company.admob.compose.internal

import androidx.lifecycle.ViewModel
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

        fun release() {
            released = true
            (result.value as? NativeAdResult.Loaded)?.handle?.destroy()
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
