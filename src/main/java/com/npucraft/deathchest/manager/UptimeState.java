package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.model.TimerMode;

public final class UptimeState {
    private long lastSeen;
    private long totalPaused;

    public UptimeState() {
    }

    public UptimeState(long lastSeen, long totalPaused) {
        this.lastSeen = Math.max(0L, lastSeen);
        this.totalPaused = Math.max(0L, totalPaused);
    }

    public long lastSeen() {
        return lastSeen;
    }

    public long totalPaused() {
        return totalPaused;
    }

    public long targetPaused(TimerMode mode, long now) {
        long additional = 0L;
        if (mode == TimerMode.PAUSE_OFFLINE && lastSeen > 0L && now > lastSeen) {
            additional = now - lastSeen;
        }
        return ChestTimerMath.saturatingAdd(totalPaused, additional);
    }

    public void markRunning(long now, long totalPaused) {
        this.lastSeen = Math.max(0L, now);
        this.totalPaused = Math.max(0L, totalPaused);
    }

    public void heartbeat(long now) {
        this.lastSeen = Math.max(0L, now);
    }
}
