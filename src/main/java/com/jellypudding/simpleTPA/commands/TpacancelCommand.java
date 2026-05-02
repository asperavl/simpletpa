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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TpacancelCommand implements CommandExecutor, TabCompleter {

    private final RequestManager requestManager;

    public TpacancelCommand(RequestManager requestManager) {
        this.requestManager = requestManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use teleportation commands.").color(NamedTextColor.RED));
            return true;
        }

        UUID playerUUID = player.getUniqueId();
        List<String> playerRequests = requestManager.getRequestKeys().stream()
                .filter(key -> key.startsWith(playerUUID + ":"))
                .collect(Collectors.toList());

        if (playerRequests.isEmpty()) {
            player.sendMessage(Component.text("You don't have any pending teleport requests.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            if (playerRequests.size() == 1) {
                cancelRequest(player, playerRequests.get(0));
            } else {
                player.sendMessage(Component.text("Usage: /tpacancel <player> or /tpacancel all").color(NamedTextColor.RED));
                player.sendMessage(Component.text("You can cancel the following pending requests:").color(NamedTextColor.YELLOW));
                for (String key : playerRequests) {
                    String[] parts = key.split(":");
                    Player target = Bukkit.getPlayer(UUID.fromString(parts[1]));
                    if (target != null && target.isOnline()) {
                        player.sendMessage(Component.empty()
                                .append(Component.text(" - ").color(NamedTextColor.GRAY))
                                .append(target.displayName()));
                    }
                }
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            for (String key : playerRequests) {
                notifyTargetOfCancel(player, key);
                requestManager.removeRequest(key);
                requestManager.cancelExpirationTask(key);
            }
            player.sendMessage(Component.text("You have cancelled all your teleport requests.").color(NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("Player not found or is offline.").color(NamedTextColor.RED));
            return true;
        }

        String requestKey = playerUUID + ":" + target.getUniqueId();
        if (!requestManager.hasRequest(requestKey)) {
            player.sendMessage(Component.text("You don't have a pending request to ").color(NamedTextColor.RED)
                    .append(target.displayName())
                    .append(Component.text(".").color(NamedTextColor.RED)));
            return true;
        }

        requestManager.removeRequest(requestKey);
        requestManager.cancelExpirationTask(requestKey);

        player.sendMessage(Component.empty()
                .append(Component.text("You have cancelled your teleport request to ").color(NamedTextColor.YELLOW))
                .append(target.displayName())
                .append(Component.text(".").color(NamedTextColor.YELLOW)));
        target.sendMessage(player.displayName()
                .append(Component.text(" has cancelled their teleport request.").color(NamedTextColor.YELLOW)));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return Collections.emptyList();
        String partial = args[0].toLowerCase();
        UUID playerUUID = player.getUniqueId();

        List<String> suggestions = new ArrayList<>();
        if ("all".startsWith(partial)) suggestions.add("all");

        requestManager.getRequestKeys().stream()
                .filter(key -> key.startsWith(playerUUID + ":"))
                .forEach(key -> {
                    String[] parts = key.split(":");
                    if (parts.length == 2) {
                        try {
                            Player target = Bukkit.getPlayer(UUID.fromString(parts[1]));
                            if (target != null && target.isOnline()) {
                                String name = target.getName();
                                if (name.toLowerCase().startsWith(partial)) suggestions.add(name);
                            }
                        } catch (IllegalArgumentException ignored) {}
                    }
                });

        return suggestions;
    }

    private void cancelRequest(Player player, String requestKey) {
        String[] parts = requestKey.split(":");
        Player target = Bukkit.getPlayer(UUID.fromString(parts[1]));
        player.sendMessage(Component.text("You have cancelled your teleport request.").color(NamedTextColor.YELLOW));
        if (target != null && target.isOnline()) {
            target.sendMessage(player.displayName()
                    .append(Component.text(" has cancelled their teleport request.").color(NamedTextColor.YELLOW)));
        }
        requestManager.removeRequest(requestKey);
        requestManager.cancelExpirationTask(requestKey);
    }

    private void notifyTargetOfCancel(Player player, String requestKey) {
        String[] parts = requestKey.split(":");
        if (parts.length == 2) {
            try {
                Player target = Bukkit.getPlayer(UUID.fromString(parts[1]));
                if (target != null && target.isOnline()) {
                    target.sendMessage(player.displayName()
                            .append(Component.text(" has cancelled their teleport request.").color(NamedTextColor.YELLOW)));
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
