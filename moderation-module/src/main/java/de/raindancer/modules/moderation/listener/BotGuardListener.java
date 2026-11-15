package de.raindancer.modules.moderation.listener;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.moderation.BotGuardSettings;
import de.raindancer.modules.moderation.service.BotGuard;
import de.raindancer.modules.moderation.util.CheatPermissions;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/** New accounts: whether they are new, where they stand when they join and when their first minutes are over, what they say. */
public final class BotGuardListener implements IModerationListener {

    private final Plugin plugin;
    private final BotGuard guard;
    private final Supplier<BotGuardSettings> settings;
    private final Function<Player, CompletableFuture<Boolean>> isNew;

    /** @param isNew whether a player is new to the whole network (Core's {@code isNewPlayer}) */
    public BotGuardListener(Plugin plugin, BotGuard guard, Supplier<BotGuardSettings> settings,
                            Function<Player, CompletableFuture<Boolean>> isNew) {
        this.plugin = plugin;
        this.guard = guard;
        this.settings = settings;
        this.isNew = isNew;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        BotGuardSettings now = settings.get();
        if (!now.bots()) {
            return;
        }
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        String name = player.getName();
        String address = player.getAddress() == null || player.getAddress().getAddress() == null ? null
                : player.getAddress().getAddress().getHostAddress();
        boolean exempt = player.hasPermission(CheatPermissions.BYPASS);
        Location start = player.getLocation();
        isNew.apply(player).thenAccept(newcomer -> guard.joined(id, name, address, Boolean.TRUE.equals(newcomer), exempt));
        Scheduling.entityLater(plugin, player, now.watchSeconds() * 20L, () -> {
            if (!player.isOnline() || !guard.watching(id)) {
                return;
            }
            Location here = player.getLocation();
            double moved = here.getWorld() == start.getWorld() ? here.distance(start) : Double.MAX_VALUE;
            guard.observed(id, moved, turned(start, here), player.getClientBrandName());
        });
    }

    /** Degrees of yaw (the short way round) and pitch together. */
    static double turned(Location from, Location to) {
        double yaw = Math.abs(((to.getYaw() - from.getYaw()) % 360 + 540) % 360 - 180);
        return yaw + Math.abs(to.getPitch() - from.getPitch());
    }

    /** Cancelled lines too: spam a chat filter stopped is still spam. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (guard.watching(id)) {
            guard.chatted(id, PlainTextComponentSerializer.plainText().serialize(event.originalMessage()));
        }
    }

    @Override
    public void forget(UUID player) {
        guard.forget(player);
    }

    @Override
    public String describe() {
        return "watching new accounts' first minutes";
    }
}
