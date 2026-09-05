package com.npucraft.deathchest.model;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ChestPlacement {
    private final ChestType type;
    private final Block primary;
    private final Block secondary;
    private final BlockFace facing;
    private final List<ItemStack> items = new ArrayList<>();

    public ChestPlacement(ChestType type, Block primary, Block secondary, BlockFace facing) {
        this.type = type;
        this.primary = primary;
        this.secondary = secondary;
        this.facing = facing;
    }

    public ChestType getType() {
        return type;
    }

    public Block getPrimary() {
        return primary;
    }

    public Block getSecondary() {
        return secondary;
    }

    public BlockFace getFacing() {
        return facing;
    }

    public List<ItemStack> getItems() {
        return items;
    }
}
