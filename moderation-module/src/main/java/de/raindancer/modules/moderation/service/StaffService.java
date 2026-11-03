package de.raindancer.modules.moderation.service;

import java.util.Set;
import de.raindancer.modules.moderation.store.DutyOpStore;
import de.raindancer.modules.moderation.rules.DutyOpRule;
import de.raindancer.core.moderation.audit.Audit;
import de.raindancer.core.moderation.audit.AuditEntry;
import de.raindancer.core.platform.permission.Grants;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.chat.Chat;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.moderation.ModerationSettings;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.model.StaffRank;
import de.raindancer.modules.moderation.store.StaffRoster;
import de.raindancer.modules.moderation.util.StaffAlerts;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Promoting, demoting and toggling a single permission.
 *
 * <h2>Why the commands and the screens both come through here</h2>
 * Because a promotion is never only the roster entry. It is the entry, <em>and</em> the granted nodes,
 * <em>and</em> making them take effect on somebody who is standing there, <em>and</em> the audit line,
 * <em>and</em> telling them, <em>and</em> telling the staff. Written twice — once for {@code /promote}
 * and once for the menu — those two lists drift, and the moderation plugin this replaces is the proof:
 * five commands, and only one of them wrote an audit entry.
 *
 * <p>So {@code /promote} and the rank screen are two ways of asking for the same method.
 */
public final class StaffService implements IModerationService {

    private static final de.raindancer.core.platform.log.LogChannel log =
            de.raindancer.core.platform.log.Log.of("moderation");

    private final Plugin plugin;
    private final Server server;
    private final StaffRoster roster;
    private final Grants grants;
    private final Audit audit;
    private final Messages messages;
    private final Chat chat;

    /**
     * Makes a grant take effect now rather than at the next login.
     *
     * <p>Core's, through {@code RainsCore.reapplyGrants}. Behind a consumer so this class stays
     * testable without a server, and because a promoted player may not be online at all.
     */
    private final Consumer<Player> reapply;

    /**
     * Who this module opped, so it only ever de-ops its own.
     *
     * <p>Not persisted on purpose. After a restart nobody is in it, so the module will not
     * de-op anybody at all — which is the safe direction: an op that outlives a demotion is
     * visible in {@code ops.json}, whereas an owner silently de-opped by a plugin they
     * installed for moderation is a support ticket nobody can diagnose.
     */
    private final java.util.Set<UUID> oppedByUs = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile DutyOpStore dutyOps;
    /** This server's operators kept for duty, as last read or written — never read from the database on a world thread. */
    private final java.util.Set<UUID> keptForDuty = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final StaffAlerts alerts;
    private volatile ModerationSettings settings;

    public StaffService(Plugin plugin, Server server, StaffRoster roster, Grants grants, Audit audit,
                       Messages messages, Chat chat, Consumer<Player> reapply, StaffAlerts alerts,
                       ModerationSettings settings) {
        this.alerts = alerts;
        this.plugin = plugin;
        this.server = server;
        this.roster = roster;
        this.grants = grants;
        this.audit = audit;
        this.messages = messages;
        this.chat = chat;
        this.reapply = reapply == null ? player -> { } : reapply;
        settings(settings);
    }

    /**
     * A rung replaces their rung; a department is added on top of what they hold. Blocks on the
     * permission groups — never on the server thread.
     */
    public boolean promote(CommandSender by, UUID who, String name, StaffRank rank) {
        StaffRank before = roster.rankOf(who).orElse(null);
        if (!roster.promote(who, rank)) {
            return false;
        }
        applyNow(who);
        if (!rank.onLadder()) {
            record("add-department", by, who, name, rank.title(), null);
            messages().send(by, "moderation.rank.added", "player", name, "rank", rank.title(),
                    "colour", rank.colour());
            tellThem(who, "moderation.rank.you-also-are", "rank", rank.title(),
                    "colour", rank.colour(), "what", rank.describe());
            tellTheStaff("<white><player></white> is now also <" + rank.colour() + "><rank></"
                            + rank.colour() + "> <gray>(by <white><by></white>)",
                    Map.of("player", String.valueOf(name), "rank", rank.title(), "by", nameOf(by)));
            return true;
        }
        boolean up = before == null || rank.isAtLeast(before);
        record(up ? "promote" : "demote", by, who, name, rank.title(),
                before == null ? "not staff" : before.title());
        messages().send(by, "moderation.rank.changed", "player", name, "rank", rank.title(),
                "colour", rank.colour());
        tellThem(who, "moderation.rank.you-are-now", "rank", rank.title(),
                "colour", rank.colour(), "what", rank.describe());
        tellTheStaff("<white><player></white> is now <" + rank.colour() + "><rank></"
                        + rank.colour() + "> <gray>(by <white><by></white>)",
                Map.of("player", String.valueOf(name), "rank", rank.title(), "by", nameOf(by)));
        return true;
    }

    /** Takes one rank away — a department, or their rung (which leaves them without one). */
    public boolean takeAway(CommandSender by, UUID who, String name, StaffRank rank) {
        if (!roster.takeAway(who, rank)) {
            return false;
        }
        applyNow(who);
        record(rank.onLadder() ? "demote" : "remove-department", by, who, name,
                roster.rankOf(who).map(StaffRank::title).orElse("not staff"), rank.title());
        messages().send(by, "moderation.rank.taken", "player", name, "rank", rank.title());
        tellThem(who, "moderation.rank.you-no-longer-are", "rank", rank.title());
        tellTheStaff("<white><player></white> is no longer <rank> <gray>(by <white><by></white>)",
                Map.of("player", String.valueOf(name), "rank", rank.title(), "by", nameOf(by)));
        return true;
    }

    /** Off the staff entirely: every staff group. */
    public boolean demote(CommandSender by, UUID who, String name) {
        StaffRank before = roster.rankOf(who).orElse(null);
        if (!roster.demote(who)) {
            return false;
        }
        applyNow(who);
        record("demote", by, who, name, "not staff", before == null ? "not staff" : before.title());
        messages().send(by, "moderation.rank.removed", "player", name);
        tellThem(who, "moderation.rank.you-are-no-longer");
        tellTheStaff("<white><player></white> is no longer staff <gray>(by <white><by></white>)",
                Map.of("player", String.valueOf(name), "by", nameOf(by)));
        return true;
    }

    public void recordSelfTool(CommandSender by, UUID who, String name,
                               de.raindancer.modules.moderation.command.SelfToolCommand.Tool tool,
                               boolean nowOn) {
        recordSelfTool(by, who, name, tool.word(), nowOn);
    }

    /** The same, for a tool that is a button on the player page and no command, such as flight. */
    public void recordSelfTool(CommandSender by, UUID who, String name, String word, boolean nowOn) {
        audit.record(AuditEntry.of("moderation", (nowOn ? "" : "un") + word)
                .by(by instanceof Player player ? player.getUniqueId() : null, nameOf(by))
                .to(who, name)
                .saying(nowOn ? "on" : "off"));
    }

    /**
     * Writes down that somebody healed, fed, hurt or starved a player.
     *
     * <p>Audited for the same reason a tool is, and more so for the harmful two: half of somebody's
     * health disappearing mid-fight is the sort of thing that gets reported as a bug or as cheating,
     * and neither answer is available from memory afterwards.
     */
    public void recordVital(CommandSender by, UUID who, String name,
                            de.raindancer.modules.moderation.model.Vital vital) {
        audit.record(AuditEntry.of("moderation", vital.word())
                .by(by instanceof Player player ? player.getUniqueId() : null, nameOf(by))
                .to(who, name)
                .saying(vital.harmful() ? "harmful" : "restorative"));
    }

    /** The roster, for a screen that wants to draw it. */
    public StaffRoster roster() {
        return roster;
    }

    /** Whether they hold a node through Core's grants. */
    public boolean has(UUID who, String node) {
        return grants.has(who, node);
    }

    // ---------------------------------------------------------------------------- the doing

    /**
     * Makes the change real for somebody who is standing there.
     *
     * <p>Otherwise a moderator is told they are a moderator and every command refuses them until they
     * relog — which they report as the promotion being broken, and which is the same bug in reverse for
     * a demotion: somebody who keeps a power they were just told they no longer have.
     */
    private void applyNow(UUID who) {
        // The permissions themselves follow on their own: LuckPerms recalculates, and Core's grants
        // re-apply on the player's thread. What is left is the op policy, which is the server's.
        Scheduling.global(plugin, () -> applyOpPolicy(who));
    }

    /** Where the operators kept for duty are, once wired; null leaves op to the rank and the server. */
    public void dutyOps(DutyOpStore store) {
        this.dutyOps = store;
    }

    /** Reads this server's operators kept for duty; off the world's threads. */
    public boolean loadDutyOps() {
        DutyOpStore store = dutyOps;
        if (store == null || !store.load()) {
            return false;
        }
        keptForDuty.addAll(store.of(server()));
        return true;
    }

    /** The key this server's operators are kept under; a server without a network id is "local". */
    private static String server() {
        String id = de.raindancer.core.platform.network.ThisServer.id();
        return id == null || id.isBlank() ? "local" : id;
    }

    /**
     * Op is a power too: with staff powers needing admin mode an operator is op only in admin mode, admins opped
     * for their rank and operators opped by hand alike. Call on the global thread.
     */
    public void applyOpPolicy(UUID who) {
        org.bukkit.OfflinePlayer them = server.getOfflinePlayer(who);
        boolean needAdminMode = de.raindancer.core.platform.permission.Duty.powersNeedAdminMode();
        boolean kept = dutyOps != null && keptForDuty.contains(who);
        if (needAdminMode && them.isOp() && !kept && dutyOps != null) {
            // Opped since the switch went on (a /op typed later): kept for duty like the rest, never lost.
            adopt(List.of(who));
            kept = true;
        }
        boolean adminRank = roster.rankOf(who).filter(rank -> rank == StaffRank.ADMIN).isPresent();
        DutyOpRule.Decision decision = new DutyOpRule().decide(new DutyOpRule.Situation(adminRank,
                settings.adminsAreOp(), kept, needAdminMode,
                de.raindancer.core.moderation.players.AdminModes.isOn(who)));
        if (decision == DutyOpRule.Decision.LEAVE) {
            return;
        }
        if (decision == DutyOpRule.Decision.DEOP_IF_OURS && them.isOp() && !oppedByUs.contains(who)) {
            // Somebody the server owner opped by hand, or in ops.json. Not ours to take away: an admin
            // demotion silently de-opping the owner's co-owner is a worse surprise than an op that
            // outlives a rank. Said in the console, because the alternative is a demotion that looks
            // complete and is not.
            log.warn("{} is still a server operator, which this module did not grant and will not "
                    + "take away. Their rank no longer implies it — remove it with /deop if that was "
                    + "the intention.", them.getName() == null ? who.toString() : them.getName());
            return;
        }
        // The one decision: DutyOpRule says OP only for an ADMIN with adminsAreOp, or for an operator the
        // owner made by hand, kept for duty — and with powers needing admin mode, only while in it.
        boolean shouldBeOp = decision == DutyOpRule.Decision.OP;
        if (them.isOp() == shouldBeOp) {
            return;
        }
        them.setOp(shouldBeOp);
        if (shouldBeOp) {
            oppedByUs.add(who);
        } else {
            oppedByUs.remove(who);
        }
    }

    /**
     * The server's switch moved. On: every rung's powers go on duty in the groups, today's operators are kept for
     * duty (and may always switch admin mode on), and whoever is not in admin mode stops being op. Off: everybody
     * kept for duty is op again, as before. Blocks on the groups and the database — never on the server thread.
     */
    public void dutySwitched(boolean on) {
        DutyOpStore store = dutyOps;
        String here = server();
        if (on) {
            int moved = roster.putOnDuty();
            if (moved > 0) {
                log.info("Staff powers need admin mode now: {} rank permission(s) moved under rains:powers=on.", moved);
            }
            List<UUID> operators = server.getOperators().stream().map(org.bukkit.OfflinePlayer::getUniqueId).toList();
            adopt(operators);
            Scheduling.global(plugin, () -> {
                java.util.Set<UUID> everyone = new java.util.HashSet<>(keptForDuty);
                everyone.addAll(operators);
                everyone.forEach(this::applyOpPolicy);
            });
        } else if (store != null) {
            Set<UUID> kept = Set.copyOf(keptForDuty);
            if (kept.isEmpty()) {
                return;
            }
            // Opped back through the one decision (kept for duty, switch off), then the list goes.
            Scheduling.global(plugin, () -> {
                kept.forEach(this::applyOpPolicy);
                keptForDuty.removeAll(kept);
                Scheduling.async(plugin, () -> store.clear(here));
            });
            log.info("Staff powers no longer need admin mode: {} operator(s) kept for duty are op again.",
                    kept.size());
        }
    }

    /** Keeps operators for duty, and lets them switch admin mode on even without a staff rank. */
    private void adopt(List<UUID> operators) {
        DutyOpStore store = dutyOps;
        if (store == null || operators.isEmpty()) {
            return;
        }
        keptForDuty.addAll(operators);
        String here = server();
        Scheduling.async(plugin, () -> {
            if (!store.add(here, operators)) {
                log.warn("The operators kept for duty could not be written; they stay op until it can be.");
            }
            operators.forEach(who -> grants.grant(who, StaffRank.ADMIN_MODE));
        });
    }

    /** Both files, off the server thread. Neither is large and both matter after a crash. */
    private void tellThem(UUID who, String key, Object... values) {
        Player here = server.getPlayer(who);
        if (here != null) {
            messages.send(here, key, values);
        }
        // Not queued as a pending notice: a rank they cannot use yet is not news they need, and the
        // moment they log in the permissions are simply there.
    }

    private void tellTheStaff(String line, Map<String, String> arguments) {
        alerts.tell(new StaffAlerts.Alert(ModerationPermission.HISTORY.node(), null, line, arguments));
    }

    /**
     * Writes the line that answers "who made them an admin?".
     *
     * <p>Always, and regardless of the audit setting. Every other audit line in this module can be
     * switched off because it records something a moderator did to a player; these record somebody
     * handing out the power to do it, which is the one thing a server owner must be able to look up.
     */
    private void record(String action, CommandSender by, UUID who, String name, String to,
                        String from) {
        AuditEntry.Builder entry = AuditEntry.of("moderation", action)
                .by(by instanceof Player player ? player.getUniqueId() : null, nameOf(by))
                .to(who, name)
                .saying(to);
        if (from != null) {
            entry = entry.with("from", from);
        }
        audit.record(entry);
    }

    private Messages messages() {
        return messages;
    }

    private static String nameOf(CommandSender sender) {
        return sender == null ? "the console" : sender.getName();
    }

    @Override
    public void settings(ModerationSettings settings) {
        this.settings = settings == null ? ModerationSettings.DEFAULTS : settings;
    }

    @Override
    public String describe() {
        return "promoting and demoting staff — the staff groups, and the record of who did it";
    }
}
