package com.npucraft.deathchest.storage;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.StorageType;

public final class StorageFactory {
    private StorageFactory() {
    }

    public static PluginStorage create(DeathChestPlugin plugin) {
        StorageType type = plugin.settings().storageType;
        SqlDialect dialect = switch (type) {
            case MYSQL -> new MysqlDialect();
            case SQLITE -> new SqliteDialect();
        };
        return new JdbcStorage(plugin, dialect);
    }
}
