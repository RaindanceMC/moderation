package de.raindancer.modules.moderation.listener;

import de.raindancer.modules.moderation.service.CheatAttemptService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.UUID;

/**
 * A command only a cheater reaches for is punished whoever types it — staff too. Only the server's owners are left
 * alone: the owner rank, an operator, or an operator kept for duty (who is not op while off duty).
 */
public final class CheatAttemptListener implements IModerationListener {

    private final CheatAttemptService attempts;
    private final java.util.function.Predicate<UUID> owner;

    public CheatAttemptListener(CheatAttemptService attempts, java.util.function.Predicate<UUID> owner) {
        this.attempts = attempts;
        this.owner = owner;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || owner.test(player.getUniqueId())) {
            return;
        }
        attempts.check(player.getUniqueId(), player.getName(), event.getMessage());
    }

    @Override
    public void forget(UUID player) {
        // Tries are counted in their file, not here.
    }
}
