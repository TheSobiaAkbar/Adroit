package io.github.thesobiaakbar.adroit.sample.compose.billing

import io.github.thesobiaakbar.adroit.billing.RestoreResult
import io.github.thesobiaakbar.adroit.billing.testing.FakeBilling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** PremiumViewModel on [FakeBilling]: the plain JVM, no Play, no Robolectric. */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiumViewModelTest {
    private val billing = FakeBilling()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `premium follows the entitlements`() {
        val vm = PremiumViewModel(billing)
        assertFalse(vm.entitlements.value.has("premium"))

        billing.own(SampleProducts.PremiumLifetime)

        assertTrue(vm.entitlements.value.has("premium"))
    }

    @Test
    fun `restore delivers its result once`() = runTest {
        val restored = RestoreResult.Restored(setOf("premium"), billing.entitlements.value)
        billing.restoreResult = restored
        val vm = PremiumViewModel(billing)

        vm.restore()

        assertEquals(restored, vm.restoreResults.first())
    }
}
