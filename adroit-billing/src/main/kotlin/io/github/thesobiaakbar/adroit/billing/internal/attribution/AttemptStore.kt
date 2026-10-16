package io.github.thesobiaakbar.adroit.billing.internal.attribution

import android.annotation.SuppressLint
import android.content.Context
import io.github.thesobiaakbar.adroit.billing.Attribution
import org.json.JSONObject
import java.util.UUID

/**
 * A purchase attempt as recorded before its Play sheet opened. [tokenHash] is set once a purchase was matched to it,
 * so a later sync of the same purchase finds it again.
 */
internal data class Attempt(val attribution: Attribution, val createdAt: Long, val tokenHash: String? = null) {
    val id: String get() = attribution.attemptId!!
}

/**
 * Purchase attempts in SharedPreferences `adroit_billing_attempts`, one JSON record per attempt, kept for 72 h (long
 * enough for slow pending payments). Holds no purchase token and no account ID.
 *
 * Plain `edit()` rather than the KTX one: [save] needs `commit()`'s result.
 */
@SuppressLint("UseKtx")
internal class AttemptStore(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Writes synchronously, so the record survives the process dying while Play's sheet is open. */
    @SuppressLint("ApplySharedPref")
    fun save(attempt: Attempt): Boolean = prefs.edit().putString(KEY + attempt.id, attempt.toJson()).commit()

    fun remove(attemptId: String) {
        prefs.edit().remove(KEY + attemptId).apply()
    }

    /** Forgets every attempt; the install ID stays. */
    fun clear() {
        prefs.edit().apply { prefs.all.keys.filter { it.startsWith(KEY) }.forEach(::remove) }.apply()
    }

    /** Every attempt younger than 72 h. Older ones, and unreadable ones, are deleted. */
    fun all(): List<Attempt> {
        val (kept, expired) = prefs.all.filterKeys { it.startsWith(KEY) }
            .map { (key, value) -> key to (value as? String)?.let(::parse) }
            .partition { (_, attempt) -> attempt != null && now() - attempt.createdAt < TTL_MILLIS }
        if (expired.isNotEmpty()) prefs.edit().apply { expired.forEach { remove(it.first) } }.apply()
        return kept.mapNotNull { it.second }
    }

    /** A random ID of this install, for Play's account ID field when the app set no account. */
    fun installId(): String = prefs.getString(INSTALL_ID, null)
        ?: UUID.randomUUID().toString().replace("-", "").also { prefs.edit().putString(INSTALL_ID, it).apply() }

    private fun Attempt.toJson(): String = JSONObject()
        .put("attemptId", attribution.attemptId)
        .put("placement", attribution.placement)
        .putOpt("paywallId", attribution.paywallId)
        .putOpt("campaignId", attribution.campaignId)
        .put("productId", attribution.productId)
        .putOpt("basePlanId", attribution.basePlanId)
        .putOpt("offerId", attribution.offerId)
        .put("createdAt", createdAt)
        .putOpt("tokenHash", tokenHash)
        .toString()

    private fun parse(json: String): Attempt? = runCatching {
        val o = JSONObject(json)
        fun opt(key: String) = if (o.has(key)) o.getString(key) else null
        Attempt(
            Attribution(
                o.getString("attemptId"), o.getString("placement"), opt("paywallId"), opt("campaignId"),
                o.getString("productId"), opt("basePlanId"), opt("offerId"), recovered = false,
            ),
            o.getLong("createdAt"),
            opt("tokenHash"),
        )
    }.getOrNull()

    companion object {
        const val NAME = "adroit_billing_attempts"
        const val TTL_MILLIS = 72 * 3_600_000L
        private const val KEY = "attempt."
        private const val INSTALL_ID = "install_id"
    }
}
