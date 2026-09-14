package com.npucraft.deathchest.hook;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class HuskSyncHealthGuardTest {
    private HuskSyncHealthGuard guard() throws ReflectiveOperationException {
        return new HuskSyncHealthGuard(SyncEvent.class, Snapshot.class, Health.class);
    }

    @Test
    void preventsDeathBeforeSnapshotApplicationAndPreservesInventory() throws Exception {
        for (double value : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            SyncEvent event = new SyncEvent(new Health(value));
            assertEquals(event.snapshot.id.toString(), guard().protect(event));
            assertEquals(1.0D, event.snapshot.health.value);
            assertSame(event.inventory, event.snapshot.inventory);
            assertEquals(1, event.edits);
        }
    }

    @Test
    void preservesNormalHealthIncludingLessThanHalfAHeart() throws Exception {
        for (double value : new double[]{0.01D, 1.0D, 10.0D, 40.0D}) {
            SyncEvent event = new SyncEvent(new Health(value));
            assertNull(guard().protect(event));
            assertEquals(value, event.snapshot.health.value);
            assertSame(event.inventory, event.snapshot.inventory);
        }
    }

    @Test
    void acceptsSnapshotsWithoutHealthData() throws Exception {
        SyncEvent event = new SyncEvent(null);
        assertNull(guard().protect(event));
        assertNull(event.snapshot.health);
        assertSame(event.inventory, event.snapshot.inventory);
    }

    @Test
    void propagatesEditingFailureSoCallerCanCancelSync() throws Exception {
        SyncEvent event = new SyncEvent(new Health(0));
        event.failEdit = true;
        assertThrows(InvocationTargetException.class, () -> guard().protect(event));
        assertEquals(0.0D, event.snapshot.health.value);
    }

    @Test
    void rejectsIncompatibleApiDuringRegistration() {
        assertThrows(NoSuchMethodException.class,
                () -> new HuskSyncHealthGuard(Object.class, Snapshot.class, Health.class));
    }

    // Contract fixtures model HuskSync's editData -> unpack -> edit -> repack lifecycle.
    // Editing a transient unpacked object without repacking must not pass these tests.
    public static class SyncEvent {
        final Object inventory = new Object();
        Packed snapshot;
        int edits;
        boolean failEdit;

        SyncEvent(Health health) {
            snapshot = new Packed(health, inventory);
        }

        public void editData(Consumer<Snapshot> editor) {
            if (failEdit) {
                throw new IllegalStateException("Snapshot serialization failed");
            }
            Snapshot unpacked = new Snapshot(snapshot.health == null ? null : new Health(snapshot.health.value));
            editor.accept(unpacked);
            snapshot.health = unpacked.health;
            edits++;
        }

        public Packed getData() {
            return snapshot;
        }
    }

    public static class Packed {
        final UUID id = UUID.randomUUID();
        final Object inventory;
        Health health;

        Packed(Health health, Object inventory) {
            this.health = health;
            this.inventory = inventory;
        }

        public UUID getId() {
            return id;
        }
    }

    public static class Snapshot {
        final Health health;

        Snapshot(Health health) {
            this.health = health;
        }

        public Optional<Health> getHealth() {
            return Optional.ofNullable(health);
        }
    }

    public static class Health {
        double value;

        Health(double value) {
            this.value = value;
        }

        public double getHealth() {
            return value;
        }

        public void setHealth(double value) {
            this.value = value;
        }
    }
}
