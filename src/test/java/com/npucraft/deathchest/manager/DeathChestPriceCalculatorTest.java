package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.model.RoundingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeathChestPriceCalculatorTest {
    @Test
    void roundUsesHalfUpInsteadOfTiesToEven() {
        assertEquals(301.0D, DeathChestPriceCalculator.round(300.5D, RoundingMode.ROUND));
        assertEquals(300.0D, DeathChestPriceCalculator.round(300.49D, RoundingMode.ROUND));
    }

    @Test
    void floorAndCeilRemainUnchanged() {
        assertEquals(300.0D, DeathChestPriceCalculator.round(300.9D, RoundingMode.FLOOR));
        assertEquals(301.0D, DeathChestPriceCalculator.round(300.1D, RoundingMode.CEIL));
    }
}
