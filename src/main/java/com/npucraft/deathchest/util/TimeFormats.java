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
            formatter = DateTimeFormatter.ofPattern(pattern == null || pattern.isBlank()
                    ? "yyyy-MM-dd HH:mm:ss" : pattern);
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

    public static String duration(long millis, String daysFormat, String hoursFormat,
                                  String minutesFormat, String secondsFormat) {
        long totalSeconds = millis <= 0L ? 0L : 1L + (millis - 1L) / 1000L;
        long days = totalSeconds / 86_400L;
        long hours = totalSeconds % 86_400L / 3_600L;
        long minutes = totalSeconds % 3_600L / 60L;
        long seconds = totalSeconds % 60L;
        Map<String, String> values = Map.of(
                "days", String.valueOf(days),
                "hours", String.valueOf(hours),
                "minutes", String.valueOf(minutes),
                "seconds", String.valueOf(seconds)
        );
        if (days > 0L) {
            return Texts.apply(daysFormat == null
                    ? "{days}天{hours}小时{minutes}分钟{seconds}秒" : daysFormat, values);
        }
        if (hours > 0L) {
            return Texts.apply(hoursFormat == null ? "{hours}小时{minutes}分钟{seconds}秒" : hoursFormat, values);
        }
        if (minutes > 0L) {
            return Texts.apply(minutesFormat == null ? "{minutes}分钟{seconds}秒" : minutesFormat, values);
        }
        return Texts.apply(secondsFormat == null ? "{seconds}秒" : secondsFormat, values);
    }

    public static long remaining(long target, long now) {
        return Math.max(0L, target - now);
    }

    public static String durationHms(long millis) {
        long seconds = millis <= 0L ? 0L : 1L + (millis - 1L) / 1000L;
        return seconds / 3600L + "时" + seconds % 3600L / 60L + "分" + seconds % 60L + "秒";
    }
}
