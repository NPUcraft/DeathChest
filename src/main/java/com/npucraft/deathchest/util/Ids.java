package com.npucraft.deathchest.util;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Ids {
    private Ids() {
    }

    public static String chestId() {
        return "DC-" + shortHex(8);
    }

    public static String recordId() {
        return "DR-" + shortHex(10);
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
