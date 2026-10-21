package io.github.thesobiaakbar.adroit.update.internal

import android.app.Activity
import io.github.thesobiaakbar.adroit.update.UpdateType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What Play says about an update, without Play types, so the logic is testable. [handle] is Play's own info. */
internal class UpdateInfo(
    val available: Boolean,
    /** A forced (immediate) update started earlier and not finished: resume it. */
    val inProgress: Boolean = false,
    val immediateAllowed: Boolean = false,
    val flexibleAllowed: Boolean = false,
    /** A flexible update downloaded in an earlier session, waiting to be installed. */
    val downloaded: Boolean = false,
    val handle: Any? = null,
)

internal enum class InstallProgress { DOWNLOADED, ENDED, OTHER }

/** The part of Play's `AppUpdateManager` the coordinator uses. */
internal interface UpdatePort {
    /** Throws when Play is unavailable or the request fails. */
    suspend fun info(): UpdateInfo

    /** Shows Play's flow and returns its Activity result code. Throws when it can't start. */
    suspend fun startFlow(info: UpdateInfo, activity: Activity, immediate: Boolean): Int

    /** Listens to the flexible download; returns the removal function. */
    fun listen(onProgress: (InstallProgress) -> Unit): () -> Unit

    fun complete()
}

internal class UpdateConfig(val type: UpdateType, val forceBelowVersionCode: Long)

/** `AdroitUpdate.check`'s decisions (docs/APP_UPDATE.md). Main thread. */
internal class UpdateCoordinator(
    private val port: UpdatePort,
    private val readConfig: () -> String?,
    private val awaitFetched: suspend () -> Unit,
    private val installedVersionCode: Long,
    private val ready: MutableStateFlow<Boolean>,
    private val warn: (String) -> Unit,
    private val fetchTimeout: Duration = 2.seconds,
    private val infoTimeout: Duration = 5.seconds,
) {
    private var stopListening: (() -> Unit)? = null

    suspend fun check(activity: Activity, default: UpdateType): Unit = coroutineScope {
        // Play and this session's Remote Config fetch, in parallel. With nothing to update, return at once: the type
        // doesn't matter then, so a cold start doesn't wait for the fetch.
        val fetched = async { withTimeoutOrNull(fetchTimeout) { awaitFetched() } }
        val update = info()
        if (update == null || !(update.available || update.inProgress || update.downloaded)) {
            fetched.cancel()
            return@coroutineScope
        }
        fetched.await()
        val config = parse(readConfig(), default)
        if (config.type == UpdateType.OFF) return@coroutineScope
        val force = config.type == UpdateType.FORCE ||
            (config.forceBelowVersionCode > 0 && installedVersionCode < config.forceBelowVersionCode)
        if (activity.isFinishing || activity.isDestroyed) return@coroutineScope
        if (force) {
            if (update.inProgress || (update.available && update.immediateAllowed)) {
                val result = start(update, activity, immediate = true) ?: return@coroutineScope
                if (result != Activity.RESULT_OK) {
                    warn("Forced update declined (result $result): closing the app")
                    activity.finishAffinity()
                    awaitCancellation()
                }
                return@coroutineScope
            }
            if (update.available) warn("Play doesn't allow a forced update now; offering a flexible one")
        }
        if (update.downloaded) {
            port.complete()
            return@coroutineScope
        }
        if (update.available && update.flexibleAllowed) {
            listen()
            start(update, activity, immediate = false)
        }
    }

    fun install() {
        if (ready.value) port.complete()
    }

    private suspend fun info(): UpdateInfo? = try {
        withTimeoutOrNull(infoTimeout) { port.info() } ?: null.also { warn("No update check: Play didn't answer") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A sideloaded or debug build, no Play, offline.
        warn("No update check: ${e.message}")
        null
    }

    /** Process-wide and idempotent: one listener until the download ends. */
    private fun listen() {
        if (stopListening != null) return
        stopListening = port.listen { progress ->
            when (progress) {
                InstallProgress.DOWNLOADED -> ready.value = true
                InstallProgress.ENDED -> {
                    ready.value = false
                    stopListening?.invoke()
                    stopListening = null
                }
                InstallProgress.OTHER -> Unit
            }
        }
    }

    private suspend fun start(update: UpdateInfo, activity: Activity, immediate: Boolean): Int? = try {
        port.startFlow(update, activity, immediate)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        warn("Play's update flow didn't start: ${e.message}")
        null
    }

    private fun parse(raw: String?, default: UpdateType): UpdateConfig {
        if (raw.isNullOrBlank()) return UpdateConfig(default, 0)
        val json = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            warn("Firebase AppUpdate ignored: not a JSON object")
            return UpdateConfig(default, 0)
        }
        val text = if (json.isNull("type")) null else json.opt("type")
        val type = if (text == null) {
            default
        } else {
            UpdateType.entries.firstOrNull { (text as? String)?.trim().equals(it.name, ignoreCase = true) }
        } ?: default.also { warn("Firebase AppUpdate \"type\" must be flexible, force or off; ignored") }
        val value = if (json.isNull("forceBelowVersionCode")) 0 else json.opt("forceBelowVersionCode")
        val below = (value as? Number)?.toDouble()
            ?.takeIf { it >= 0 && it == Math.floor(it) && it <= Long.MAX_VALUE }?.toLong() ?: 0L.also { warn("Firebase AppUpdate \"forceBelowVersionCode\" must be a whole number, 0 or more; ignored") }
        return UpdateConfig(type, below)
    }
}
