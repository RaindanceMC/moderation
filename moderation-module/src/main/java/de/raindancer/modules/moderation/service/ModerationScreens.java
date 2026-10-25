package de.raindancer.modules.moderation.service;

import de.raindancer.core.moderation.punishment.Punishment;
import de.raindancer.core.moderation.punishment.Punishments;
import de.raindancer.core.moderation.vanish.Vanish;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.hologram.ScreenKind;
import de.raindancer.core.ui.hologram.Screens;
import de.raindancer.modules.moderation.ModerationSettings;
import de.raindancer.modules.moderation.model.StaffRank;
import de.raindancer.modules.moderation.rules.ScreenLineRule;
import de.raindancer.modules.moderation.rules.StandingRule;
import de.raindancer.modules.moderation.store.StaffRoster;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Moderation's screens for Core's {@code /screen}: the staff who are on (never a vanished one), and everybody's own
 * standing. Nobody's record but the viewer's own is ever drawn.
 */
public final class ModerationScreens implements IModerationService {

    public static final List<String> KINDS = List.of("moderation-staff", "moderation-you");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Server server;
    private final Vanish vanish;
    private final StaffRoster roster;
    private final Punishments punishments;
    private final Supplier<StandingRule> standing;
    private final FineService fines;
    private final ScreenLineRule rule = new ScreenLineRule();

    public ModerationScreens(Server server, Vanish vanish, StaffRoster roster, Punishments punishments,
                             Supplier<StandingRule> standing, FineService fines) {
        this.server = server;
        this.vanish = vanish;
        this.roster = roster;
        this.punishments = punishments;
        this.standing = standing;
        this.fines = fines;
    }

    @Override
    public void settings(ModerationSettings settings) {
        // The screens read live state and no setting.
    }

    public void register(Screens screens, Plugin owner) {
        screens.register(owner, ScreenKind.everyone("moderation-staff", "Staff online",
                "The staff who are on right now, highest rank first. Vanished staff are never shown.",
                Material.IRON_HELMET, this::staff));
        screens.register(owner, ScreenKind.personal("moderation-you", "Your standing",
                "Everybody's own standing: recent warnings, restrictions and fines owed. Only ever their own.",
                Material.SHIELD, this::you));
    }

    private Component staff() {
        List<ScreenLineRule.Seen> online = server.getOnlinePlayers().stream()
                .filter(player -> !vanish.isVanished(player.getUniqueId()))
                .map(this::seen).flatMap(java.util.Optional::stream).toList();
        return MINI.deserialize(rule.staff(online));
    }

    private java.util.Optional<ScreenLineRule.Seen> seen(Player player) {
        return roster.rankOf(player.getUniqueId()).map(rank ->
                new ScreenLineRule.Seen(player.getName(), rank.title(), rank.colour(), rank.weight()));
    }

    private Component you(ScreenKind.Viewer viewer) {
        Instant now = Instant.now();
        StandingRule judge = standing.get();
        List<Punishment> record = punishments.history(viewer.id());
        List<String> restrictions = record.stream().filter(one -> one.isActiveAt(now))
                .map(one -> rule.restriction(one.kind().past(), one.remainingAt(now).orElse(null))).toList();
        Money owed = fines.owed(viewer.id());
        return MINI.deserialize(rule.you(judge.of(record, now), judge.recentWarnings(record, now), restrictions,
                owed.isPositive() ? Fees.format(owed) : null));
    }
}
