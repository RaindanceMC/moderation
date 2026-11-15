package de.raindancer.modules.moderation.listener;

import de.raindancer.modules.moderation.service.MacroWatch;
import de.raindancer.modules.moderation.service.Tracks;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/**
 * Every click, for the macro watch: arm swings as left clicks, uses with the main hand as right clicks, and what
 * explains a swing that is no click (mining, dropping). Cancelled events count too: the click happened.
 */
public final class MacroListener implements IModerationListener {

    private final MacroWatch watch;
    private final Tracks tracks;

    public MacroListener(MacroWatch watch, Tracks tracks) {
        this.watch = watch;
        this.tracks = tracks;
    }

    private boolean exempt(Player player) {
        return tracks.find(player.getUniqueId()).map(track -> track.bypassAll).orElse(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || exempt(event.getPlayer())) {
            return;
        }
        Player player = event.getPlayer();
        Location eyes = player.getLocation();
        watch.swing(player.getUniqueId(), player.getName(), eyes.getYaw(), eyes.getPitch());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        rightClick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUseEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND) {
            rightClick(event.getPlayer());
        }
    }

    private void rightClick(Player player) {
        if (exempt(player)) {
            return;
        }
        Location eyes = player.getLocation();
        watch.rightClick(player.getUniqueId(), player.getName(), eyes.getYaw(), eyes.getPitch());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDig(BlockDamageEvent event) {
        watch.digging(event.getPlayer().getUniqueId(), !event.getInstaBreak());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDigStopped(BlockDamageAbortEvent event) {
        watch.digging(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        watch.digging(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {
        watch.dropped(event.getPlayer().getUniqueId());
    }

    @Override
    public void forget(UUID player) {
        watch.forget(player);
    }

    @Override
    public String describe() {
        return "every click, for the macro watch";
    }
}
