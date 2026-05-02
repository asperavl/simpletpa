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
import java.util.UUID;
import java.util.stream.Collectors;

public class TpaCommand implements CommandExecutor, TabCompleter {

    private final RequestManager requestManager;

    public TpaCommand(RequestManager requestManager) {
        this.requestManager = requestManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use teleportation commands.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /tpa <player>").color(NamedTextColor.RED));
            return true;
        }

        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (requestManager.hasCooldown(playerUUID, currentTime)) {
            long remaining = (requestManager.getCooldownExpiry(playerUUID) - currentTime) / 1000 + 1;
            player.sendMessage(Component.text("Please wait " + remaining + " seconds before sending another request.").color(NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("Player not found or is offline.").color(NamedTextColor.RED));
            return true;
        }

        if (target.getUniqueId().equals(playerUUID)) {
            player.sendMessage(Component.text("You cannot teleport to yourself.").color(NamedTextColor.RED));
            return true;
        }

        if (!requestManager.isAllowCrossWorld() && !player.getWorld().equals(target.getWorld())) {
            player.sendMessage(Component.text("You cannot teleport to a player in a different dimension.").color(NamedTextColor.RED));
            return true;
        }

        String requestKey = playerUUID + ":" + target.getUniqueId();
        if (requestManager.hasRequest(requestKey)) {
            player.sendMessage(Component.text("You already have a pending request to this player.").color(NamedTextColor.RED));
            return true;
        }

        requestManager.addRequest(requestKey, currentTime);
        requestManager.setCooldown(playerUUID, currentTime + requestManager.getRequestCooldownMillis());
        requestManager.scheduleExpiration(requestKey, playerUUID, target.getUniqueId());

        String timeoutDisplay = formatTimeout(requestManager.getRequestTimeoutTicks());

        player.sendMessage(Component.empty()
                .append(Component.text("Teleport request sent to ").color(NamedTextColor.GREEN))
                .append(target.displayName())
                .append(Component.text(".").color(NamedTextColor.GREEN)));
        player.sendMessage(Component.text("This request will expire in " + timeoutDisplay + ".").color(NamedTextColor.YELLOW));

        target.sendMessage(player.displayName()
                .append(Component.text(" has requested to teleport to you.").color(NamedTextColor.GREEN)));
        target.sendMessage(Component.empty()
                .append(Component.text("Type /tpaccept ").color(NamedTextColor.YELLOW))
                .append(player.displayName())
                .append(Component.text(" to accept. This request will expire in " + timeoutDisplay + ".").color(NamedTextColor.YELLOW)));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || args.length != 1) return Collections.emptyList();
        String partial = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .filter(name -> !name.equals(sender.getName()))
                .collect(Collectors.toList());
    }

    private String formatTimeout(long ticks) {
        int totalSeconds = (int) (ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String display = minutes > 0 ? minutes + " minute" + (minutes > 1 ? "s" : "") : "";
        if (seconds > 0) {
            if (!display.isEmpty()) display += " and ";
            display += seconds + " second" + (seconds > 1 ? "s" : "");
        }
        return display;
    }
}
