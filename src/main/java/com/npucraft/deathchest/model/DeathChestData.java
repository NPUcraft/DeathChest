package com.npucraft.deathchest.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeathChestData {
    private String id;
    private String recordId;
    private UUID ownerUuid;
    private String ownerName;
    private String world;
    private int x;
    private int y;
    private int z;
    private Integer secondX;
    private Integer secondY;
    private Integer secondZ;
    private ChestType chestType = ChestType.SINGLE;
    private long createdAt;
    private long unlockAt;
    private long expireAt;
    private long timerPausedMillis;
    private double price;
    private String currency;
    private boolean unpaid;
    private boolean locked;
    private UUID hologramId;
    private boolean active = true;

    public List<LocationKey> blockKeys() {
        List<LocationKey> keys = new ArrayList<>(2);
        keys.add(new LocationKey(world, x, y, z));
        if (chestType == ChestType.DOUBLE && secondX != null && secondY != null && secondZ != null) {
            keys.add(new LocationKey(world, secondX, secondY, secondZ));
        }
        return keys;
    }

    public Location primaryLocation(World bukkitWorld) {
        return new Location(bukkitWorld, x + 0.5, y, z + 0.5);
    }

    public Location hologramLocation(World bukkitWorld, double height) {
        double hx = x + 0.5;
        double hz = z + 0.5;
        if (chestType == ChestType.DOUBLE && secondX != null && secondZ != null) {
            hx = (x + secondX) / 2.0 + 0.5;
            hz = (z + secondZ) / 2.0 + 0.5;
        }
        return new Location(bukkitWorld, hx, y + height, hz);
    }

    public boolean isProtected(long now) {
        return !unpaid && now < unlockAt;
    }

    public boolean occupies(Block block) {
        if (block == null || block.getWorld() == null) {
            return false;
        }
        if (!block.getWorld().getName().equals(world)) {
            return false;
        }
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        if (bx == x && by == y && bz == z) {
            return true;
        }
        return chestType == ChestType.DOUBLE
                && secondX != null && secondY != null && secondZ != null
                && bx == secondX && by == secondY && bz == secondZ;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public Integer getSecondX() {
        return secondX;
    }

    public void setSecondX(Integer secondX) {
        this.secondX = secondX;
    }

    public Integer getSecondY() {
        return secondY;
    }

    public void setSecondY(Integer secondY) {
        this.secondY = secondY;
    }

    public Integer getSecondZ() {
        return secondZ;
    }

    public void setSecondZ(Integer secondZ) {
        this.secondZ = secondZ;
    }

    public ChestType getChestType() {
        return chestType;
    }

    public void setChestType(ChestType chestType) {
        this.chestType = chestType;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUnlockAt() {
        return unlockAt;
    }

    public void setUnlockAt(long unlockAt) {
        this.unlockAt = unlockAt;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }

    public long getTimerPausedMillis() {
        return timerPausedMillis;
    }

    public void setTimerPausedMillis(long timerPausedMillis) {
        this.timerPausedMillis = Math.max(0L, timerPausedMillis);
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isUnpaid() {
        return unpaid;
    }

    public void setUnpaid(boolean unpaid) {
        this.unpaid = unpaid;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public UUID getHologramId() {
        return hologramId;
    }

    public void setHologramId(UUID hologramId) {
        this.hologramId = hologramId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
