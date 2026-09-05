package com.npucraft.deathchest.manager;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeathChestPairingTest {
    @Test
    void assignsLeftHalfWhenPartnerIsClockwiseFromFacing() {
        assertEquals(Chest.Type.LEFT, DeathChestManager.primaryChestType(BlockFace.NORTH, 1, 0));
        assertEquals(Chest.Type.LEFT, DeathChestManager.primaryChestType(BlockFace.EAST, 0, 1));
        assertEquals(Chest.Type.LEFT, DeathChestManager.primaryChestType(BlockFace.SOUTH, -1, 0));
        assertEquals(Chest.Type.LEFT, DeathChestManager.primaryChestType(BlockFace.WEST, 0, -1));
    }

    @Test
    void assignsRightHalfWhenPartnerIsCounterClockwiseFromFacing() {
        assertEquals(Chest.Type.RIGHT, DeathChestManager.primaryChestType(BlockFace.NORTH, -1, 0));
        assertEquals(Chest.Type.RIGHT, DeathChestManager.primaryChestType(BlockFace.EAST, 0, -1));
        assertEquals(Chest.Type.RIGHT, DeathChestManager.primaryChestType(BlockFace.SOUTH, 1, 0));
        assertEquals(Chest.Type.RIGHT, DeathChestManager.primaryChestType(BlockFace.WEST, 0, 1));
    }

    @Test
    void rejectsPartnerAlongFacingAxis() {
        assertThrows(IllegalArgumentException.class,
                () -> DeathChestManager.primaryChestType(BlockFace.NORTH, 0, -1));
    }
}
