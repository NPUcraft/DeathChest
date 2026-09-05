package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.model.TimerMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerPauseTest {
    @Test
    void firstBootDoesNotPause() {
        UptimeState state = new UptimeState(0L, 0L);
        assertEquals(0L, state.targetPaused(TimerMode.PAUSE_OFFLINE, 1_000_000L));
    }

    @Test
    void pauseOfflineAddsDowntime() {
        UptimeState state = new UptimeState(1_000L, 0L);
        assertEquals(3_600_000L, state.targetPaused(TimerMode.PAUSE_OFFLINE, 1_000L + 3_600_000L));
    }

    @Test
    void realtimeDoesNotAddDowntime() {
        UptimeState state = new UptimeState(1_000L, 5_000L);
        assertEquals(5_000L, state.targetPaused(TimerMode.REALTIME, 1_000L + 3_600_000L));
    }

    @Test
    void clockSkewBackwardsDoesNotSubtract() {
        UptimeState state = new UptimeState(10_000L, 4_000L);
        assertEquals(4_000L, state.targetPaused(TimerMode.PAUSE_OFFLINE, 9_000L));
    }

    @Test
    void expireNeverStaysZero() {
        assertEquals(0L, ChestTimerMath.shiftExpire(0L, 60_000L));
    }

    @Test
    void unlockAlwaysShifts() {
        assertEquals(70_000L, ChestTimerMath.shiftUnlock(10_000L, 60_000L));
    }

    @Test
    void saturatingAddDoesNotOverflow() {
        assertEquals(Long.MAX_VALUE, ChestTimerMath.saturatingAdd(Long.MAX_VALUE - 10L, 20L));
    }

    @Test
    void alreadyCaughtUpChestsOnlyReceiveNewDowntime() {
        long previousPaused = 50_000L;
        long additional = 3_600_000L;
        long target = previousPaused + additional;
        assertEquals(additional, ChestTimerMath.pauseDelta(previousPaused, target));
        assertEquals(0L, ChestTimerMath.pauseDelta(target, target));
    }

    @Test
    void crashMidApplyDoesNotDoubleShift() {
        long lastSeen = 1_000L;
        long previousPaused = 0L;
        long firstNow = lastSeen + 10_000L;
        long firstTarget = new UptimeState(lastSeen, previousPaused).targetPaused(TimerMode.PAUSE_OFFLINE, firstNow);
        assertEquals(10_000L, firstTarget);

        long chestAUnlock = 100_000L;
        long chestAExpire = 200_000L;
        long chestAPaused = 0L;
        // chest A was saved before crash
        chestAUnlock = ChestTimerMath.shiftUnlock(chestAUnlock, ChestTimerMath.pauseDelta(chestAPaused, firstTarget));
        chestAExpire = ChestTimerMath.shiftExpire(chestAExpire, ChestTimerMath.pauseDelta(chestAPaused, firstTarget));
        chestAPaused = firstTarget;

        long chestBUnlock = 100_000L;
        long chestBExpire = 200_000L;
        long chestBPaused = 0L;
        // yaml not updated; lastSeen still old
        long secondNow = firstNow + 5_000L;
        long secondTarget = new UptimeState(lastSeen, previousPaused).targetPaused(TimerMode.PAUSE_OFFLINE, secondNow);
        assertEquals(15_000L, secondTarget);

        long deltaA = ChestTimerMath.pauseDelta(chestAPaused, secondTarget);
        long deltaB = ChestTimerMath.pauseDelta(chestBPaused, secondTarget);
        chestAUnlock = ChestTimerMath.shiftUnlock(chestAUnlock, deltaA);
        chestAExpire = ChestTimerMath.shiftExpire(chestAExpire, deltaA);
        chestBUnlock = ChestTimerMath.shiftUnlock(chestBUnlock, deltaB);
        chestBExpire = ChestTimerMath.shiftExpire(chestBExpire, deltaB);

        assertEquals(5_000L, deltaA);
        assertEquals(15_000L, deltaB);
        assertEquals(chestBUnlock, chestAUnlock);
        assertEquals(chestBExpire, chestAExpire);
        assertEquals(115_000L, chestAUnlock);
        assertEquals(215_000L, chestAExpire);
    }

    @Test
    void expiredBeforeShutdownStaysExpiredAfterPause() {
        long lastSeen = 150_000L;
        long now = 200_000L;
        long additional = new UptimeState(lastSeen, 0L).targetPaused(TimerMode.PAUSE_OFFLINE, now);
        long shifted = ChestTimerMath.shiftExpire(100_000L, additional);
        assertTrue(shifted < now);
    }

    @Test
    void wouldExpireDuringDowntimeIsRevivedWhenPaused() {
        long lastSeen = 150_000L;
        long now = 200_000L;
        long additional = new UptimeState(lastSeen, 0L).targetPaused(TimerMode.PAUSE_OFFLINE, now);
        long shifted = ChestTimerMath.shiftExpire(180_000L, additional);
        assertTrue(shifted > now);
    }

    @Test
    void deletedUptimeRecoversFromChestMarker() {
        UptimeState lost = new UptimeState(0L, 0L);
        long computed = lost.targetPaused(TimerMode.PAUSE_OFFLINE, 9_000_000L);
        long maxChestPaused = 50_000L;
        long recovered = Math.max(computed, maxChestPaused);
        assertEquals(0L, computed);
        assertEquals(50_000L, recovered);
        assertEquals(0L, ChestTimerMath.pauseDelta(maxChestPaused, recovered));
    }

    @Test
    void newChestDoesNotInheritHistoricalPause() {
        long totalPaused = 86_400_000L;
        long additional = 3_600_000L;
        long target = totalPaused + additional;
        assertEquals(additional, ChestTimerMath.pauseDelta(totalPaused, target));
        assertFalse(ChestTimerMath.pauseDelta(totalPaused, target) > additional);
    }
}
