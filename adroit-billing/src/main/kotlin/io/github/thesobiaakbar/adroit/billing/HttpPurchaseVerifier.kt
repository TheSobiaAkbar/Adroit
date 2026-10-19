package io.github.thesobiaakbar.adroit.billing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A [PurchaseVerifier] and [EntitlementProvider] that call the app's backend as docs/BILLING_BACKEND.md describes:
 *
 * ```
 * val backend = HttpPurchaseVerifier(
 *     url = "https://api.example.com/billing/verify",
 *     entitlementsUrl = "https://api.example.com/billing/entitlements",
 *     headers = { mapOf("Authorization" to "Bearer ${auth.freshIdToken()}") },
 * )
 * BillingSettings(…, verifier = backend, entitlementProvider = backend)
 * ```
 *
 * HTTPS only, no redirects followed, responses of at most 64 KB. A 5xx or any other non-2xx status, a timeout, an IO
 * error or a response it can't read is [VerificationResult.Unreachable]: nothing is granted and the purchase is
 * verified again on the next sync. A `VERIFIED` answer for other products than the purchase's is
 * [VerificationResult.Rejected] ("product mismatch"). The request carries the [PurchaseToVerify.accountIdHash], never
 * a raw account ID. Network calls run on [Dispatchers.IO].
 *
 * @param url the `POST /verify` endpoint.
 * @param headers the request headers, called before every request (so an expired auth token can be refreshed). An
 *   exception from it is [VerificationResult.Unreachable].
 * @param entitlementsUrl the `GET /entitlements` endpoint; null makes [fetch] answer null (Play's data alone).
 * @param timeout the connect timeout, and the read timeout.
 * @param allowLocalHttp allows `http://` to `localhost`, `127.0.0.1` and `10.0.2.2` (the emulator's host), for a local
 *   backend in development. Pass `BuildConfig.DEBUG`, never true in a release build.
 * @throws IllegalArgumentException when a URL isn't HTTPS (or allowed local HTTP).
 */
public class HttpPurchaseVerifier @JvmOverloads constructor(
    private val url: String,
    private val headers: suspend () -> Map<String, String> = { emptyMap() },
    private val entitlementsUrl: String? = null,
    private val timeout: Duration = 15.seconds,
    private val allowLocalHttp: Boolean = false,
) : PurchaseVerifier, EntitlementProvider {
    init {
        requireSecure(url)
        entitlementsUrl?.let(::requireSecure)
        require(timeout.isPositive()) { "timeout must be positive, was $timeout" }
    }

    override suspend fun verify(purchase: PurchaseToVerify): VerificationResult {
        val response = try {
            request(url, verifyBody(purchase).toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return VerificationResult.Unreachable(e.javaClass.simpleName)
        }
        if (response.status !in 200..299) return VerificationResult.Unreachable("HTTP ${response.status}")
        return try {
            verification(JSONObject(response.body), purchase.productIds)
        } catch (e: JSONException) {
            VerificationResult.Unreachable("malformed response")
        }
    }

    override suspend fun fetch(accountIdHash: String?): List<EntitlementGrant>? {
        val base = entitlementsUrl ?: return null
        val target = accountIdHash?.let { base + (if ('?' in base) "&" else "?") + "account=" + URLEncoder.encode(it, "UTF-8") } ?: base
        return try {
            val response = request(target, body = null)
            if (response.status !in 200..299) return null
            grants(JSONObject(response.body))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override fun toString(): String = "HttpPurchaseVerifier(url=$url, entitlementsUrl=$entitlementsUrl)"

    private class Response(val status: Int, val body: String)

    /** POSTs [body] as JSON, or GETs when it is null. */
    private suspend fun request(target: String, body: String?): Response {
        val requestHeaders = headers()
        return withContext(Dispatchers.IO) {
            val connection = URL(target).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = timeout.inWholeMilliseconds.toInt()
                connection.readTimeout = timeout.inWholeMilliseconds.toInt()
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                requestHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                if (body != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toByteArray()) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                Response(status, stream?.use(::readLimited).orEmpty())
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun requireSecure(target: String) {
        val parsed = URL(target)
        val localHttp = allowLocalHttp && parsed.protocol == "http" && parsed.host in LOCAL_HOSTS
        require(parsed.protocol == "https" || localHttp) {
            "Backend URL must be https://" + if (allowLocalHttp) " (or http:// to ${LOCAL_HOSTS.joinToString()})" else ""
        }
    }
}

private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

private const val MAX_RESPONSE_BYTES = 64 * 1024

private const val MAX_REASON = 200

private fun readLimited(stream: InputStream): String {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
        if (out.size() > MAX_RESPONSE_BYTES) throw IOException("response over 64 KB")
    }
    return out.toString("UTF-8")
}

private fun verifyBody(purchase: PurchaseToVerify): JSONObject = JSONObject()
    .put("packageName", purchase.packageName)
    .put("purchaseToken", purchase.purchaseToken)
    .put("productIds", JSONArray(purchase.productIds))
    .put("type", purchase.type.name)
    .put("accountIdHash", purchase.accountIdHash ?: JSONObject.NULL)
    .put(
        "attribution",
        purchase.attribution.let {
            JSONObject()
                .put("placement", it.placement)
                .put("paywallId", it.paywallId ?: JSONObject.NULL)
                .put("campaignId", it.campaignId ?: JSONObject.NULL)
                .put("productId", it.productId)
                .put("basePlanId", it.basePlanId ?: JSONObject.NULL)
                .put("offerId", it.offerId ?: JSONObject.NULL)
                .put("attemptId", it.attemptId ?: JSONObject.NULL)
                .put("recovered", it.recovered)
        },
    )

private fun verification(json: JSONObject, requested: List<String>): VerificationResult =
    when (val status = json.getString("status")) {
        "VERIFIED" -> {
            val products = json.getJSONArray("productIds").let { array -> List(array.length()) { array.getString(it) } }
            if (products.toSet() != requested.toSet()) {
                VerificationResult.Rejected("product mismatch")
            } else {
                VerificationResult.Verified(acknowledgedByBackend = json.optBoolean("acknowledged", false))
            }
        }
        "REJECTED" -> VerificationResult.Rejected(json.optString("reason", "rejected").take(MAX_REASON))
        else -> VerificationResult.Unreachable("unknown status ${status.take(MAX_REASON)}")
    }

/** The `grants` array; an entry it can't read is left out. */
private fun grants(json: JSONObject): List<EntitlementGrant> {
    val array = json.getJSONArray("grants")
    return (0 until array.length()).mapNotNull { index ->
        try {
            val grant = array.getJSONObject(index)
            val entitlements = grant.getJSONArray("entitlements")
            EntitlementGrant(
                productId = grant.getString("productId"),
                type = ProductType.valueOf(grant.getString("type")),
                entitlements = List(entitlements.length()) { entitlements.getString(it) }.toSet(),
                state = GrantState.valueOf(grant.getString("state")),
                source = GrantSource.BACKEND,
                purchaseTimeMillis = grant.getLong("purchaseTimeMillis"),
            )
        } catch (e: JSONException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
