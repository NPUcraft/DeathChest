package com.npucraft.deathchest.model;

import com.npucraft.deathchest.util.ItemStacks;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeathRecord {
    private String recordId;
    private UUID playerUuid;
    private String playerName;
    private long deathTime;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String deathCause;
    private UUID killerUuid;
    private String killerName;
    private int playerLevelBefore;
    private int totalExperienceBefore;
    private float experienceProgressBefore;
    private int experienceKept;
    private int experienceLost;
    private double balanceBefore;
    private double balanceAfter;
    private String economyProvider;
    private String currencyId;
    private double calculatedPrice;
    private double chargedPrice;
    private boolean insufficientBalance;
    private String insufficientBalanceMode;
    private boolean deathChestEnabled;
    private boolean deathChestCreated;
    private String deathChestId;
    private String deathChestWorld;
    private Integer deathChestX;
    private Integer deathChestY;
    private Integer deathChestZ;
    private ChestType chestType;
    private boolean protectedChest;
    private Long unlockAt;
    private Long expireAt;
    private RecordStatus status = RecordStatus.PREPARED;
    private String failureReason;
    private boolean rollbackInProgress;
    private boolean itemsRestored;
    private boolean experienceRestored;
    private List<ItemStack> items = new ArrayList<>();

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public long getDeathTime() {
        return deathTime;
    }

    public void setDeathTime(long deathTime) {
        this.deathTime = deathTime;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public String getDeathCause() {
        return deathCause;
    }

    public void setDeathCause(String deathCause) {
        this.deathCause = deathCause;
    }

    public UUID getKillerUuid() {
        return killerUuid;
    }

    public void setKillerUuid(UUID killerUuid) {
        this.killerUuid = killerUuid;
    }

    public String getKillerName() {
        return killerName;
    }

    public void setKillerName(String killerName) {
        this.killerName = killerName;
    }

    public int getPlayerLevelBefore() {
        return playerLevelBefore;
    }

    public void setPlayerLevelBefore(int playerLevelBefore) {
        this.playerLevelBefore = playerLevelBefore;
    }

    public int getTotalExperienceBefore() {
        return totalExperienceBefore;
    }

    public void setTotalExperienceBefore(int totalExperienceBefore) {
        this.totalExperienceBefore = totalExperienceBefore;
    }

    public float getExperienceProgressBefore() {
        return experienceProgressBefore;
    }

    public void setExperienceProgressBefore(float experienceProgressBefore) {
        this.experienceProgressBefore = experienceProgressBefore;
    }

    public int getExperienceKept() {
        return experienceKept;
    }

    public void setExperienceKept(int experienceKept) {
        this.experienceKept = experienceKept;
    }

    public int getExperienceLost() {
        return experienceLost;
    }

    public void setExperienceLost(int experienceLost) {
        this.experienceLost = experienceLost;
    }

    public double getBalanceBefore() {
        return balanceBefore;
    }

    public void setBalanceBefore(double balanceBefore) {
        this.balanceBefore = balanceBefore;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(double balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getEconomyProvider() {
        return economyProvider;
    }

    public void setEconomyProvider(String economyProvider) {
        this.economyProvider = economyProvider;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(String currencyId) {
        this.currencyId = currencyId;
    }

    public double getCalculatedPrice() {
        return calculatedPrice;
    }

    public void setCalculatedPrice(double calculatedPrice) {
        this.calculatedPrice = calculatedPrice;
    }

    public double getChargedPrice() {
        return chargedPrice;
    }

    public void setChargedPrice(double chargedPrice) {
        this.chargedPrice = chargedPrice;
    }

    public boolean isInsufficientBalance() {
        return insufficientBalance;
    }

    public void setInsufficientBalance(boolean insufficientBalance) {
        this.insufficientBalance = insufficientBalance;
    }

    public String getInsufficientBalanceMode() {
        return insufficientBalanceMode;
    }

    public void setInsufficientBalanceMode(String insufficientBalanceMode) {
        this.insufficientBalanceMode = insufficientBalanceMode;
    }

    public boolean isDeathChestEnabled() {
        return deathChestEnabled;
    }

    public void setDeathChestEnabled(boolean deathChestEnabled) {
        this.deathChestEnabled = deathChestEnabled;
    }

    public boolean isDeathChestCreated() {
        return deathChestCreated;
    }

    public void setDeathChestCreated(boolean deathChestCreated) {
        this.deathChestCreated = deathChestCreated;
    }

    public String getDeathChestId() {
        return deathChestId;
    }

    public void setDeathChestId(String deathChestId) {
        this.deathChestId = deathChestId;
    }

    public String getDeathChestWorld() {
        return deathChestWorld;
    }

    public void setDeathChestWorld(String deathChestWorld) {
        this.deathChestWorld = deathChestWorld;
    }

    public Integer getDeathChestX() {
        return deathChestX;
    }

    public void setDeathChestX(Integer deathChestX) {
        this.deathChestX = deathChestX;
    }

    public Integer getDeathChestY() {
        return deathChestY;
    }

    public void setDeathChestY(Integer deathChestY) {
        this.deathChestY = deathChestY;
    }

    public Integer getDeathChestZ() {
        return deathChestZ;
    }

    public void setDeathChestZ(Integer deathChestZ) {
        this.deathChestZ = deathChestZ;
    }

    public ChestType getChestType() {
        return chestType;
    }

    public void setChestType(ChestType chestType) {
        this.chestType = chestType;
    }

    public boolean isProtectedChest() {
        return protectedChest;
    }

    public void setProtectedChest(boolean protectedChest) {
        this.protectedChest = protectedChest;
    }

    public Long getUnlockAt() {
        return unlockAt;
    }

    public void setUnlockAt(Long unlockAt) {
        this.unlockAt = unlockAt;
    }

    public Long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Long expireAt) {
        this.expireAt = expireAt;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public void setStatus(RecordStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public boolean isRollbackInProgress() {
        return rollbackInProgress;
    }

    public void setRollbackInProgress(boolean rollbackInProgress) {
        this.rollbackInProgress = rollbackInProgress;
    }

    public boolean isItemsRestored() {
        return itemsRestored;
    }

    public void setItemsRestored(boolean itemsRestored) {
        this.itemsRestored = itemsRestored;
    }

    public boolean isExperienceRestored() {
        return experienceRestored;
    }

    public void setExperienceRestored(boolean experienceRestored) {
        this.experienceRestored = experienceRestored;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public void setItems(List<ItemStack> items) {
        this.items = items == null ? new ArrayList<>() : ItemStacks.deepCopy(items);
    }
}
