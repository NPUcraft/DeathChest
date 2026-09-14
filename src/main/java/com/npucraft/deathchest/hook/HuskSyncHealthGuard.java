package com.npucraft.deathchest.hook;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;

/** Uses the HuskSync v3 PreSyncEvent API without requiring HuskSync on standalone servers. */
final class HuskSyncHealthGuard {
    private final Method editData;
    private final Method getData;
    private final Method getId;
    private final Method getHealthData;
    private final Method getHealth;
    private final Method setHealth;

    HuskSyncHealthGuard(Class<?> eventType, Class<?> dataHolderType, Class<?> healthType)
            throws ReflectiveOperationException {
        editData = eventType.getMethod("editData", Consumer.class);
        getData = eventType.getMethod("getData");
        getId = getData.getReturnType().getMethod("getId");
        getHealthData = dataHolderType.getMethod("getHealth");
        getHealth = healthType.getMethod("getHealth");
        setHealth = healthType.getMethod("setHealth", double.class);
    }

    /** Returns the adjusted snapshot ID, or null when no correction was necessary. */
    String protect(Object event) throws ReflectiveOperationException {
        boolean[] adjusted = {false};
        // editData repacks the change into the event's snapshot before HuskSync applies it.
        // Do not access Bukkit players here: PreSyncEvent may run asynchronously.
        editData.invoke(event, (Consumer<Object>) data -> {
            try {
                Optional<?> optional = (Optional<?>) getHealthData.invoke(data);
                if (optional.isEmpty()) {
                    return;
                }
                Object healthData = optional.get();
                double health = ((Number) getHealth.invoke(healthData)).doubleValue();
                if (!Double.isFinite(health) || health <= 0.0D) {
                    setHealth.invoke(healthData, 1.0D);
                    adjusted[0] = true;
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not protect HuskSync snapshot health", exception);
            }
        });
        return adjusted[0] ? String.valueOf(getId.invoke(getData.invoke(event))) : null;
    }
}
