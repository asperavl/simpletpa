package com.jellypudding.simpleTPA;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class RequestManager {

    private final SimpleTPA plugin;

    // key = "requesterUUID:targetUUID"
    private final HashMap<String, Long> teleportRequests = new HashMap<>();
    private final HashMap<String, BukkitTask> expirationTasks = new HashMap<>();
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    private long requestTimeoutTicks;
    private long requestCooldownMillis;
    private boolean allowCrossWorld;

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

        plugin.getLogger().info("Config loaded: timeout=" + timeoutSeconds + "s, cooldown=" + cooldownSeconds + "s, cross-world=" + allowCrossWorld);
    }

    public void shutdown() {
        expirationTasks.values().forEach(BukkitTask::cancel);
        teleportRequests.clear();
        expirationTasks.clear();
        cooldowns.clear();
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
}
