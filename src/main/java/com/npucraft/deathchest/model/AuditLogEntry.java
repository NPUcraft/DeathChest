package com.npucraft.deathchest.model;

import java.util.UUID;

public class AuditLogEntry {
    private long id;
    private String eventType;
    private long timestamp;
    private UUID actorUuid;
    private String actorName;
    private UUID targetUuid;
    private String targetName;
    private String deathChestId;
    private String recordId;
    private String details;
    private boolean force;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public UUID getActorUuid() {
        return actorUuid;
    }

    public void setActorUuid(UUID actorUuid) {
        this.actorUuid = actorUuid;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public void setTargetUuid(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getDeathChestId() {
        return deathChestId;
    }

    public void setDeathChestId(String deathChestId) {
        this.deathChestId = deathChestId;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
}
