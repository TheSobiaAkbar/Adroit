package io.github.thesobiaakbar.adroit.billing

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.internal.play.FakePlayBillingPort
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdroitBillingTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val valid = BillingSettings(TestProducts.entries, TestPaywalls.entries)
    private val invalid = BillingSettings(emptyList())

    private val port = FakePlayBillingPort()

    @Before
    fun setUp() {
        AdroitBilling.resetForTests()
        AdroitBilling.portFactory = { port }
    }

    @After
    fun tearDown() {
        setDebuggable(false)
        AdroitBilling.resetForTests()
    }

    private fun setDebuggable(debuggable: Boolean) {
        val info = application.applicationInfo
        info.flags = if (debuggable) info.flags or ApplicationInfo.FLAG_DEBUGGABLE
        else info.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    }

    @Test
    fun `flows exist before initialize and start unknown`() {
        assertEquals(EntitlementSnapshot.EMPTY_UNKNOWN, AdroitBilling.entitlements.value)
        assertEquals(BillingConnectionState.Disconnected, AdroitBilling.connection.value)
        assertEquals(CatalogState.Loading, AdroitBilling.catalog.value)
    }

    @Test
    fun `calls before initialize report NotInitialized instead of throwing`() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val result = AdroitBilling.purchase(activity, PurchaseRequest(TestProducts.PremiumSub, placement = "home"))
        assertEquals(PurchaseResult.Failed(BillingError.NotInitialized, null), result)
        assertEquals(RestoreResult.Failed(BillingError.NotInitialized), AdroitBilling.restore())
        assertFalse(AdroitBilling.openManageSubscriptions(activity))
    }

    @Test
    fun `valid settings are kept`() {
        AdroitBilling.initialize(application, valid)
        assertSame(valid, AdroitBilling.settings)
    }

    @Test
    fun `invalid settings throw in a debuggable app, listing the problem`() {
        setDebuggable(true)
        val error = assertThrows(IllegalStateException::class.java) { AdroitBilling.initialize(application, invalid) }
        assertTrue(error.message!!.contains("products is empty"))
        assertNull(AdroitBilling.settings)
    }

    @Test
    fun `invalid settings leave billing off in a release app, without throwing`() {
        setDebuggable(false)
        AdroitBilling.initialize(application, invalid)
        assertNull(AdroitBilling.settings)
    }

    @Test
    fun `a second initialize keeps the first settings`() {
        AdroitBilling.initialize(application, valid)
        AdroitBilling.initialize(application, BillingSettings(listOf(TestProducts.Coins)))
        assertSame(valid, AdroitBilling.settings)
    }

    @Test
    fun `setAccount before initialize is remembered`() {
        AdroitBilling.setAccount("user-1")
        assertEquals("user-1", AdroitBilling.requestedAccount)
    }

    @Test
    fun `initialize connects to Play`() {
        AdroitBilling.initialize(application, valid)
        assertEquals(BillingConnectionState.Connected, AdroitBilling.connection.value)
        assertEquals(1, port.connectCalls)
    }

    @Test
    fun `refresh reconnects after billing was unavailable`() = runTest {
        port.connectResults += PlayResult.Failed(BillingError.Unavailable(3))
        AdroitBilling.initialize(application, valid)
        assertTrue(AdroitBilling.connection.value is BillingConnectionState.Unavailable)
        AdroitBilling.refresh()
        assertEquals(BillingConnectionState.Connected, AdroitBilling.connection.value)
    }

    @Test
    fun `only shutdownForTests ends the connection`() {
        AdroitBilling.initialize(application, valid)
        Robolectric.buildActivity(Activity::class.java).setup().destroy()
        assertEquals(0, port.endConnectionCalls)
        AdroitBilling.shutdownForTests()
        assertEquals(1, port.endConnectionCalls)
        assertEquals(BillingConnectionState.Disconnected, AdroitBilling.connection.value)
    }
}
