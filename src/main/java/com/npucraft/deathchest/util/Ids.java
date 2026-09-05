package com.npucraft.deathchest.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Ids {
    private Ids() {
    }

    public static String chestId(String playerId, long deathTime, String timezone, int part) {
        return readableId("DC", playerId, deathTime, timezone)
                + (part > 1 ? "-P" + part : "");
    }

    public static String recordId(String playerId, long deathTime, String timezone) {
        return readableId("DR", playerId, deathTime, timezone);
    }

    private static String readableId(String prefix, String playerId, long deathTime, String timezone) {
        String safePlayerId = playerId == null ? "UNKNOWN"
                : playerId.replaceAll("[^A-Za-z0-9_]", "");
        if (safePlayerId.isBlank()) {
            safePlayerId = "UNKNOWN";
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone);
        } catch (Exception ignored) {
            zone = ZoneId.of("Asia/Shanghai");
        }
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
                .withZone(zone)
                .format(Instant.ofEpochMilli(deathTime));
        return prefix + "-" + safePlayerId + "-" + timestamp;
    }

    public static String recoveryId() {
        return "RS-" + shortHex(10);
    }

    private static String shortHex(int length) {
        String hex = UUID.randomUUID().toString().replace("-", "")
                + Long.toHexString(ThreadLocalRandom.current().nextLong());
        if (hex.length() < length) {
            hex = hex + "000000000000";
        }
        return hex.substring(0, length).toUpperCase(Locale.ROOT);
    }
}
