package com.npucraft.deathchest.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageResourceTest {
    @Test
    void chineseMessagesParseAndKeepMultilineLayout() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("message_zh.yml")) {
            assertNotNull(input);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            assertTrue(yaml.getString("prefix", "").contains("DC"));
            assertTrue(yaml.getString("list-entry", "").contains("\n"));
            assertEquals(4, yaml.getStringList("hologram.protected").size());
            assertEquals(4, yaml.getStringList("hologram.public").size());
        }
    }
}
