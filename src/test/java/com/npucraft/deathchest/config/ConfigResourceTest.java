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
        }
    }
}
