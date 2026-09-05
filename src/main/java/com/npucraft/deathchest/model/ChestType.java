package com.npucraft.deathchest.model;

public enum ChestType {
    SINGLE,
    DOUBLE;

    public int slots() {
        return this == DOUBLE ? 54 : 27;
    }
}
