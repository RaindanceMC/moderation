package de.raindancer.modules.moderation.command;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.StaffRank;
import de.raindancer.modules.moderation.util.Players;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@code /demote <player>} — takes somebody down a rung, or off the staff entirely.
 *
 * <h2>Why one step rather than straight off</h2>
 * Because "demote" usually means "not this rank any more", not "not staff any more" — an admin who
 * misused the config is often still a perfectly good moderator. So the default is one rung down, and
 * {@code /demote <player> none} is the way to say the harder thing out loud.
 *
 * <p>Ops only, for the same reason as {@code /promote}: a power that hands out powers cannot be one of
 * the powers it hands out.
 */
public final class DemoteCommand implements IModerationCommand {

    /** The word that means "off the staff", rather than one rung down. */
    private static final String OFF_ENTIRELY = "none";

    private final Supplier<ModerationServices> services;

    public DemoteCommand(Supplier<ModerationServices> services) {
        this.services = services;
    }

    @Override
    public String describe() {
        return "takes somebody down a rung, one rank away, or off the staff";
    }

    @Override
    public String permission() {
        return PromoteCommand.USE;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.isOp() || sender.hasPermission(PromoteCommand.USE)
                || sender.hasPermission(
                de.raindancer.modules.moderation.model.ModerationPermission.APPOINT.node());
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        ModerationServices moderation = services.get();
        if (args.length == 0) {
            moderation.messages().send(sender, "moderation.usage",
                    "usage", "/demote <player> [" + OFF_ENTIRELY + "|<rank>]");
            return;
        }
        Optional<OfflinePlayer> found = Players.one(moderation.messages(), moderation.server(), sender, args[0]);
        if (found.isEmpty()) {
            return;
        }
        OfflinePlayer them = found.get();
        UUID who = them.getUniqueId();
        String name = Players.nameOf(them);
        UUID actor = sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null;
        Set<StaffRank> held = moderation.roster().ranksOf(who);
        if (held.isEmpty()) {
            moderation.messages().send(sender, "moderation.rank.not-staff", "player", name);
            return;
        }
        boolean offEntirely = args.length > 1 && OFF_ENTIRELY.startsWith(args[1].toLowerCase(Locale.ROOT));
        Optional<StaffRank> named = args.length > 1 && !offEntirely ? StaffRank.byName(args[1]) : Optional.empty();
        if (args.length > 1 && !offEntirely && named.isEmpty()) {
            moderation.messages().send(sender, "moderation.rank.unknown", "word", args[1],
                    "ranks", String.join(", ", StaffRank.names()));
            return;
        }
        if (named.isPresent() && !held.contains(named.get())) {
            moderation.messages().send(sender, "moderation.rank.not-that-rank", "player", name,
                    "rank", named.get().title());
            return;
        }
        var allowed = named.isPresent()
                ? moderation.promotionRule().mayTakeAway(actor, who, named.get())
                : moderation.promotionRule().mayDemote(actor, who);
        if (allowed.isRefused()) {
            allowed.refusal().ifPresent(key -> moderation.messages().send(sender, key,
                    "rank", allowed.detail() == null ? "" : allowed.detail(), "player", name));
            return;
        }
        Optional<StaffRank> rung = moderation.roster().rankOf(who);
        Scheduling.async(moderation.plugin(), () -> {
            if (named.isPresent()) {
                moderation.staff().takeAway(sender, who, name, named.get());
            } else if (offEntirely || rung.flatMap(StaffRank::below).isEmpty()) {
                moderation.staff().demote(sender, who, name);
            } else {
                moderation.staff().promote(sender, who, name, rung.get().below().get());
            }
        });
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            return Players.suggest(services.get().server(), source.getSender(), args.length == 1 ? args[0] : "");
        }
        if (args.length == 2) {
            List<String> options = new ArrayList<>();
            options.add(OFF_ENTIRELY);
            options.addAll(StaffRank.names());
            options.removeIf(option -> !option.startsWith(args[1].toLowerCase(Locale.ROOT)));
            return options;
        }
        return List.of();
    }
}
