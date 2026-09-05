package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.AuditEventType;
import com.npucraft.deathchest.model.ChestPlacement;
import com.npucraft.deathchest.model.ChestType;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.model.ExpireMode;
import com.npucraft.deathchest.model.LocationKey;
import com.npucraft.deathchest.util.ItemMatcher;
import com.npucraft.deathchest.util.ItemStacks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathChestManager {
    private final DeathChestPlugin plugin;
    private final Map<String, DeathChestData> byId = new ConcurrentHashMap<>();
    private final Map<LocationKey, String> byLocation = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> byOwner = new ConcurrentHashMap<>();
    private final Map<String, Integer> emptyTasks = new ConcurrentHashMap<>();

    public DeathChestManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        byId.clear();
        byLocation.clear();
        byOwner.clear();
        for (DeathChestData chest : plugin.storage().loadActiveChests()) {
            index(chest);
        }
        plugin.getLogger().info("Loaded " + byId.size() + " active death chests.");
    }

    public Collection<DeathChestData> all() {
        return List.copyOf(byId.values());
    }

    public Optional<DeathChestData> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        DeathChestData memory = byId.get(id);
        if (memory != null) {
            return Optional.of(memory);
        }
        Optional<DeathChestData> loaded = plugin.storage().loadChest(id);
        if (loaded.isPresent() && loaded.get().isActive()) {
            index(loaded.get());
            return loaded;
        }
        return Optional.empty();
    }

    public Optional<DeathChestData> byBlock(Block block) {
        if (block == null) {
            return Optional.empty();
        }
        LocationKey locationKey = LocationKey.of(block.getLocation());
        String id = byLocation.get(locationKey);
        if (id != null) {
            Optional<DeathChestData> indexed = byId(id);
            if (indexed.isPresent() && hasCompleteIdentity(indexed.get())) {
                return indexed;
            }
            byLocation.remove(locationKey, id);
        }
        if (block.getState() instanceof TileState tile) {
            String pdcId = plugin.keys().readChestId(tile);
            if (pdcId != null) {
                Optional<DeathChestData> existing = byId(pdcId);
                if (existing.isPresent() && hasCompleteIdentity(existing.get())
                        && existing.get().blockKeys().contains(locationKey)) {
                    return existing;
                }
                plugin.getLogger().warning("Ignored untrusted or stale DeathChest PDC at "
                        + block.getWorld().getName() + " " + block.getX() + " " + block.getY() + " " + block.getZ()
                        + " id=" + pdcId);
            }
        }
        return Optional.empty();
    }

    public Optional<DeathChestData> byInventory(Inventory inventory) {
        if (inventory == null) {
            return Optional.empty();
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof org.bukkit.block.Chest chest) {
            return byBlock(chest.getBlock());
        }
        if (holder instanceof org.bukkit.block.DoubleChest doubleChest) {
            InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof org.bukkit.block.Chest leftChest) {
                Optional<DeathChestData> found = byBlock(leftChest.getBlock());
                if (found.isPresent()) {
                    return found;
                }
            }
            InventoryHolder right = doubleChest.getRightSide();
            if (right instanceof org.bukkit.block.Chest rightChest) {
                return byBlock(rightChest.getBlock());
            }
        }
        return Optional.empty();
    }

    public List<DeathChestData> byOwner(UUID owner) {
        List<String> ids = byOwner.getOrDefault(owner, List.of());
        List<DeathChestData> chests = new ArrayList<>();
        for (String id : ids) {
            DeathChestData chest = byId.get(id);
            if (chest != null) {
                chests.add(chest);
            }
        }
        chests.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return chests;
    }

    public List<DeathChestData> byRecord(String recordId) {
        List<DeathChestData> chests = new ArrayList<>();
        if (recordId == null) {
            return chests;
        }
        for (DeathChestData chest : byId.values()) {
            if (recordId.equals(chest.getRecordId())) {
                chests.add(chest);
            }
        }
        if (!chests.isEmpty()) {
            return chests;
        }
        try {
            for (DeathChestData loaded : plugin.storage().loadChestsByRecord(recordId)) {
                if (loaded.isActive()) {
                    index(loaded);
                    chests.add(loaded);
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to load death chests for record " + recordId + ": " + exception.getMessage());
        }
        return chests;
    }

    public boolean isOccupied(LocationKey key) {
        return byLocation.containsKey(key);
    }

    public DeathChestData createPhysical(DeathChestData data, ChestPlacement placement, List<ItemStack> items) {
        Block primary = placement.getPrimary();
        Block secondary = placement.getSecondary();
        org.bukkit.block.data.BlockData primaryBefore = primary.getBlockData().clone();
        org.bukkit.block.data.BlockData secondaryBefore = secondary == null ? null : secondary.getBlockData().clone();
        boolean water1 = primary.getType() == Material.WATER;
        boolean water2 = secondary != null && secondary.getType() == Material.WATER;
        Material material = plugin.settings().chestBlockType == null ? Material.CHEST : plugin.settings().chestBlockType;
        if (plugin.timerClock() != null) {
            data.setTimerPausedMillis(plugin.timerClock().totalPaused());
        }
        try {
            primary.setType(material, false);
            if (secondary != null) {
                secondary.setType(material, false);
            }
            applyChestData(primary, placement.getFacing(), placement.getType() == ChestType.DOUBLE
                    ? partnerType(primary, secondary, placement.getFacing(), true) : Chest.Type.SINGLE, water1);
            if (secondary != null) {
                applyChestData(secondary, placement.getFacing(),
                        partnerType(primary, secondary, placement.getFacing(), false), water2);
                validateDoubleChest(primary, secondary);
            }
            writePdc(primary, data);
            if (secondary != null) {
                writePdc(secondary, data);
            }
            storeAssignedItems(primary, secondary, placement.getType(), items);
        } catch (RuntimeException exception) {
            revertBlock(primary, primaryBefore);
            if (secondary != null && secondaryBefore != null) {
                revertBlock(secondary, secondaryBefore);
            }
            throw exception;
        }
        try {
            plugin.storage().saveChest(data);
            index(data);
            plugin.audit().log(AuditEventType.CHEST_CREATED, data.getOwnerUuid(), data.getOwnerName(), data.getOwnerUuid(),
                    data.getOwnerName(), data.getId(), data.getRecordId(), data.getWorld() + " " + data.getX() + " " + data.getY() + " " + data.getZ(), false);
            return data;
        } catch (RuntimeException exception) {
            revertBlock(primary, primaryBefore);
            if (secondary != null && secondaryBefore != null) {
                revertBlock(secondary, secondaryBefore);
            }
            unindex(data);
            try {
                plugin.storage().deleteChest(data.getId());
            } catch (RuntimeException ignored) {
            }
            throw exception;
        }
    }

    public void destroySilently(DeathChestData data) {
        World world = Bukkit.getWorld(data.getWorld());
        if (world != null) {
            clearBlockIfOwned(world.getBlockAt(data.getX(), data.getY(), data.getZ()), data);
            if (data.getChestType() == ChestType.DOUBLE && data.getSecondX() != null) {
                clearBlockIfOwned(world.getBlockAt(data.getSecondX(), data.getSecondY(), data.getSecondZ()), data);
            }
        }
        plugin.holograms().remove(data);
        unindex(data);
        plugin.storage().deleteChest(data.getId());
        cancelEmptyTask(data.getId());
    }

    public void removeAndStoreRemaining(DeathChestData data, boolean toRecovery) {
        List<ItemStack> remaining = currentItems(data);
        if (toRecovery && !remaining.isEmpty()) {
            if (!plugin.recovery().storeChestTransfer(data, remaining)) {
                throw new IllegalStateException("Cannot safely remove death chest " + data.getId()
                        + " because its remaining items could not be persisted");
            }
        }
        destroySilently(data);
        plugin.audit().log(AuditEventType.CHEST_REMOVED, null, "DeathChest", data.getOwnerUuid(), data.getOwnerName(),
                data.getId(), data.getRecordId(), "remaining=" + remaining.size(), false);
    }

    public List<ItemStack> currentItems(DeathChestData data) {
        World world = Bukkit.getWorld(data.getWorld());
        if (world == null || !chunkLoaded(data)) {
            return List.of();
        }
        List<ItemStack> items = new ArrayList<>();
        appendOwnedBlockItems(world.getBlockAt(data.getX(), data.getY(), data.getZ()), data, items);
        if (data.getChestType() == ChestType.DOUBLE && data.getSecondX() != null) {
            appendOwnedBlockItems(world.getBlockAt(data.getSecondX(), data.getSecondY(), data.getSecondZ()), data, items);
        }
        return items;
    }

    public Inventory inventoryOf(DeathChestData data) {
        World world = Bukkit.getWorld(data.getWorld());
        if (world == null || !chunkLoaded(data)) {
            return null;
        }
        Block primary = world.getBlockAt(data.getX(), data.getY(), data.getZ());
        if (!isOwnedBlock(primary, data)) {
            return null;
        }
        if (data.getChestType() == ChestType.DOUBLE && data.getSecondX() != null) {
            Block secondary = world.getBlockAt(data.getSecondX(), data.getSecondY(), data.getSecondZ());
            if (!isOwnedBlock(secondary, data)) {
                return null;
            }
        }
        return inventoryOf(primary);
    }

    public ItemStack[] contentsOf(DeathChestData data) {
        List<org.bukkit.block.Chest> blocks = ownedChestBlocks(data);
        if (blocks.isEmpty()) {
            return null;
        }
        int expectedBlocks = data.getChestType() == ChestType.DOUBLE ? 2 : 1;
        if (blocks.size() != expectedBlocks) {
            return null;
        }
        ItemStack[] contents = new ItemStack[expectedBlocks * 27];
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            ItemStack[] half = blocks.get(blockIndex).getBlockInventory().getContents();
            System.arraycopy(ItemStacks.cloneArray(half), 0, contents, blockIndex * 27, 27);
        }
        return contents;
    }

    public void setContents(DeathChestData data, ItemStack[] contents) {
        List<org.bukkit.block.Chest> blocks = ownedChestBlocks(data);
        int expectedBlocks = data.getChestType() == ChestType.DOUBLE ? 2 : 1;
        int expectedSlots = expectedBlocks * 27;
        if (blocks.size() != expectedBlocks || contents == null || contents.length != expectedSlots) {
            throw new IllegalStateException("Death chest inventory layout is unavailable: " + data.getId());
        }
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            ItemStack[] half = Arrays.copyOfRange(contents, blockIndex * 27, (blockIndex + 1) * 27);
            blocks.get(blockIndex).getBlockInventory().setContents(ItemStacks.cloneArray(half));
        }
    }

    public Inventory inventoryOf(Block block) {
        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            return chest.getInventory();
        }
        return null;
    }

    public boolean existsInWorld(DeathChestData data) {
        World world = Bukkit.getWorld(data.getWorld());
        if (world == null) {
            return false;
        }
        if (!chunkLoaded(data)) {
            return true;
        }
        if (!isOwnedBlock(world.getBlockAt(data.getX(), data.getY(), data.getZ()), data)) {
            return false;
        }
        return data.getChestType() != ChestType.DOUBLE || data.getSecondX() == null
                || isOwnedBlock(world.getBlockAt(data.getSecondX(), data.getSecondY(), data.getSecondZ()), data);
    }

    public boolean chunkLoaded(DeathChestData data) {
        World world = Bukkit.getWorld(data.getWorld());
        if (world == null) {
            return false;
        }
        return world.isChunkLoaded(Math.floorDiv(data.getX(), 16), Math.floorDiv(data.getZ(), 16));
    }

    public boolean worldLoaded(DeathChestData data) {
        return data.getWorld() != null && Bukkit.getWorld(data.getWorld()) != null;
    }

    public void cleanupIfDue(DeathChestData chest, long now) {
        if (!worldLoaded(chest) || !chunkLoaded(chest)) {
            return;
        }
        if (!existsInWorld(chest)) {
            plugin.getLogger().warning("Death chest blocks no longer match stored PDC identity; preserving any verified remainder: "
                    + chest.getId());
            removeAndStoreRemaining(chest, true);
            return;
        }
        if (plugin.settings().publicTimeSeconds > 0 && chest.getExpireAt() > 0 && now >= chest.getExpireAt()) {
            expire(chest);
        }
    }

    public int reconcilePendingTransfers() {
        int reconciled = 0;
        for (DeathChestData chest : List.copyOf(byId.values())) {
            if (!plugin.recovery().hasChestTransfer(chest)) {
                continue;
            }
            destroySilently(chest);
            reconciled++;
            plugin.getLogger().warning("Completed interrupted death-chest transfer: " + chest.getId());
        }
        return reconciled;
    }

    public long maxTimerPausedMillis() {
        long max = 0L;
        for (DeathChestData chest : byId.values()) {
            max = Math.max(max, chest.getTimerPausedMillis());
        }
        return max;
    }

    public int catchUpTimers(long targetPaused) {
        if (targetPaused < 0L) {
            return 0;
        }
        int shifted = 0;
        for (DeathChestData chest : all()) {
            if (catchUpTimer(chest, targetPaused)) {
                shifted++;
            }
        }
        return shifted;
    }

    private boolean catchUpTimer(DeathChestData chest, long targetPaused) {
        long delta = ChestTimerMath.pauseDelta(chest.getTimerPausedMillis(), targetPaused);
        if (delta <= 0L) {
            if (chest.getTimerPausedMillis() != targetPaused) {
                long oldPaused = chest.getTimerPausedMillis();
                chest.setTimerPausedMillis(targetPaused);
                try {
                    plugin.storage().saveChest(chest);
                } catch (RuntimeException exception) {
                    chest.setTimerPausedMillis(oldPaused);
                    plugin.getLogger().warning("Failed to sync pause marker for death chest " + chest.getId() + ": "
                            + exception.getMessage());
                }
            }
            return false;
        }
        long oldUnlock = chest.getUnlockAt();
        long oldExpire = chest.getExpireAt();
        long oldPaused = chest.getTimerPausedMillis();
        chest.setUnlockAt(ChestTimerMath.shiftUnlock(oldUnlock, delta));
        chest.setExpireAt(ChestTimerMath.shiftExpire(oldExpire, delta));
        chest.setTimerPausedMillis(targetPaused);
        try {
            plugin.storage().saveChest(chest);
        } catch (RuntimeException exception) {
            chest.setUnlockAt(oldUnlock);
            chest.setExpireAt(oldExpire);
            chest.setTimerPausedMillis(oldPaused);
            plugin.getLogger().warning("Failed to shift timers for death chest " + chest.getId() + ": " + exception.getMessage());
            return false;
        }
        try {
            syncPdc(chest);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to sync death chest block data for " + chest.getId() + ": " + exception.getMessage());
        }
        if (chest.getRecordId() != null) {
            try {
                plugin.records().get(chest.getRecordId()).ifPresent(record -> {
                    record.setUnlockAt(chest.getUnlockAt());
                    record.setExpireAt(chest.getExpireAt());
                    plugin.records().save(record);
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Failed to sync death record timers for " + chest.getId() + ": " + exception.getMessage());
            }
        }
        return true;
    }

    public void syncPdc(DeathChestData data) {
        World world = Bukkit.getWorld(data.getWorld());
        if (world == null) {
            return;
        }
        syncPdc(world.getBlockAt(data.getX(), data.getY(), data.getZ()), data);
        if (data.getChestType() == ChestType.DOUBLE && data.getSecondX() != null) {
            syncPdc(world.getBlockAt(data.getSecondX(), data.getSecondY(), data.getSecondZ()), data);
        }
    }

    public void syncPdc(Block block, DeathChestData data) {
        if (block == null || !block.getChunk().isLoaded() || !(block.getState() instanceof TileState tile)
                || !isOwnedBlock(block, data)) {
            return;
        }
        long unlock = plugin.keys().readLong(tile, plugin.keys().unlockAt, Long.MIN_VALUE);
        long expire = plugin.keys().readLong(tile, plugin.keys().expireAt, Long.MIN_VALUE);
        if (unlock == data.getUnlockAt() && expire == data.getExpireAt()) {
            return;
        }
        writePdc(block, data);
    }

    public void setLocked(DeathChestData data, boolean locked) {
        data.setLocked(locked);
        plugin.storage().setChestLocked(data.getId(), locked);
    }

    public void unlock(DeathChestData data, CommandSender sender) {
        long now = System.currentTimeMillis();
        data.setUnlockAt(now);
        data.setUnpaid(false);
        if (plugin.settings().publicTimeSeconds > 0) {
            data.setExpireAt(now + plugin.settings().publicTimeSeconds * 1000L);
        } else {
            data.setExpireAt(0L);
        }
        plugin.storage().saveChest(data);
        syncPdc(data);
        plugin.holograms().update(data);
        if (data.getRecordId() != null) {
            plugin.records().get(data.getRecordId()).ifPresent(record -> {
                record.setUnlockAt(data.getUnlockAt());
                record.setExpireAt(data.getExpireAt());
                record.setProtectedChest(false);
                plugin.records().save(record);
            });
        }
        UUID actor = sender instanceof Player player ? player.getUniqueId() : null;
        boolean adminUnlock = sender.hasPermission("deathchest.admin")
                && !(sender instanceof Player player && player.getUniqueId().equals(data.getOwnerUuid()));
        plugin.audit().log(adminUnlock ? AuditEventType.ADMIN_UNLOCK : AuditEventType.CHEST_UNLOCKED,
                actor, sender.getName(), data.getOwnerUuid(), data.getOwnerName(),
                data.getId(), data.getRecordId(), "unlocked", false);
    }

    public String stateLabel(DeathChestData data, long now) {
        if (data.isLocked()) {
            return plugin.messages().raw("chest-state-locked", "LOCKED");
        }
        if (data.isUnpaid()) {
            return plugin.messages().raw("chest-state-unpaid", "UNPAID");
        }
        if (data.isProtected(now)) {
            return plugin.messages().raw("chest-state-protected", "PROTECTED");
        }
        return plugin.messages().raw("chest-state-public", "PUBLIC");
    }

    public void scheduleEmptyCheck(DeathChestData data) {
        if (!plugin.settings().removeEmptyChest) {
            return;
        }
        cancelEmptyTask(data.getId());
        int delay = Math.max(1, plugin.settings().removeEmptyDelayTicks);
        int task = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            emptyTasks.remove(data.getId());
            Optional<DeathChestData> current = byId(data.getId());
            if (current.isEmpty()) {
                return;
            }
            DeathChestData chest = current.get();
            if (existsInWorld(chest) && currentItems(chest).isEmpty()) {
                plugin.audit().chest(chest, "因空箱自动移除", "");
                updateRecordStatus(chest, com.npucraft.deathchest.model.RecordStatus.RETRIEVED);
                removeAndStoreRemaining(chest, false);
            }
        }, delay);
        emptyTasks.put(data.getId(), task);
    }

    public void cancelEmptyTask(String id) {
        Integer task = emptyTasks.remove(id);
        if (task != null) {
            Bukkit.getScheduler().cancelTask(task);
        }
    }

    public void expire(DeathChestData data) {
        List<ItemStack> items = currentItems(data);
        boolean drop = plugin.settings().expireMode != ExpireMode.DELETE_ITEMS;
        if (drop) {
            dropItems(data, items);
        }
        updateRecordStatus(data, com.npucraft.deathchest.model.RecordStatus.EXPIRED);
        destroySilently(data);
        plugin.audit().log(AuditEventType.CHEST_EXPIRED, null, "DeathChest", data.getOwnerUuid(), data.getOwnerName(),
                data.getId(), data.getRecordId(), plugin.settings().expireMode.name(), false);
        Player owner = Bukkit.getPlayer(data.getOwnerUuid());
        if (owner != null && drop) {
            plugin.messages().send(owner, "death-chest-expired-drops", Map.of(
                    "id", data.getId(),
                    "world", data.getWorld(),
                    "x", String.valueOf(data.getX()),
                    "y", String.valueOf(data.getY()),
                    "z", String.valueOf(data.getZ())
            ));
        }
    }

    private void updateRecordStatus(DeathChestData chest, com.npucraft.deathchest.model.RecordStatus status) {
        if (chest.getRecordId() == null) {
            return;
        }
        try {
            plugin.records().get(chest.getRecordId()).ifPresent(record -> {
                record.setStatus(status);
                plugin.records().save(record);
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to update death record status for " + chest.getId() + ": "
                    + exception.getMessage());
        }
    }

    private void dropItems(DeathChestData data, List<ItemStack> items) {
        World world = Bukkit.getWorld(data.getWorld());
        if (world == null) {
            if (!plugin.recovery().store(data.getOwnerUuid(), data.getRecordId(), items)) {
                plugin.getLogger().severe("Cannot drop remaining items for death chest " + data.getId()
                        + ": world is unloaded and recovery storage is disabled.");
            }
            return;
        }
        org.bukkit.Location location = data.primaryLocation(world).add(0, 0.5, 0);
        for (ItemStack item : items) {
            if (!ItemStacks.isEmpty(item)) {
                world.dropItemNaturally(location, item);
            }
        }
    }

    private void revertBlock(Block block, org.bukkit.block.data.BlockData previous) {
        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            chest.getBlockInventory().clear();
            chest.update(true, false);
        }
        block.setBlockData(previous, false);
    }

    private void applyChestData(Block block, BlockFace facing, Chest.Type type, boolean waterlogged) {
        if (!(block.getBlockData() instanceof Chest chest)) {
            return;
        }
        chest.setFacing(facing);
        chest.setType(type);
        if (waterlogged && chest instanceof Waterlogged logged) {
            logged.setWaterlogged(true);
        }
        block.setBlockData(chest, false);
    }

    private void storeAssignedItems(Block primary, Block secondary, ChestType type, List<ItemStack> items) {
        int slots = type.slots();
        Inventory packed = Bukkit.createInventory(null, slots);
        HashMap<Integer, ItemStack> leftover = packed.addItem(ItemStacks.deepCopy(items).toArray(ItemStack[]::new));
        if (!leftover.isEmpty()) {
            throw new IllegalStateException("Assigned items exceed planned death chest capacity: slots=" + slots
                    + " leftover=" + leftover.size());
        }

        org.bukkit.block.Chest primaryChest = physicalChest(primary);
        primaryChest.getBlockInventory().setContents(Arrays.copyOfRange(packed.getContents(), 0, 27));
        List<ItemStack> stored = new ArrayList<>(ItemStacks.fromArray(primaryChest.getBlockInventory().getContents()));
        if (type == ChestType.DOUBLE) {
            org.bukkit.block.Chest secondaryChest = physicalChest(secondary);
            secondaryChest.getBlockInventory().setContents(Arrays.copyOfRange(packed.getContents(), 27, 54));
            stored.addAll(ItemStacks.fromArray(secondaryChest.getBlockInventory().getContents()));
        }
        if (!ItemMatcher.matches(items, stored)) {
            throw new IllegalStateException("Death chest item verification failed after physical write");
        }
    }

    private org.bukkit.block.Chest physicalChest(Block block) {
        if (block == null || !(block.getState() instanceof org.bukkit.block.Chest chest)) {
            throw new IllegalStateException("Death chest block inventory missing after placement");
        }
        return chest;
    }

    private List<org.bukkit.block.Chest> ownedChestBlocks(DeathChestData data) {
        World world = Bukkit.getWorld(data.getWorld());
        if (world == null || !chunkLoaded(data)) {
            return List.of();
        }
        List<org.bukkit.block.Chest> blocks = new ArrayList<>(2);
        Block primary = world.getBlockAt(data.getX(), data.getY(), data.getZ());
        if (!isOwnedBlock(primary, data) || !(primary.getState() instanceof org.bukkit.block.Chest primaryChest)) {
            return List.of();
        }
        blocks.add(primaryChest);
        if (data.getChestType() == ChestType.DOUBLE) {
            if (data.getSecondX() == null || data.getSecondY() == null || data.getSecondZ() == null) {
                return List.of();
            }
            Block secondary = world.getBlockAt(data.getSecondX(), data.getSecondY(), data.getSecondZ());
            if (!isOwnedBlock(secondary, data) || !(secondary.getState() instanceof org.bukkit.block.Chest secondaryChest)) {
                return List.of();
            }
            blocks.add(secondaryChest);
        }
        return blocks;
    }

    private Chest.Type partnerType(Block primary, Block secondary, BlockFace facing, boolean forPrimary) {
        if (secondary == null) {
            return Chest.Type.SINGLE;
        }
        Chest.Type primaryType = primaryChestType(facing,
                secondary.getX() - primary.getX(), secondary.getZ() - primary.getZ());
        if (forPrimary) {
            return primaryType;
        }
        return primaryType == Chest.Type.LEFT ? Chest.Type.RIGHT : Chest.Type.LEFT;
    }

    static Chest.Type primaryChestType(BlockFace facing, int partnerOffsetX, int partnerOffsetZ) {
        BlockFace clockwise = switch (facing) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> throw new IllegalArgumentException("Chest facing must be horizontal: " + facing);
        };
        if (partnerOffsetX == clockwise.getModX() && partnerOffsetZ == clockwise.getModZ()) {
            return Chest.Type.LEFT;
        }
        if (partnerOffsetX == -clockwise.getModX() && partnerOffsetZ == -clockwise.getModZ()) {
            return Chest.Type.RIGHT;
        }
        throw new IllegalArgumentException("Death chest partner is not perpendicular to facing " + facing
                + ": dx=" + partnerOffsetX + " dz=" + partnerOffsetZ);
    }

    private void validateDoubleChest(Block primary, Block secondary) {
        Inventory primaryInventory = physicalChest(primary).getInventory();
        Inventory secondaryInventory = physicalChest(secondary).getInventory();
        if (primaryInventory.getSize() != 54 || secondaryInventory.getSize() != 54) {
            throw new IllegalStateException("Double death chest did not form a combined 54-slot inventory: primary="
                    + primaryInventory.getSize() + " secondary=" + secondaryInventory.getSize());
        }
    }

    private void writePdc(Block block, DeathChestData data) {
        if (block.getState() instanceof TileState tile) {
            plugin.keys().writeChest(tile, data.getId(), data.getRecordId(), data.getOwnerUuid().toString(),
                    data.getCreatedAt(), data.getUnlockAt(), data.getExpireAt());
        }
    }

    private void clearBlock(Block block) {
        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            chest.getBlockInventory().clear();
            chest.update(true, false);
        }
        block.setType(Material.AIR, false);
    }

    private boolean isOwnedBlock(Block block, DeathChestData data) {
        if (block == null || data == null || !(block.getState() instanceof TileState tile)) {
            return false;
        }
        LocationKey key = LocationKey.of(block.getLocation());
        return data.blockKeys().contains(key)
                && data.getId().equals(plugin.keys().readChestId(tile))
                && Objects.equals(data.getRecordId(), plugin.keys().readRecordId(tile))
                && Objects.equals(data.getOwnerUuid(), plugin.keys().readOwnerUuid(tile))
                && data.getCreatedAt() == plugin.keys().readLong(tile, plugin.keys().createdAt, Long.MIN_VALUE);
    }

    private boolean hasCompleteIdentity(DeathChestData data) {
        World world = Bukkit.getWorld(data.getWorld());
        if (world == null || !chunkLoaded(data)
                || !isOwnedBlock(world.getBlockAt(data.getX(), data.getY(), data.getZ()), data)) {
            return false;
        }
        return data.getChestType() != ChestType.DOUBLE || data.getSecondX() == null
                || isOwnedBlock(world.getBlockAt(data.getSecondX(), data.getSecondY(), data.getSecondZ()), data);
    }

    private void appendOwnedBlockItems(Block block, DeathChestData data, List<ItemStack> items) {
        if (!isOwnedBlock(block, data) || !(block.getState() instanceof org.bukkit.block.Chest chest)) {
            return;
        }
        items.addAll(ItemStacks.fromArray(chest.getBlockInventory().getContents()));
    }

    private void clearBlockIfOwned(Block block, DeathChestData data) {
        if (isOwnedBlock(block, data)) {
            clearBlock(block);
        }
    }

    private void index(DeathChestData data) {
        byId.put(data.getId(), data);
        for (LocationKey key : data.blockKeys()) {
            byLocation.put(key, data.getId());
        }
        byOwner.computeIfAbsent(data.getOwnerUuid(), uuid -> new ArrayList<>()).remove(data.getId());
        byOwner.computeIfAbsent(data.getOwnerUuid(), uuid -> new ArrayList<>()).add(data.getId());
    }

    private void unindex(DeathChestData data) {
        byId.remove(data.getId());
        for (LocationKey key : data.blockKeys()) {
            byLocation.remove(key);
        }
        List<String> ids = byOwner.get(data.getOwnerUuid());
        if (ids != null) {
            ids.remove(data.getId());
        }
    }
}
