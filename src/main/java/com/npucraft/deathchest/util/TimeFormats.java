package com.npucraft.deathchest.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class TimeFormats {
    private TimeFormats() {
    }

    public static DateTimeFormatter formatter(String pattern, String timezone) {
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern == null || pattern.isBlank() ? "yyyy-MM-dd HH:mm:ss" : pattern);
        } catch (IllegalArgumentException exception) {
            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        }
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        if (timezone != null && !timezone.isBlank()) {
            try {
                zone = ZoneId.of(timezone);
            } catch (Exception ignored) {
            }
        }
        return formatter.withZone(zone);
    }

    public static String formatInstant(long epochMillis, DateTimeFormatter formatter) {
        if (epochMillis <= 0L) {
            return "-";
        }
        return formatter.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String duration(long millis, String minutesSecondsFormat, String secondsFormat) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes <= 0L) {
            return Texts.apply(secondsFormat == null ? "{seconds}秒" : secondsFormat, Map.of("seconds", String.valueOf(seconds)));
        }
        return Texts.apply(minutesSecondsFormat == null ? "{minutes}分{seconds}秒" : minutesSecondsFormat, Map.of(
                "minutes", String.valueOf(minutes),
                "seconds", String.valueOf(seconds)
        ));
    }

    public static long remaining(long target, long now) {
        return Math.max(0L, target - now);
    }
}
