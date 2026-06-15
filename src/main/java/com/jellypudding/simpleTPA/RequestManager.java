package com.jellypudding.simpleTPA;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class RequestManager {

    private final SimpleTPA plugin;

    // key = "requesterUUID:targetUUID"
    private final HashMap<String, Long> teleportRequests = new HashMap<>();
    private final HashMap<String, BukkitTask> expirationTasks = new HashMap<>();
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    // tpback state
    private final HashMap<UUID, Location> preTPLocations = new HashMap<>();
    private final HashMap<UUID, Long> preTPTimestamps = new HashMap<>();
    private final HashMap<UUID, Long> tpbackCooldowns = new HashMap<>();
    private final HashMap<UUID, BukkitTask> pendingTpbacks = new HashMap<>();

    private long requestTimeoutTicks;
    private long requestCooldownMillis;
    private boolean allowCrossWorld;

    // tpback config
    private long tpbackCooldownMillis;
    private long tpbackExpiryMillis;
    private boolean tpbackClearOnDeath;
    private int tpbackWarmupTicks;

    public RequestManager(SimpleTPA plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        int timeoutSeconds = plugin.getConfig().getInt("request-timeout", 120);
        requestTimeoutTicks = timeoutSeconds * 20L;

        int cooldownSeconds = plugin.getConfig().getInt("request-cooldown", 10);
        requestCooldownMillis = cooldownSeconds * 1000L;

        allowCrossWorld = plugin.getConfig().getBoolean("allow-cross-world", false);

        int tpbackCooldownSeconds = plugin.getConfig().getInt("tpback-cooldown", 30);
        tpbackCooldownMillis = tpbackCooldownSeconds * 1000L;

        int tpbackExpirySeconds = plugin.getConfig().getInt("tpback-expiry", 300);
        tpbackExpiryMillis = tpbackExpirySeconds * 1000L;

        tpbackClearOnDeath = plugin.getConfig().getBoolean("tpback-clear-on-death", true);

        int warmupSeconds = plugin.getConfig().getInt("tpback-warmup", 3);
        tpbackWarmupTicks = warmupSeconds * 20;

        plugin.getLogger().info("Config loaded: timeout=" + timeoutSeconds + "s, cooldown=" + cooldownSeconds + "s, cross-world=" + allowCrossWorld
                + ", tpback-cooldown=" + tpbackCooldownSeconds + "s, tpback-expiry=" + tpbackExpirySeconds + "s"
                + ", tpback-warmup=" + warmupSeconds + "s");
    }

    public void shutdown() {
        expirationTasks.values().forEach(BukkitTask::cancel);
        teleportRequests.clear();
        expirationTasks.clear();
        cooldowns.clear();
        pendingTpbacks.values().forEach(BukkitTask::cancel);
        pendingTpbacks.clear();
        preTPLocations.clear();
        preTPTimestamps.clear();
        tpbackCooldowns.clear();
    }

    public boolean hasRequest(String requestKey) {
        return teleportRequests.containsKey(requestKey);
    }

    public void addRequest(String requestKey, long timestamp) {
        teleportRequests.put(requestKey, timestamp);
    }

    public void removeRequest(String requestKey) {
        teleportRequests.remove(requestKey);
    }

    // Returns a snapshot of all active request keys.
    public Set<String> getRequestKeys() {
        return new HashSet<>(teleportRequests.keySet());
    }

    public List<UUID> getPendingRequesters(UUID targetUUID) {
        List<UUID> requesters = new ArrayList<>();
        for (String requestKey : teleportRequests.keySet()) {
            String[] parts = requestKey.split(":");
            if (parts.length == 2 && parts[1].equals(targetUUID.toString())) {
                try {
                    requesters.add(UUID.fromString(parts[0]));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return requesters;
    }

    public void scheduleExpiration(String requestKey, UUID requesterUUID, UUID targetUUID) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!teleportRequests.containsKey(requestKey)) return;
            teleportRequests.remove(requestKey);
            expirationTasks.remove(requestKey);

            Player requester = Bukkit.getPlayer(requesterUUID);
            Player target = Bukkit.getPlayer(targetUUID);

            if (requester != null) {
                Component targetName = target != null ? target.displayName()
                        : Component.text("that player").color(NamedTextColor.RED);
                requester.sendMessage(Component.text("Your teleport request to ").color(NamedTextColor.RED)
                        .append(targetName)
                        .append(Component.text(" has expired.").color(NamedTextColor.RED)));
            }

            if (target != null) {
                Component requesterName = requester != null ? requester.displayName()
                        : Component.text("a player").color(NamedTextColor.RED);
                target.sendMessage(Component.text("Teleport request from ").color(NamedTextColor.RED)
                        .append(requesterName)
                        .append(Component.text(" has expired.").color(NamedTextColor.RED)));
            }
        }, requestTimeoutTicks);

        expirationTasks.put(requestKey, task);
    }

    public void cancelExpirationTask(String requestKey) {
        BukkitTask task = expirationTasks.remove(requestKey);
        if (task != null) task.cancel();
    }

    public boolean hasCooldown(UUID playerUUID, long currentTime) {
        Long expiry = cooldowns.get(playerUUID);
        return expiry != null && currentTime < expiry;
    }

    public long getCooldownExpiry(UUID playerUUID) {
        return cooldowns.getOrDefault(playerUUID, 0L);
    }

    public void setCooldown(UUID playerUUID, long expiresAt) {
        cooldowns.put(playerUUID, expiresAt);
    }

    public long getRequestTimeoutTicks() {
        return requestTimeoutTicks;
    }

    public long getRequestCooldownMillis() {
        return requestCooldownMillis;
    }

    public boolean isAllowCrossWorld() {
        return allowCrossWorld;
    }

    // -------------------------------------------------------------------------
    // tpback — location saving
    // -------------------------------------------------------------------------

    public void savePreTPLocation(UUID playerUUID, Location location) {
        preTPLocations.put(playerUUID, location);
        preTPTimestamps.put(playerUUID, System.currentTimeMillis());
    }

    /**
     * Returns the saved pre-TP location, or null if it has expired or was never set.
     * Lazily removes expired entries.
     */
    public Location getPreTPLocation(UUID playerUUID) {
        if (!preTPLocations.containsKey(playerUUID)) return null;
        if (tpbackExpiryMillis > 0) {
            long savedAt = preTPTimestamps.getOrDefault(playerUUID, 0L);
            if (System.currentTimeMillis() - savedAt > tpbackExpiryMillis) {
                preTPLocations.remove(playerUUID);
                preTPTimestamps.remove(playerUUID);
                return null;
            }
        }
        return preTPLocations.get(playerUUID);
    }

    /** Removes the saved location — call this on successful tpback to enforce one-use. */
    public void consumePreTPLocation(UUID playerUUID) {
        preTPLocations.remove(playerUUID);
        preTPTimestamps.remove(playerUUID);
    }

    /** Removes the saved location without teleporting — call on death (if configured) or quit. */
    public void clearPreTPLocation(UUID playerUUID) {
        preTPLocations.remove(playerUUID);
        preTPTimestamps.remove(playerUUID);
    }

    // -------------------------------------------------------------------------
    // tpback — cooldown
    // -------------------------------------------------------------------------

    public boolean hasTpbackCooldown(UUID playerUUID, long now) {
        Long expiry = tpbackCooldowns.get(playerUUID);
        return expiry != null && now < expiry;
    }

    public long getTpbackCooldownExpiry(UUID playerUUID) {
        return tpbackCooldowns.getOrDefault(playerUUID, 0L);
    }

    public void setTpbackCooldown(UUID playerUUID, long expiresAt) {
        tpbackCooldowns.put(playerUUID, expiresAt);
    }

    // -------------------------------------------------------------------------
    // tpback — warmup task management
    // -------------------------------------------------------------------------

    public boolean hasPendingTpback(UUID playerUUID) {
        return pendingTpbacks.containsKey(playerUUID);
    }

    public void storePendingTpback(UUID playerUUID, BukkitTask task) {
        pendingTpbacks.put(playerUUID, task);
    }

    public void cancelPendingTpback(UUID playerUUID) {
        BukkitTask task = pendingTpbacks.remove(playerUUID);
        if (task != null) task.cancel();
    }

    // -------------------------------------------------------------------------
    // tpback — config getters
    // -------------------------------------------------------------------------

    public long getTpbackCooldownMillis() {
        return tpbackCooldownMillis;
    }

    public boolean isTpbackClearOnDeath() {
        return tpbackClearOnDeath;
    }

    public int getTpbackWarmupTicks() {
        return tpbackWarmupTicks;
    }
}
