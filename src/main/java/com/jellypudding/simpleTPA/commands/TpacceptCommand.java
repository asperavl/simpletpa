package com.jellypudding.simpleTPA.commands;

import com.jellypudding.simpleTPA.RequestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class TpacceptCommand implements CommandExecutor, TabCompleter {

    private final RequestManager requestManager;

    public TpacceptCommand(RequestManager requestManager) {
        this.requestManager = requestManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use teleportation commands.").color(NamedTextColor.RED));
            return true;
        }

        UUID targetUUID = player.getUniqueId();
        List<UUID> pendingRequesters = requestManager.getPendingRequesters(targetUUID);

        if (pendingRequesters.isEmpty()) {
            player.sendMessage(Component.text("You don't have any pending teleport requests.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /tpaccept <player>").color(NamedTextColor.RED));
            player.sendMessage(Component.text("Pending requests from:").color(NamedTextColor.YELLOW));
            for (UUID requesterUUID : pendingRequesters) {
                Player requester = Bukkit.getPlayer(requesterUUID);
                if (requester != null && requester.isOnline()) {
                    player.sendMessage(Component.empty()
                            .append(Component.text(" - ").color(NamedTextColor.GRAY))
                            .append(requester.displayName()));
                }
            }
            return true;
        }

        Player requester = Bukkit.getPlayer(args[0]);
        if (requester == null || !requester.isOnline()) {
            player.sendMessage(Component.text("Player not found or is offline.").color(NamedTextColor.RED));
            return true;
        }

        String requestKey = requester.getUniqueId() + ":" + targetUUID;
        if (!requestManager.hasRequest(requestKey)) {
            player.sendMessage(Component.text("You don't have a pending request from ").color(NamedTextColor.RED)
                    .append(requester.displayName())
                    .append(Component.text(".").color(NamedTextColor.RED)));
            return true;
        }

        if (!requestManager.isAllowCrossWorld() && !player.getWorld().equals(requester.getWorld())) {
            player.sendMessage(Component.text("You cannot accept a teleport request from a player in a different dimension.").color(NamedTextColor.RED));
            requestManager.removeRequest(requestKey);
            requestManager.cancelExpirationTask(requestKey);
            return true;
        }

        if (requester.isDead()) {
            player.sendMessage(Component.empty()
                    .append(Component.text("Cannot teleport ").color(NamedTextColor.RED))
                    .append(requester.displayName())
                    .append(Component.text(" - they are currently dead.").color(NamedTextColor.RED)));
            requester.sendMessage(player.displayName()
                    .append(Component.text(" tried to accept your teleport request but you were dead. Request cancelled.").color(NamedTextColor.RED)));
            requestManager.removeRequest(requestKey);
            requestManager.cancelExpirationTask(requestKey);
            return true;
        }

        requestManager.savePreTPLocation(requester.getUniqueId(), requester.getLocation());
        requester.teleport(player.getLocation());

        requester.sendMessage(Component.text("Teleported to ").color(NamedTextColor.GREEN)
                .append(player.displayName())
                .append(Component.text(".").color(NamedTextColor.GREEN)));
        player.sendMessage(requester.displayName()
                .append(Component.text(" has been teleported to you.").color(NamedTextColor.GREEN)));

        requestManager.removeRequest(requestKey);
        requestManager.cancelExpirationTask(requestKey);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return Collections.emptyList();
        String partial = args[0].toLowerCase();
        return requestManager.getPendingRequesters(player.getUniqueId()).stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
    }
}
