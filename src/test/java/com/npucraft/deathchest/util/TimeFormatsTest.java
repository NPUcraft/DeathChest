package com.npucraft.deathchest.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatsTest {
    @Test
    void formatsTabCountdownWithTotalHoursAndAllUnits() {
        assertEquals("51时4分5秒", TimeFormats.durationHms(183_845_000L));
        assertEquals("0时0分1秒", TimeFormats.durationHms(1L));
        assertEquals("1时0分0秒", TimeFormats.durationHms(3_599_001L));
        assertEquals("0时0分0秒", TimeFormats.durationHms(0L));
        assertEquals("0时0分0秒", TimeFormats.durationHms(-1L));
    }

    private static final String DAYS = "{days}天{hours}小时{minutes}分钟{seconds}秒";
    private static final String HOURS = "{hours}小时{minutes}分钟{seconds}秒";
    private static final String MINUTES = "{minutes}分钟{seconds}秒";
    private static final String SECONDS = "{seconds}秒";

    @Test
    void formatsEveryDurationUnit() {
        assertEquals("2天3小时4分钟5秒", TimeFormats.duration(183_845_000L,
                "{days}天{hours}小时{minutes}分钟{seconds}秒", "{hours}小时{minutes}分钟{seconds}秒",
                "{minutes}分钟{seconds}秒", "{seconds}秒"));
        assertEquals("12小时0分钟0秒", TimeFormats.duration(43_200_000L, DAYS, HOURS, MINUTES, SECONDS));
        assertEquals("5分钟7秒", TimeFormats.duration(307_000L, DAYS, HOURS, MINUTES, SECONDS));
        assertEquals("9秒", TimeFormats.duration(9_000L, DAYS, HOURS, MINUTES, SECONDS));
    }

    @Test
    void roundsPositiveSubSecondRemainderUp() {
        assertEquals("1秒", TimeFormats.duration(1L, DAYS, HOURS, MINUTES, SECONDS));
    }

    @Test
    void formatsAbsoluteTimeInConfiguredTimezone() {
        String formatted = TimeFormats.formatInstant(Instant.parse("2026-09-05T07:00:01Z").toEpochMilli(),
                TimeFormats.formatter("yyyy-MM-dd HH:mm:ss", "Asia/Shanghai"));
        assertEquals("2026-09-05 15:00:01", formatted);
    }
}
