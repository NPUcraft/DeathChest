package com.npucraft.deathchest.hook;

import com.npucraft.deathchest.DeathChestPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Native CoinsEngine hook via reflection. Also accepts ExcellentEconomy (CoinsEngine 2.8+ rebrand)
 * so servers on either plugin name can use provider: COINSENGINE.
 */
public final class CoinsEngineEconomyProvider implements EconomyProvider {
    private final DeathChestPlugin plugin;
    private final String currencyId;
    private Object api;
    private Object currency;
    private String currencyName;
    private Backend backend = Backend.NONE;

    private enum Backend {
        NONE,
        COINS_ENGINE,
        EXCELLENT_ECONOMY
    }

    public CoinsEngineEconomyProvider(DeathChestPlugin plugin, String currencyId) {
        this.plugin = plugin;
        this.currencyId = currencyId == null || currencyId.isBlank() ? "coins" : currencyId;
        hook();
    }

    public void hook() {
        this.api = null;
        this.currency = null;
        this.currencyName = currencyId;
        this.backend = Backend.NONE;
        if (plugin.getServer().getPluginManager().getPlugin("CoinsEngine") != null) {
            if (hookCoinsEngine()) {
                return;
            }
        }
        if (plugin.getServer().getPluginManager().getPlugin("ExcellentEconomy") != null) {
            if (hookExcellentEconomy()) {
                return;
            }
        }
        plugin.getLogger().severe("Economy provider is COINSENGINE but CoinsEngine/ExcellentEconomy was not found or the currency '" + currencyId + "' does not exist.");
    }

    private boolean hookCoinsEngine() {
        try {
            Class<?> apiClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            Class<?> currencyClass = Class.forName("su.nightexpress.coinsengine.api.currency.Currency");
            Method getCurrency = apiClass.getMethod("getCurrency", String.class);
            Object found = getCurrency.invoke(null, currencyId);
            if (found == null) {
                plugin.getLogger().severe("CoinsEngine is installed but currency '" + currencyId + "' does not exist.");
                return false;
            }
            this.api = apiClass;
            this.currency = found;
            this.backend = Backend.COINS_ENGINE;
            this.currencyName = invokeString(found, "getName", invokeString(found, "getId", currencyId));
            plugin.getLogger().info("Hooked CoinsEngine currency: " + currencyName);
            return true;
        } catch (ClassNotFoundException exception) {
            plugin.getLogger().warning("CoinsEngine plugin is present but API classes were not found.");
            return false;
        } catch (Exception exception) {
            plugin.getLogger().severe("Failed to hook CoinsEngine: " + exception.getMessage());
            return false;
        }
    }

    private boolean hookExcellentEconomy() {
        try {
            Class<?> apiClass = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI");
            ServicesManager services = Bukkit.getServicesManager();
            RegisteredServiceProvider<?> provider = services.getRegistration(apiClass);
            Object instance = provider == null ? null : provider.getProvider();
            if (instance == null) {
                plugin.getLogger().severe("ExcellentEconomy is installed but its API service is not registered yet.");
                return false;
            }
            Method hasCurrency = findMethod(apiClass, "hasCurrency", String.class);
            if (hasCurrency != null) {
                Object has = hasCurrency.invoke(instance, currencyId);
                if (has instanceof Boolean bool && !bool) {
                    plugin.getLogger().severe("ExcellentEconomy is installed but currency '" + currencyId + "' does not exist.");
                    return false;
                }
            }
            Method getCurrency = findMethod(apiClass, "getCurrency", String.class);
            Object found = getCurrency == null ? null : getCurrency.invoke(instance, currencyId);
            this.api = instance;
            this.currency = found;
            this.backend = Backend.EXCELLENT_ECONOMY;
            this.currencyName = found == null ? currencyId : invokeString(found, "getName", currencyId);
            plugin.getLogger().info("Hooked ExcellentEconomy (CoinsEngine successor) currency: " + currencyName);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        } catch (Exception exception) {
            plugin.getLogger().severe("Failed to hook ExcellentEconomy: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public String id() {
        return "COINSENGINE";
    }

    @Override
    public boolean available() {
        return backend != Backend.NONE && api != null;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (!available()) {
            throw new IllegalStateException("CoinsEngine economy is unavailable");
        }
        try {
            if (backend == Backend.COINS_ENGINE) {
                Class<?> apiClass = (Class<?>) api;
                Class<?> currencyClass = Class.forName("su.nightexpress.coinsengine.api.currency.Currency");
                Method method = findBalanceMethod(apiClass, currencyClass, player);
                if (method == null) {
                    throw new NoSuchMethodException("No supported CoinsEngine getBalance method");
                }
                Object value = invokeBalance(method, player);
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
                throw new IllegalStateException("CoinsEngine getBalance returned a non-numeric value");
            }
            Method method = findMethod(api.getClass(), "getBalance", Player.class, String.class);
            if (method != null && player.isOnline()) {
                Object value = method.invoke(api, player.getPlayer(), currencyId);
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
                throw new IllegalStateException("ExcellentEconomy getBalance returned a non-numeric value");
            }
            Method uuidMethod = findMethod(api.getClass(), "getBalance", UUID.class, String.class);
            if (uuidMethod != null) {
                Object value = uuidMethod.invoke(api, player.getUniqueId(), currencyId);
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
                throw new IllegalStateException("ExcellentEconomy getBalance returned a non-numeric value");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("CoinsEngine getBalance failed", exception);
        }
        throw new IllegalStateException("No supported ExcellentEconomy getBalance method");
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return amount <= 0.0D || getBalance(player) + 0.000001D >= amount;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        if (!available() || !has(player, amount)) {
            return false;
        }
        return mutate(player, amount, false);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        if (!available()) {
            return false;
        }
        return mutate(player, amount, true);
    }

    private boolean mutate(OfflinePlayer player, double amount, boolean deposit) {
        try {
            if (backend == Backend.COINS_ENGINE) {
                Class<?> apiClass = (Class<?>) api;
                Class<?> currencyClass = Class.forName("su.nightexpress.coinsengine.api.currency.Currency");
                String name = deposit ? "addBalance" : "removeBalance";
                Method method = findMutateMethod(apiClass, currencyClass, name);
                if (method == null) {
                    return false;
                }
                Object result = invokeMutate(method, player, amount);
                return result == null || Boolean.TRUE.equals(result);
            }
            String name = deposit ? "deposit" : "withdraw";
            if (player.isOnline()) {
                Method method = findMethod(api.getClass(), name, Player.class, String.class, double.class);
                if (method != null) {
                    Object result = method.invoke(api, player.getPlayer(), currencyId, amount);
                    return result == null || Boolean.TRUE.equals(result);
                }
            }
            Method uuidMethod = findMethod(api.getClass(), name, UUID.class, String.class, double.class);
            if (uuidMethod != null) {
                Object result = uuidMethod.invoke(api, player.getUniqueId(), currencyId, amount);
                return result == null || Boolean.TRUE.equals(result);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("CoinsEngine balance update failed: " + exception.getMessage());
            return false;
        }
        return false;
    }

    private Method findBalanceMethod(Class<?> apiClass, Class<?> currencyClass, OfflinePlayer player) {
        if (player.isOnline()) {
            Method onlineMethod = findMethod(apiClass, "getBalance", Player.class, currencyClass);
            if (onlineMethod != null) {
                return onlineMethod;
            }
        }
        Method method = findMethod(apiClass, "getBalance", OfflinePlayer.class, currencyClass);
        if (method != null) {
            return method;
        }
        return findMethod(apiClass, "getBalance", UUID.class, currencyClass);
    }

    private Method findMutateMethod(Class<?> apiClass, Class<?> currencyClass, String name) {
        Method method = findMethod(apiClass, name, Player.class, currencyClass, double.class);
        if (method != null) {
            return method;
        }
        method = findMethod(apiClass, name, OfflinePlayer.class, currencyClass, double.class);
        if (method != null) {
            return method;
        }
        return findMethod(apiClass, name, UUID.class, currencyClass, double.class);
    }

    private Object invokeBalance(Method method, OfflinePlayer player) throws Exception {
        Class<?> first = method.getParameterTypes()[0];
        if (first == Player.class) {
            Player online = player.getPlayer();
            if (online == null) {
                throw new IllegalStateException("CoinsEngine requires an online player for balance lookup");
            }
            return method.invoke(null, online, currency);
        }
        if (first == UUID.class) {
            return method.invoke(null, player.getUniqueId(), currency);
        }
        return method.invoke(null, player, currency);
    }

    private Object invokeMutate(Method method, OfflinePlayer player, double amount) throws Exception {
        Class<?> first = method.getParameterTypes()[0];
        if (first == Player.class) {
            Player online = player.getPlayer();
            if (online == null) {
                return false;
            }
            return method.invoke(null, online, currency, amount);
        }
        if (first == UUID.class) {
            return method.invoke(null, player.getUniqueId(), currency, amount);
        }
        return method.invoke(null, player, currency, amount);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static String invokeString(Object target, String method, String fallback) {
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            return value == null ? fallback : String.valueOf(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    public String getCurrencyName() {
        return currencyName == null ? currencyId : currencyName;
    }

    @Override
    public String getCurrencyId() {
        return currencyId;
    }
}
