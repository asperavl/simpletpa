package com.jellypudding.simpleTPA.commands;

import com.jellypudding.simpleTPA.RequestManager;
import com.jellypudding.simpleTPA.SimpleTPA;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TpbackCommand implements CommandExecutor, TabCompleter {

    private final SimpleTPA plugin;
    private final RequestManager requestManager;

    public TpbackCommand(SimpleTPA plugin, RequestManager requestManager) {
        this.plugin = plugin;
        this.requestManager = requestManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use teleportation commands.").color(NamedTextColor.RED));
            return true;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Block duplicate warmup requests
        if (requestManager.hasPendingTpback(uuid)) {
            player.sendMessage(Component.text("You already have a teleport pending. Stand still and don't take damage!").color(NamedTextColor.RED));
            return true;
        }

        // Cooldown check (only applies after a successful /tpback, not after a cancelled warmup)
        if (requestManager.hasTpbackCooldown(uuid, now)) {
            long remaining = (requestManager.getTpbackCooldownExpiry(uuid) - now) / 1000 + 1;
            player.sendMessage(Component.text("Please wait " + remaining + " second" + (remaining == 1 ? "" : "s") + " before using /tpback again.").color(NamedTextColor.RED));
            return true;
        }

        // Check saved location
        Location savedLocation = requestManager.getPreTPLocation(uuid);
        if (savedLocation == null) {
            player.sendMessage(Component.text("You have no saved location to return to.").color(NamedTextColor.RED));
            return true;
        }

        // Check world is still loaded
        if (savedLocation.getWorld() == null) {
            player.sendMessage(Component.text("That world is no longer available.").color(NamedTextColor.RED));
            requestManager.consumePreTPLocation(uuid);
            return true;
        }

        int warmupTicks = requestManager.getTpbackWarmupTicks();

        if (warmupTicks <= 0) {
            // Instant teleport — no warmup
            performTeleport(player, uuid, savedLocation, now);
        } else {
            // Warmup path — schedule teleport and warn the player
            int warmupSeconds = warmupTicks / 20;
            player.sendMessage(Component.text("Teleporting in " + warmupSeconds + " second" + (warmupSeconds == 1 ? "" : "s") + ". Take damage to cancel.").color(NamedTextColor.YELLOW));

            BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Remove from pending map before doing anything else
                requestManager.cancelPendingTpback(uuid);

                // Re-fetch location in case it expired during the warmup window
                Location dest = requestManager.getPreTPLocation(uuid);
                if (dest == null || dest.getWorld() == null) {
                    player.sendMessage(Component.text("Teleport cancelled — your saved location is no longer available.").color(NamedTextColor.RED));
                    return;
                }

                performTeleport(player, uuid, dest, System.currentTimeMillis());
            }, warmupTicks);

            requestManager.storePendingTpback(uuid, task);
        }

        return true;
    }

    /** Shared logic for instant and warmup paths: consume location, set cooldown, teleport. */
    private void performTeleport(Player player, UUID uuid, Location destination, long now) {
        requestManager.consumePreTPLocation(uuid);
        requestManager.setTpbackCooldown(uuid, now + requestManager.getTpbackCooldownMillis());
        player.teleport(destination);
        player.sendMessage(Component.text("Teleported back to your previous location.").color(NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
