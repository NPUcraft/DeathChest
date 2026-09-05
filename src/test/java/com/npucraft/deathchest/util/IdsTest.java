package com.npucraft.deathchest.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdsTest {
    @Test
    void chestIdContainsPlayerAndShanghaiDeathTime() {
        long deathTime = Instant.parse("2026-09-05T07:30:43.125Z").toEpochMilli();
        assertEquals("DC-SUPER1FH-20260905-153043-125",
                Ids.chestId("SUPER1FH", deathTime, "Asia/Shanghai", 1));
    }

    @Test
    void extraChestGetsStablePartSuffix() {
        long deathTime = Instant.parse("2026-09-05T07:30:43Z").toEpochMilli();
        assertEquals("DC-Steve-20260905-153043-000-P2",
                Ids.chestId("Steve", deathTime, "Asia/Shanghai", 2));
    }

    @Test
    void invalidTimezoneFallsBackToShanghai() {
        long deathTime = Instant.parse("2026-09-05T07:30:43Z").toEpochMilli();
        assertEquals("DC-Steve-20260905-153043-000",
                Ids.chestId("Steve", deathTime, "invalid/timezone", 1));
    }

    @Test
    void recordIdContainsPlayerAndDeathTime() {
        long deathTime = Instant.parse("2026-09-05T07:30:43.125Z").toEpochMilli();
        assertEquals("DR-SUPER1FH-20260905-153043-125",
                Ids.recordId("SUPER1FH", deathTime, "Asia/Shanghai"));
    }
}
