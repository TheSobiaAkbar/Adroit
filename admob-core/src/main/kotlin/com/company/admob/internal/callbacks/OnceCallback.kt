package com.company.admob.internal.callbacks

import com.company.admob.internal.threading.postToMain
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Delivers a result to [callback] at most once, even if [invoke] races from several threads (for example, an SDK
 * callback against a watchdog timeout). Delivery goes through [dispatch], which posts to the main thread by
 * default, so hosts never receive a synchronous callback. The callback reference is dropped after delivery.
 */
internal class OnceCallback<T>(
    callback: (T) -> Unit,
    private val dispatch: (() -> Unit) -> Unit = ::postToMain,
) {
    private val invoked = AtomicBoolean(false)

    @Volatile
    private var callback: ((T) -> Unit)? = callback

    val isInvoked: Boolean
        get() = invoked.get()

    /** Delivers [value] if this is the first call. Returns true if it was delivered, false if already invoked. */
    operator fun invoke(value: T): Boolean {
        if (!invoked.compareAndSet(false, true)) return false
        val target = callback ?: return true
        callback = null
        dispatch { target(value) }
        return true
    }
}
