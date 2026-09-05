package com.npucraft.deathchest.hook;

import com.npucraft.deathchest.DeathChestPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public final class ResidenceIntegration implements ProtectionIntegration {
    private final DeathChestPlugin plugin;
    private boolean available;
    private Object residencePlugin;
    private Method getResidenceManager;
    private Method getByLoc;
    private Method getPermissions;
    private Method playerHasPlayerFlag;
    private Method playerHasNameFlag;

    public ResidenceIntegration(DeathChestPlugin plugin) {
        this.plugin = plugin;
        hook();
    }

    public void hook() {
        this.available = false;
        if (!plugin.settings().residenceEnabled) {
            return;
        }
        if (plugin.getServer().getPluginManager().getPlugin("Residence") == null) {
            return;
        }
        try {
            Class<?> residenceClass = Class.forName("com.bekvon.bukkit.residence.Residence");
            Method getInstance = findMethod(residenceClass, "getInstance");
            Object instance = getInstance == null ? null : getInstance.invoke(null);
            if (instance == null) {
                Method getResidenceManagerStatic = findMethod(residenceClass, "getResidenceManager");
                this.residencePlugin = residenceClass;
                this.getResidenceManager = getResidenceManagerStatic;
            } else {
                this.residencePlugin = instance;
                this.getResidenceManager = findMethod(residenceClass, "getResidenceManager");
            }
            if (getResidenceManager == null) {
                plugin.getLogger().warning("Residence is installed but ResidenceManager could not be resolved. Skipping Residence checks.");
                return;
            }
            Object manager = getResidenceManager.getParameterCount() == 0
                    ? (residencePlugin instanceof Class<?> ? getResidenceManager.invoke(null) : getResidenceManager.invoke(residencePlugin))
                    : null;
            if (manager == null) {
                plugin.getLogger().warning("Residence manager is unavailable. Skipping Residence checks.");
                return;
            }
            this.getByLoc = findMethod(manager.getClass(), "getByLoc", Location.class);
            this.available = getByLoc != null;
            if (available) {
                plugin.getLogger().info("Hooked Residence protection integration.");
            }
        } catch (ClassNotFoundException ignored) {
            plugin.getLogger().warning("Residence plugin is present but API classes were not found.");
        } catch (LinkageError error) {
            plugin.getLogger().warning("Residence API has a missing optional dependency ("
                    + error.getClass().getSimpleName() + "). Skipping Residence checks: " + error.getMessage());
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to hook Residence: " + exception.getMessage());
        }
    }

    @Override
    public String getName() {
        return "Residence";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean canCreateDeathChest(Player player, Location location) {
        if (!available || player == null || location == null) {
            return true;
        }
        try {
            Object manager = getResidenceManager.getParameterCount() == 0
                    ? (residencePlugin instanceof Class<?> ? getResidenceManager.invoke(null) : getResidenceManager.invoke(residencePlugin))
                    : getResidenceManager.invoke(residencePlugin);
            if (manager == null || getByLoc == null) {
                return true;
            }
            Object residence = getByLoc.invoke(manager, location);
            if (residence == null) {
                return true;
            }
            if (!plugin.settings().avoidNoPermissionResidence) {
                return true;
            }
            Object permissions = permissions(residence);
            if (permissions == null) {
                return true;
            }
            if (plugin.settings().checkBuildPermission && !playerHas(permissions, player, location, "build")) {
                return false;
            }
            if (plugin.settings().checkPlacePermission && !playerHas(permissions, player, location, "place")) {
                return false;
            }
            if (plugin.settings().checkContainerPermission && !playerHas(permissions, player, location, "container")) {
                return false;
            }
            return true;
        } catch (Exception | LinkageError exception) {
            plugin.debug("Residence check failed: " + exception.getMessage());
            return true;
        }
    }

    private Object permissions(Object residence) throws Exception {
        if (getPermissions == null) {
            getPermissions = findMethod(residence.getClass(), "getPermissions");
            if (getPermissions == null) {
                getPermissions = findMethod(residence.getClass(), "getPermisssions");
            }
        }
        return getPermissions == null ? null : getPermissions.invoke(residence);
    }

    private boolean playerHas(Object permissions, Player player, Location location, String flag) throws Exception {
        if (playerHasPlayerFlag == null && playerHasNameFlag == null) {
            playerHasPlayerFlag = findMethod(permissions.getClass(), "playerHas", Player.class, String.class, boolean.class);
            if (playerHasPlayerFlag == null) {
                Class<?> flagsClass = tryLoad("com.bekvon.bukkit.residence.containers.Flags");
                if (flagsClass != null && flagsClass.isEnum()) {
                    playerHasPlayerFlag = findMethod(permissions.getClass(), "playerHas", Player.class, flagsClass, boolean.class);
                }
            }
            playerHasNameFlag = findMethod(permissions.getClass(), "playerHas", String.class, String.class, boolean.class);
            if (playerHasNameFlag == null) {
                playerHasNameFlag = findMethod(permissions.getClass(), "playerHas", String.class, boolean.class);
            }
        }
        if (playerHasPlayerFlag != null) {
            Class<?>[] types = playerHasPlayerFlag.getParameterTypes();
            Object flagArg = flagArgument(types[types.length == 3 ? 1 : 1], flag);
            Object result = playerHasPlayerFlag.invoke(permissions, player, flagArg, true);
            return result instanceof Boolean bool ? bool : true;
        }
        if (playerHasNameFlag != null) {
            Class<?>[] types = playerHasNameFlag.getParameterTypes();
            if (types.length == 3) {
                Object result = playerHasNameFlag.invoke(permissions, player.getName(), flag, true);
                return result instanceof Boolean bool ? bool : true;
            }
            Object result = playerHasNameFlag.invoke(permissions, player.getName(), true);
            return result instanceof Boolean bool ? bool : true;
        }
        plugin.debug("Residence permissions API did not expose playerHas for flag " + flag + " at " + location);
        return true;
    }

    private Object flagArgument(Class<?> type, String flag) {
        if (type == String.class) {
            return flag;
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            if (constants != null) {
                for (Object constant : constants) {
                    if (constant.toString().equalsIgnoreCase(flag)) {
                        return constant;
                    }
                }
            }
        }
        return flag;
    }

    private static Class<?> tryLoad(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException | LinkageError | SecurityException ignored) {
            return null;
        }
    }
}
