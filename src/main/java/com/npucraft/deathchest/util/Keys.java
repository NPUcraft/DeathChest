package com.npucraft.deathchest.util;

import org.bukkit.NamespacedKey;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class Keys {
    public final NamespacedKey chestId;
    public final NamespacedKey recordId;
    public final NamespacedKey ownerUuid;
    public final NamespacedKey createdAt;
    public final NamespacedKey unlockAt;
    public final NamespacedKey expireAt;
    public final NamespacedKey hologramMarker;

    public Keys(Plugin plugin) {
        this.chestId = new NamespacedKey(plugin, "deathchest_id");
        this.recordId = new NamespacedKey(plugin, "record_id");
        this.ownerUuid = new NamespacedKey(plugin, "owner_uuid");
        this.createdAt = new NamespacedKey(plugin, "created_at");
        this.unlockAt = new NamespacedKey(plugin, "unlock_at");
        this.expireAt = new NamespacedKey(plugin, "expire_at");
        this.hologramMarker = new NamespacedKey(plugin, "hologram");
    }

    public void writeChest(TileState state, String id, String record, String owner, long created, long unlock, long expire) {
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        pdc.set(chestId, PersistentDataType.STRING, id);
        if (record != null && !record.isBlank()) {
            pdc.set(recordId, PersistentDataType.STRING, record);
        }
        pdc.set(ownerUuid, PersistentDataType.STRING, owner);
        pdc.set(createdAt, PersistentDataType.LONG, created);
        pdc.set(unlockAt, PersistentDataType.LONG, unlock);
        pdc.set(expireAt, PersistentDataType.LONG, expire);
        state.update(true, false);
    }

    public String readChestId(TileState state) {
        return state.getPersistentDataContainer().get(chestId, PersistentDataType.STRING);
    }

    public String readRecordId(TileState state) {
        return state.getPersistentDataContainer().get(recordId, PersistentDataType.STRING);
    }

    public UUID readOwnerUuid(TileState state) {
        String raw = state.getPersistentDataContainer().get(ownerUuid, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public long readLong(TileState state, NamespacedKey key, long fallback) {
        Long value = state.getPersistentDataContainer().get(key, PersistentDataType.LONG);
        return value == null ? fallback : value;
    }
}
