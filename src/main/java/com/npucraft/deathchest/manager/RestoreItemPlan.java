package com.npucraft.deathchest.manager;

/**
 * Decides where restore items should come from so --force cannot duplicate a snapshot
 * that was already dropped, recovered, or looted from the chest.
 */
public final class RestoreItemPlan {
    private RestoreItemPlan() {
    }

    public record Decision(boolean refuse, String refuseKey, boolean useRecovery, boolean useChestContents,
                           boolean useSnapshot) {
        public static Decision skip() {
            return new Decision(false, null, false, false, false);
        }

        public static Decision refuse(String key) {
            return new Decision(true, key, false, false, false);
        }

        public static Decision give(boolean recovery, boolean chests, boolean snapshot) {
            return new Decision(false, null, recovery, chests, snapshot);
        }
    }

    public static Decision decide(boolean restoreItems, boolean force, boolean deathChestCreated,
                                  boolean chestsExist, boolean recoveryPending, boolean snapshotEmpty,
                                  String failureReason) {
        if (!restoreItems) {
            return Decision.skip();
        }
        if (!force) {
            if (recoveryPending) {
                return Decision.refuse("restore-safe-in-recovery");
            }
            if (!deathChestCreated) {
                if ("VIRTUAL_STORAGE".equals(failureReason)) {
                    return Decision.refuse("restore-safe-virtual-storage");
                }
                return Decision.refuse("restore-safe-never-created");
            }
            if (!chestsExist) {
                return Decision.refuse("restore-safe-missing-chest");
            }
            if (snapshotEmpty) {
                return Decision.refuse("restore-no-snapshot");
            }
            return Decision.give(false, false, true);
        }
        if (recoveryPending) {
            return Decision.give(true, chestsExist, false);
        }
        if (chestsExist) {
            return Decision.give(false, true, false);
        }
        if (!deathChestCreated) {
            return Decision.refuse("restore-force-already-dropped");
        }
        if (snapshotEmpty) {
            return Decision.refuse("restore-no-snapshot");
        }
        return Decision.give(false, false, true);
    }
}
