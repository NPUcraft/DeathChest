package com.npucraft.deathchest.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigResourceTest {
    @Test
    void defaultVerticalSearchRadiusCoversHighAltitudeDeaths() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(input);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            assertEquals(128, yaml.getInt("location.vertical-search-radius"));
            assertEquals(200.0D, yaml.getDouble("price.base"));
            assertEquals(2.0D, yaml.getDouble("price.level.price-per-level"));
            assertEquals(20.0D, yaml.getDouble("price.inventory.price-per-slot"));
            assertEquals(1200.0D, yaml.getDouble("price.maximum"));
            assertEquals("yyyy-MM-dd HH:mm:ss", yaml.getString("time.date-format"));
            assertEquals(true, yaml.getBoolean("player-settings.default-enabled"));
            assertEquals(true, yaml.getBoolean("player-settings.allow-toggle"));
        }
    }
}
