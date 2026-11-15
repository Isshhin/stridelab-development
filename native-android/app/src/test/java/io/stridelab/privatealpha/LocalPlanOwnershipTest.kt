package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class LocalPlanOwnershipTest {
    @Test fun deviceOwnerIsUuidBackedAndStableUnderValidation() {
        val first = LocalPlanOwnership.create()
        val other = LocalPlanOwnership.create()
        assertTrue(LocalPlanOwnership.isLocalPlanOwner(first))
        assertTrue(LocalPlanOwnership.isLocalPlanOwner(other))
        assertNotEquals(first, other)
        assertEquals(first, LocalPlanOwnership.activeOwner(null, first))
    }

    @Test fun malformedAndCloudOwnerIdsNeverPassAsDevicePlanIdentity() {
        for (id in listOf(
            "", "local-plan-", "local-garmin-e4fe", "synthetic-cloud",
            "local-plan-not-a-uuid", "local-plan-00000000-0000-0000-0000-000000000000-extra"
        )) {
            assertFalse(id, LocalPlanOwnership.isLocalPlanOwner(id))
        }
    }

    @Test fun signingInSelectsCloudOwnerWithoutReparentingDevicePlans() {
        val deviceOwner = LocalPlanOwnership.create()
        val cloud = "synthetic-cloud-athlete"
        assertEquals(cloud, LocalPlanOwnership.activeOwner(cloud, deviceOwner))
        assertTrue(LocalPlanOwnership.isVisibleOwner(deviceOwner, null, deviceOwner))
        assertFalse(LocalPlanOwnership.isVisibleOwner(deviceOwner, cloud, deviceOwner))
        assertTrue(LocalPlanOwnership.isVisibleOwner(cloud, cloud, deviceOwner))
        assertFalse(LocalPlanOwnership.isVisibleOwner(cloud, null, deviceOwner))
    }

    @Test fun twoDevicePlanNamespacesDoNotExposeEachOthersPlans() {
        val a = LocalPlanOwnership.create()
        val b = LocalPlanOwnership.create()
        assertFalse(LocalPlanOwnership.isVisibleOwner(a, null, b))
        assertFalse(LocalPlanOwnership.isVisibleOwner(b, null, a))
    }
}
