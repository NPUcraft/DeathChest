package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.AuditEventType;
import com.npucraft.deathchest.model.AuditLogEntry;
import com.npucraft.deathchest.model.DeathChestData;

import java.util.UUID;
import java.util.logging.Level;

public final class AuditLogger {
    private final DeathChestPlugin plugin;

    public AuditLogger(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public void player(String actor, String action, String details) {
        console(Level.INFO, "玩家 " + nvl(actor) + " " + action + suffix(details));
    }

    public void chest(String action, String details) {
        console(Level.INFO, "死亡箱 " + action + suffix(details));
    }

    public void chest(DeathChestData data, String action, String details) {
        if (data == null) {
            chest(action, details);
            return;
        }
        console(Level.INFO, "死亡箱 " + nvl(data.getId()) + " " + action
                + " 主人=" + nvl(data.getOwnerName())
                + " " + location(data)
                + suffix(details));
    }

    public void log(AuditEventType type, UUID actorUuid, String actorName, UUID targetUuid, String targetName,
                    String chestId, String recordId, String details, boolean force) {
        Level level = type == AuditEventType.ERROR ? Level.WARNING : Level.INFO;
        console(level, format(type, actorName, targetName, chestId, recordId, details, force));
        if (!plugin.settings().auditEnabled) {
            return;
        }
        AuditLogEntry entry = new AuditLogEntry();
        entry.setEventType(type.name());
        entry.setTimestamp(System.currentTimeMillis());
        entry.setActorUuid(actorUuid);
        entry.setActorName(actorName);
        entry.setTargetUuid(targetUuid);
        entry.setTargetName(targetName);
        entry.setDeathChestId(chestId);
        entry.setRecordId(recordId);
        entry.setDetails(details);
        entry.setForce(force);
        try {
            plugin.storage().saveAudit(entry);
        } catch (Exception exception) {
            plugin.getLogger().warning("写入审计数据库失败: " + exception.getMessage());
        }
    }

    private void console(Level level, String message) {
        if (!plugin.settings().auditLogToConsole || message == null || message.isBlank()) {
            return;
        }
        plugin.getLogger().log(level, message);
    }

    private String format(AuditEventType type, String actorName, String targetName, String chestId, String recordId,
                          String details, boolean force) {
        String extra = suffix(details) + (force ? " force=true" : "");
        String chest = nvl(chestId);
        String owner = nvl(targetName);
        String actor = nvl(actorName);
        String record = nvl(recordId);
        return switch (type) {
            case DEATH_PREPARED -> "玩家 " + owner + " 死亡，已记录掉落 record=" + record + extra;
            case DEATH_COMMITTED -> "玩家 " + owner + " 死亡箱事务已提交 chest=" + chest + " record=" + record + extra;
            case CHEST_CREATED -> "死亡箱 " + chest + " 已放置 主人=" + owner + extra;
            case CHEST_UNLOCKED -> "死亡箱 " + chest + " 已公开 主人=" + owner + " 操作者=" + actor + extra;
            case ADMIN_UNLOCK -> "死亡箱 " + chest + " 被管理员公开 主人=" + owner + " 操作者=" + actor + extra;
            case CHEST_REMOVED -> "死亡箱 " + chest + " 已移除 主人=" + owner + extra;
            case CHEST_EXPIRED -> "死亡箱 " + chest + " 已过期 主人=" + owner + extra;
            case QUICK_RETRIEVE -> "玩家 " + actor + " 快速取回死亡箱 " + chest + " 主人=" + owner + extra;
            case ECONOMY_WITHDRAW -> "玩家 " + owner + " 死亡箱扣费 chest=" + chest + " record=" + record + extra;
            case ADMIN_RESTORE -> "管理员 " + actor + " 回滚 record=" + record + " 目标=" + owner + extra;
            case ADMIN_FORCE_RESTORE -> "管理员 " + actor + " 强制回滚 record=" + record + " 目标=" + owner + extra;
            case ERROR -> "错误 操作者=" + actor + " 目标=" + owner + " chest=" + chest + " record=" + record + extra;
        };
    }

    public static String location(DeathChestData data) {
        if (data == null) {
            return "-";
        }
        return nvl(data.getWorld()) + " " + data.getX() + " " + data.getY() + " " + data.getZ();
    }

    private static String suffix(String details) {
        if (details == null || details.isBlank()) {
            return "";
        }
        return " " + details;
    }

    private static String nvl(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
