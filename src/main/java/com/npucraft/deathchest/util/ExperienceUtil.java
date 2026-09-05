package com.npucraft.deathchest.util;

import com.npucraft.deathchest.model.ExperienceMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class ExperienceUtil {
    private ExperienceUtil() {
    }

    public static int totalExperience(Player player) {
        int level = Math.max(0, player.getLevel());
        float progress = Math.max(0F, Math.min(1F, player.getExp()));
        int intoLevel = Math.round(progress * player.getExpToLevel());
        return experienceAtLevel(level) + intoLevel;
    }

    public static int experienceAtLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) Math.floor(2.5 * level * level - 40.5 * level + 360);
        }
        return (int) Math.floor(4.5 * level * level - 162.5 * level + 2220);
    }

    public static int expToNext(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        }
        if (level <= 30) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    public static LevelProgress fromTotal(int total) {
        int remaining = Math.max(0, total);
        int level = 0;
        while (true) {
            int needed = expToNext(level);
            if (remaining < needed) {
                float progress = needed == 0 ? 0F : (float) remaining / (float) needed;
                return new LevelProgress(level, remaining, progress, total);
            }
            remaining -= needed;
            level++;
            if (level > 100000) {
                return new LevelProgress(level, 0, 0F, total);
            }
        }
    }

    public static KeptExperience calculate(Player player, ExperienceMode mode, int percentage) {
        int before = totalExperience(player);
        int kept;
        switch (mode) {
            case KEEP_ALL -> kept = before;
            case NONE -> kept = 0;
            case PERCENTAGE -> {
                int pct = Math.max(0, Math.min(100, percentage));
                kept = (int) Math.floor(before * (pct / 100.0D));
            }
            default -> {
                return new KeptExperience(before, -1, -1, true);
            }
        }
        int lost = Math.max(0, before - kept);
        return new KeptExperience(before, kept, lost, false);
    }

    public static void applyToDeathEvent(PlayerDeathEvent event, int keptTotal) {
        LevelProgress progress = fromTotal(keptTotal);
        event.setKeepLevel(false);
        event.setShouldDropExperience(false);
        event.setDroppedExp(0);
        event.setNewLevel(progress.level());
        event.setNewExp(progress.intoLevel());
        event.setNewTotalExp(keptTotal);
    }

    public static void applyToPlayer(Player player, int total) {
        LevelProgress progress = fromTotal(Math.max(0, total));
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0F);
        player.setLevel(progress.level());
        player.setExp(progress.progress());
        player.giveExp(0);
        player.setTotalExperience(Math.max(0, total));
        player.setLevel(progress.level());
        player.setExp(progress.progress());
    }

    public record LevelProgress(int level, int intoLevel, float progress, int total) {
    }

    public record KeptExperience(int before, int kept, int lost, boolean vanilla) {
    }
}
