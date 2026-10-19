package io.github.thesobiaakbar.adroit.billing

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress
import kotlin.time.Duration.Companion.milliseconds

/** Against a JDK `HttpServer` on 127.0.0.1 (no new dependency); Robolectric for the real `org.json`. */
@RunWith(RobolectricTestRunner::class)
class HttpPurchaseVerifierTest {
    private class Received(val method: String, val path: String, val headers: Map<String, String?>, val body: String)

    private val received = mutableListOf<Received>()
    private var status = 200
    private var response = ""
    private var delayMillis = 0L
    private var redirectTo: String? = null

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            received += Received(
                exchange.requestMethod,
                exchange.requestURI.toString(),
                exchange.requestHeaders.mapValues { it.value.firstOrNull() },
                exchange.requestBody.readBytes().decodeToString(),
            )
            Thread.sleep(delayMillis)
            redirectTo?.let { exchange.responseHeaders.add("Location", it) }
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}"
    private var token = 0

    private val verifier = HttpPurchaseVerifier(
        url = "$base/verify",
        headers = { mapOf("Authorization" to "Bearer t${++token}") },
        entitlementsUrl = "$base/entitlements",
        timeout = 300.milliseconds,
        allowLocalHttp = true,
    )

    private val purchase = PurchaseToVerify(
        listOf("premium_sub"), ProductType.SUBSCRIPTION, "TOKEN-123", "com.example",
        Attribution("a-1", "home", "Onboarding", null, "premium_sub", "monthly", null, false), "hash-abc",
    )

    @After
    fun stop() = server.stop(0)

    private fun verify() = runBlocking { verifier.verify(purchase) }

    @Test
    fun `verified, with the request the contract describes`() {
        response = """{"status":"VERIFIED","acknowledged":true,"productIds":["premium_sub"],"entitlements":["premium"]}"""

        assertEquals(VerificationResult.Verified(acknowledgedByBackend = true), verify())

        val request = received.single()
        assertEquals("POST", request.method)
        assertEquals("/verify", request.path)
        val body = JSONObject(request.body)
        assertEquals("TOKEN-123", body.getString("purchaseToken"))
        assertEquals("com.example", body.getString("packageName"))
        assertEquals("SUBSCRIPTION", body.getString("type"))
        assertEquals("premium_sub", body.getJSONArray("productIds").getString(0))
        assertEquals("hash-abc", body.getString("accountIdHash"))
        assertEquals("home", body.getJSONObject("attribution").getString("placement"))
        assertTrue(body.getJSONObject("attribution").isNull("campaignId"))
    }

    @Test
    fun `rejected keeps the reason`() {
        response = """{"status":"REJECTED","reason":"token bound to another account"}"""

        assertEquals(VerificationResult.Rejected("token bound to another account"), verify())
    }

    @Test
    fun `a 500, a 401 and a redirect are unreachable`() {
        status = 500
        assertEquals(VerificationResult.Unreachable("HTTP 500"), verify())
        status = 401
        assertEquals(VerificationResult.Unreachable("HTTP 401"), verify())
        status = 302
        redirectTo = "http://evil.example/verify"
        assertEquals(VerificationResult.Unreachable("HTTP 302"), verify())
        assertEquals(3, received.size) // The redirect wasn't followed.
    }

    @Test
    fun `a timeout is unreachable`() {
        delayMillis = 1_000
        response = """{"status":"VERIFIED","productIds":["premium_sub"]}"""

        assertTrue(verify() is VerificationResult.Unreachable)
    }

    @Test
    fun `malformed or oversized JSON is unreachable`() {
        response = "<html>oops</html>"
        assertEquals(VerificationResult.Unreachable("malformed response"), verify())
        response = """{"status":"VERIFIED"}""" // No productIds.
        assertEquals(VerificationResult.Unreachable("malformed response"), verify())
        response = """{"status":"REJECTED","reason":"${"x".repeat(70_000)}"}"""
        assertEquals(VerificationResult.Unreachable("IOException"), verify())
    }

    @Test
    fun `verified for another product is a product mismatch`() {
        response = """{"status":"VERIFIED","acknowledged":false,"productIds":["coins_100"]}"""

        assertEquals(VerificationResult.Rejected("product mismatch"), verify())
    }

    @Test
    fun `headers are called for every request`() {
        response = """{"status":"VERIFIED","productIds":["premium_sub"]}"""
        verify()
        verify()

        assertEquals(listOf("Bearer t1", "Bearer t2"), received.map { it.headers["Authorization"] })
    }

    @Test
    fun `a throwing headers lambda is unreachable`() {
        val failing = HttpPurchaseVerifier("$base/verify", headers = { error("no session") }, allowLocalHttp = true)

        assertTrue(runBlocking { failing.verify(purchase) } is VerificationResult.Unreachable)
        assertTrue(received.isEmpty())
    }

    @Test
    fun `entitlements are read by account hash, unreadable grants are left out`() {
        response = """{"grants":[
            {"productId":"premium_sub","type":"SUBSCRIPTION","entitlements":["premium"],"state":"GRACE_FROM_BACKEND","purchaseTimeMillis":5},
            {"productId":"bad","type":"NOPE","entitlements":[],"state":"ACTIVE","purchaseTimeMillis":1}
        ]}"""

        val grants = runBlocking { verifier.fetch("hash-abc") }

        assertEquals("GET", received.single().method)
        assertEquals("/entitlements?account=hash-abc", received.single().path)
        val grant = grants!!.single()
        assertEquals(GrantState.GRACE_FROM_BACKEND, grant.state)
        assertEquals(GrantSource.BACKEND, grant.source)
        assertEquals(setOf("premium"), grant.entitlements)
    }

    @Test
    fun `entitlements answer null on failure or without a URL`() {
        status = 503
        assertNull(runBlocking { verifier.fetch("hash-abc") })
        val noUrl = HttpPurchaseVerifier("https://api.example.com/verify")
        assertNull(runBlocking { noUrl.fetch("hash-abc") })
    }

    @Test
    fun `only https, or local http when allowed`() {
        assertThrows(IllegalArgumentException::class.java) { HttpPurchaseVerifier("http://api.example.com/verify") }
        assertThrows(IllegalArgumentException::class.java) { HttpPurchaseVerifier("$base/verify") }
        assertThrows(IllegalArgumentException::class.java) {
            HttpPurchaseVerifier("http://api.example.com/verify", allowLocalHttp = true)
        }
        HttpPurchaseVerifier("http://10.0.2.2:8080/verify", allowLocalHttp = true)
        assertFalse(HttpPurchaseVerifier("https://api.example.com/verify").toString().contains("TOKEN"))
    }
}
