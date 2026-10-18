package de.raindancer.modules.moderation.command;

import de.raindancer.core.moderation.audit.AuditEntry;
import de.raindancer.core.platform.permission.Duty;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.StaffRank;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/** {@code /dev} — dev mode on or off. In it a dev holds every permission (LuckPerms context rains:dev=on). */
public final class DevCommand implements IModerationCommand {

    private final Supplier<ModerationServices> services;

    public DevCommand(Supplier<ModerationServices> services) {
        this.services = services;
    }

    @Override
    public String describe() {
        return "switches dev mode, in which a developer holds every permission";
    }

    @Override
    public String permission() {
        return StaffRank.DEV_MODE;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(StaffRank.DEV_MODE);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        ModerationServices moderation = services.get();
        if (!(source.getSender() instanceof Player player)) {
            moderation.messages().send(source.getSender(), "moderation.only-a-player");
            return;
        }
        if (!moderation.roster().editable()) {
            moderation.messages().send(player, "moderation.dev.needs-luckperms");
            return;
        }
        boolean on = Duty.toggleDev(player.getUniqueId());
        moderation.audit().record(AuditEntry.of("moderation", on ? "dev mode on" : "dev mode off")
                .by(player.getUniqueId(), player.getName()));
        moderation.messages().send(player, on ? "moderation.dev.switched-on" : "moderation.dev.switched-off");
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return List.of();
    }
}
