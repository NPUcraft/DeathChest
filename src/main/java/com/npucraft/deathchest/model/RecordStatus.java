package com.npucraft.deathchest.model;

public enum RecordStatus {
    PREPARED,
    CHEST_CREATED,
    COMMITTED,
    RETRIEVED,
    NORMAL_DROP,
    FAILED,
    ROLLED_BACK,
    ADMIN_RESTORED,
    PARTIALLY_RESTORED,
    EXPIRED
}
