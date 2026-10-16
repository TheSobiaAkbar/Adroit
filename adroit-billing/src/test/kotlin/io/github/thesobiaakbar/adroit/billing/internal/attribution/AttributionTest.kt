package io.github.thesobiaakbar.adroit.billing.internal.attribution

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchase
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchaseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AttributionTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private var now = 0L
    private val store = AttemptStore(application, now = { now })
    private val matcher = AttributionMatcher(store)

    private fun attempt(id: String, product: String, placement: String, at: Long) =
        Attempt(Attribution(id, placement, "Onboarding", "spring", product, "monthly", "trial", recovered = false), at)
            .also { store.save(it) }

    private fun purchase(product: String, token: String, profileId: String? = null) = PlayPurchase(
        listOf(product), token, "GPA.1", 0, PlayPurchaseState.PURCHASED, 1, isAcknowledged = false,
        isAutoRenewing = true, isSuspended = false, obfuscatedAccountId = null, obfuscatedProfileId = profileId,
        originalJson = "{}", signature = "",
    )

    @Test
    fun `an attempt survives the round trip through SharedPreferences`() {
        val saved = attempt("a-1", "premium_sub", "home", at = 5)
        assertEquals(listOf(saved), AttemptStore(application, now = { now }).all())
    }

    @Test
    fun `attempts are pruned once 72 hours old`() {
        attempt("a-1", "premium_sub", "home", at = 0)
        now = AttemptStore.TTL_MILLIS - 1
        assertEquals(1, store.all().size)
        now = AttemptStore.TTL_MILLIS
        assertEquals(emptyList<Attempt>(), store.all())
        val prefs = application.getSharedPreferences(AttemptStore.NAME, 0)
        assertFalse(prefs.all.keys.any { it.startsWith("attempt.") })
    }

    @Test
    fun `the profile ID Play echoes back picks its attempt, even an older one`() {
        val older = attempt("0000-1111", "premium_sub", "onboarding", at = 1)
        attempt("2222-3333", "premium_sub", "settings", at = 2)
        assertEquals(older.attribution, matcher.match(purchase("premium_sub", "t1", profileId = "00001111")))
    }

    @Test
    fun `without a profile ID the newest attempt of the product is picked`() {
        attempt("a-1", "premium_sub", "onboarding", at = 1)
        val newest = attempt("a-2", "premium_sub", "settings", at = 2)
        attempt("a-3", "premium_lifetime", "home", at = 3)
        assertEquals(newest.attribution, matcher.match(purchase("premium_sub", "t1")))
    }

    @Test
    fun `a purchase matching no attempt is unknown, never another product's placement`() {
        attempt("a-1", "premium_lifetime", "home", at = 1)
        assertEquals(Attribution.unknown("premium_sub"), matcher.match(purchase("premium_sub", "t1")))
    }

    @Test
    fun `a matched attempt stays with its purchase and no other purchase takes it`() {
        val saved = attempt("a-1", "premium_sub", "home", at = 1)
        assertEquals(saved.attribution, matcher.match(purchase("premium_sub", "t1")))
        assertEquals(Attribution.unknown("premium_sub"), matcher.match(purchase("premium_sub", "t2")))
        assertEquals(saved.attribution, AttributionMatcher(AttemptStore(application, now = { now })).match(purchase("premium_sub", "t1")))
    }

    @Test
    fun `no purchase token is stored`() {
        attempt("a-1", "premium_sub", "home", at = 1)
        matcher.match(purchase("premium_sub", "secret-token"))
        val stored = application.getSharedPreferences(AttemptStore.NAME, 0).all.values.joinToString()
        assertFalse(stored.contains("secret-token"))
    }
}
