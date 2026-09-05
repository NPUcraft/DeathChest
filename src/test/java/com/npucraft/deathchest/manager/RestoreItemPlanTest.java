package com.npucraft.deathchest.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreItemPlanTest {
    @Test
    void safeRefusesWhenItemsAreInRecovery() {
        RestoreItemPlan.Decision decision = RestoreItemPlan.decide(true, false, true, false, true, false, null);
        assertTrue(decision.refuse());
        assertEquals("restore-safe-in-recovery", decision.refuseKey());
    }

    @Test
    void safeUsesDedicatedMessageForVirtualStorage() {
        RestoreItemPlan.Decision decision = RestoreItemPlan.decide(
                true, false, false, false, false, false, "VIRTUAL_STORAGE");
        assertEquals("restore-safe-virtual-storage", decision.refuseKey());
    }

    @Test
    void safeRefusesNormalDropWithoutChest() {
        RestoreItemPlan.Decision decision = RestoreItemPlan.decide(true, false, false, false, false, false, "NORMAL_DROP");
        assertEquals("restore-safe-never-created", decision.refuseKey());
    }

    @Test
    void forceDoesNotRegrantSnapshotAfterPartialLoot() {
        RestoreItemPlan.Decision decision = RestoreItemPlan.decide(true, true, true, true, false, false, null);
        assertFalse(decision.refuse());
        assertTrue(decision.useChestContents());
        assertFalse(decision.useSnapshot());
    }

    @Test
    void forceDoesNotRegrantSnapshotWhenRecoveryIsPending() {
        RestoreItemPlan.Decision decision = RestoreItemPlan.decide(true, true, true, false, true, false, null);
        assertFalse(decision.refuse());
        assertTrue(decision.useRecovery());
        assertFalse(decision.useSnapshot());
    }

    @Test
    void forceRefusesWhenVanillaDropsAlreadyHappened() {
        RestoreItemPlan.Decision decision = RestoreItemPlan.decide(true, true, false, false, false, false, "INSUFFICIENT_BALANCE");
        assertEquals("restore-force-already-dropped", decision.refuseKey());
    }

    @Test
    void forceMayUseSnapshotWhenCreatedChestIsGone() {
        RestoreItemPlan.Decision decision = RestoreItemPlan.decide(true, true, true, false, false, false, null);
        assertFalse(decision.refuse());
        assertTrue(decision.useSnapshot());
        assertFalse(decision.useChestContents());
        assertFalse(decision.useRecovery());
    }
}
