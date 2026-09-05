package com.npucraft.deathchest.manager;

public final class ChestTimerMath {
    private ChestTimerMath() {
    }

    public static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (left >= Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public static long shiftUnlock(long unlockAt, long delta) {
        return saturatingAdd(unlockAt, Math.max(0L, delta));
    }

    public static long shiftExpire(long expireAt, long delta) {
        if (expireAt <= 0L || delta <= 0L) {
            return expireAt;
        }
        return saturatingAdd(expireAt, delta);
    }

    public static long pauseDelta(long alreadyPaused, long targetPaused) {
        if (targetPaused <= alreadyPaused) {
            return 0L;
        }
        return targetPaused - alreadyPaused;
    }
}
