package com.jellypudding.simpleTPA.listeners;

import com.jellypudding.simpleTPA.RequestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerStateListener implements Listener {

    private final RequestManager requestManager;

    public PlayerStateListener(RequestManager requestManager) {
        this.requestManager = requestManager;
    }

    /**
     * Cancels a pending /tpback warmup if the player takes any damage.
     * Uses EntityDamageEvent (not EntityDamageByEntityEvent) to catch ALL sources:
     * PvP, mobs, fall damage, fire, explosions, etc.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (requestManager.hasPendingTpback(uuid)) {
            requestManager.cancelPendingTpback(uuid);
            player.sendMessage(Component.text("Teleport cancelled! You took damage.").color(NamedTextColor.RED));
        }
    }

    /**
     * On death: always cancel any pending warmup, and optionally wipe the saved location.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        // Always cancel a warmup if the player dies mid-countdown
        requestManager.cancelPendingTpback(uuid);
        if (requestManager.isTpbackClearOnDeath()) {
            requestManager.clearPreTPLocation(uuid);
        }
    }

    /**
     * On quit: cancel warmup and wipe saved location to prevent orphaned tasks
     * or location data for offline players.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        requestManager.cancelPendingTpback(uuid);
        requestManager.clearPreTPLocation(uuid);
    }
}
