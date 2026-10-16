package io.github.thesobiaakbar.adroit.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementSnapshotTest {
    private fun grant(state: GrantState, entitlements: Set<String> = setOf("premium", "ad_free")) =
        EntitlementGrant("premium_sub", ProductType.SUBSCRIPTION, entitlements, state, GrantSource.CLIENT, 1L)

    private fun snapshot(status: SnapshotStatus, vararg grants: EntitlementGrant) =
        EntitlementSnapshot(grants.toList(), status, adFreeEntitlements = setOf("ad_free"))

    @Test
    fun `fresh and cached snapshots grant active entitlements`() {
        listOf(SnapshotStatus.Fresh(10), SnapshotStatus.Cached(5)).forEach { status ->
            val snapshot = snapshot(status, grant(GrantState.ACTIVE))
            assertTrue(snapshot.has("premium"))
            assertTrue(snapshot.adFree)
            assertEquals(setOf("premium", "ad_free"), snapshot.active)
        }
    }

    @Test
    fun `an unknown snapshot grants nothing, even with active grants`() {
        val snapshot = snapshot(SnapshotStatus.Unknown, grant(GrantState.ACTIVE))
        assertFalse(snapshot.has("premium"))
        assertFalse(snapshot.adFree)
        assertTrue(snapshot.active.isEmpty())
    }

    @Test
    fun `pending and suspended grants give no access, grace from the backend does`() {
        assertFalse(snapshot(SnapshotStatus.Fresh(1), grant(GrantState.PENDING)).has("premium"))
        assertFalse(snapshot(SnapshotStatus.Fresh(1), grant(GrantState.SUSPENDED)).has("premium"))
        assertTrue(snapshot(SnapshotStatus.Fresh(1), grant(GrantState.GRACE_FROM_BACKEND)).has("premium"))
    }

    @Test
    fun `adFree needs one of the ad-free entitlements`() {
        assertFalse(snapshot(SnapshotStatus.Fresh(1), grant(GrantState.ACTIVE, setOf("premium"))).adFree)
    }

    @Test
    fun `the empty snapshot is unknown and grants nothing`() {
        assertEquals(SnapshotStatus.Unknown, EntitlementSnapshot.EMPTY_UNKNOWN.status)
        assertFalse(EntitlementSnapshot.EMPTY_UNKNOWN.has("premium"))
        assertFalse(EntitlementSnapshot.EMPTY_UNKNOWN.adFree)
    }
}
