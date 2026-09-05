package com.npucraft.deathchest.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;

public final class Texts {
    public static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Texts() {
    }

    public static String apply(String input, Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String output = input;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                output = output.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
                output = output.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return output;
    }

    public static Component mini(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(input);
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    public static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0000001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format("%.2f", value);
    }
}
