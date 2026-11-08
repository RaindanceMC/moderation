package de.raindancer.modules.moderation.service;

import de.raindancer.modules.moderation.ModerationSettings;
import de.raindancer.modules.moderation.AntiCheatSettings;
import de.raindancer.modules.moderation.model.CheckType;
import de.raindancer.modules.moderation.model.Evidence;
import de.raindancer.modules.moderation.model.Flag;
import de.raindancer.modules.moderation.model.PlayerTrack;
import de.raindancer.modules.moderation.rules.ActionRule;
import de.raindancer.modules.moderation.store.EvidenceLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Where every flag goes: it raises the violation level, is written down, alerts staff, is weighed for a staff report
 * and, at the levels its family sets, kicks or bans. Setbacks and cancels are Grim's own.
 */
public final class ViolationService implements IModerationService {

    private final ActionRule rule;
    private final AlertService alerts;
    private final PunishService punishments;
    private final EvidenceLog evidence;
    private volatile AntiCheatSettings settings = AntiCheatSettings.DEFAULTS;

    private volatile AutoReportService reports;
    private volatile de.raindancer.core.moderation.feed.ModerationFeed feed;
    private volatile de.raindancer.modules.moderation.store.ViolationLedger ledger;
    private volatile MiningRecords mining;
    private final de.raindancer.modules.moderation.rules.ExcuseRule excuses = new de.raindancer.modules.moderation.rules.ExcuseRule();
    private volatile java.util.function.Function<Player, de.raindancer.modules.moderation.rules.ExcuseRule.Context> context =
            player -> de.raindancer.modules.moderation.rules.ExcuseRule.Context.NONE;

    /** Where the server's own doings come from — riding a companion, lent flight, lag — for {@link ExcuseRule}. */
    public void excusesWith(java.util.function.Function<Player, de.raindancer.modules.moderation.rules.ExcuseRule.Context> context) {
        this.context = context == null ? player -> de.raindancer.modules.moderation.rules.ExcuseRule.Context.NONE : context;
    }

    /** The x-ray side's per-player record, reset and measured afresh together with the anti-cheat's. */
    public interface MiningRecords {
        void clear(java.util.UUID player);

        void measureAfresh(java.util.UUID player, long fromMillis);
    }

    public ViolationService(ActionRule rule, AlertService alerts, PunishService punishments, EvidenceLog evidence) {
        this.rule = rule;
        this.alerts = alerts;
        this.punishments = punishments;
        this.evidence = evidence;
    }

    /** Whether a check is worth running for this player right now. */
    public boolean runs(PlayerTrack track, CheckType check) {
        AntiCheatSettings now = settings;
        return rule.runs(check, now) && !track.bypassAll && !track.bypassed.contains(check);
    }

    public ActionRule.Decision flag(Player player, PlayerTrack track, Flag flag) {
        CheckType check = flag.check();
        if (!runs(track, check)) {
            return ActionRule.Decision.NOTHING;
        }
        AntiCheatSettings now = settings;
        de.raindancer.modules.moderation.rules.ExcuseRule.Context why = context.apply(player);
        de.raindancer.modules.moderation.rules.ExcuseRule.Verdict verdict = excuses.judge(check, why);
        if (verdict == de.raindancer.modules.moderation.rules.ExcuseRule.Verdict.EXCUSED) {
            // Written down with its reason, for anybody reading the log later, but no level: what the server gave
            // them explains it.
            if (now.evidence()) {
                Location at = player.getLocation();
                evidence.add(player.getUniqueId(), player.getName(), new Evidence(System.currentTimeMillis(), check.key(),
                        track.violations().level(check), "excused (" + excuses.reason(why) + "): " + flag.detail(),
                        at.getWorld() == null ? "?" : at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ(),
                        track.ping, Bukkit.getTPS()[0]));
            }
            return ActionRule.Decision.NOTHING;
        }
        boolean softened = verdict == de.raindancer.modules.moderation.rules.ExcuseRule.Verdict.SOFTENED;
        double weight = softened ? flag.weight() * de.raindancer.modules.moderation.rules.ExcuseRule.LAG_WEIGHT : flag.weight();
        double level = track.violations().add(check, weight);
        gather(track.id(), check, Math.max(0, weight));
        ActionRule.Decision decided = rule.decide(check, level, now);
        // Lag never kicks or bans: the server or the line is to blame as likely as the player.
        ActionRule.Decision decision = softened ? new ActionRule.Decision(decided.alert(), false, false) : decided;
        if (now.evidence()) {
            Location at = player.getLocation();
            evidence.add(player.getUniqueId(), player.getName(), new Evidence(System.currentTimeMillis(), check.key(),
                    level, flag.detail(), at.getWorld() == null ? "?" : at.getWorld().getName(), at.getBlockX(),
                    at.getBlockY(), at.getBlockZ(), track.ping, Bukkit.getTPS()[0]));
        }
        alerts.verbose(player, check, level, flag.detail());
        if (decision.alert()) {
            alerts.alert(player, check, level, flag.detail(), track.ping);
            tell(feed, player, check, level, flag.detail(), track.ping);
        }
        if (decision.ban()) {
            punishments.atBanLevel(player, track, check, level);
            halve(track, check);
        } else if (decision.kick()) {
            punishments.kick(player, track, check, level);
            halve(track, check);
        }
        AutoReportService toReport = reports;
        var kind = de.raindancer.modules.moderation.model.ReportKind.of(check);
        if (toReport != null && kind.isPresent()) {
            Location at = player.getLocation();
            toReport.flagged(player.getUniqueId(), player.getName(), check, flag.weight(), flag.detail(), levelOf(track, kind.get()),
                    track.ping, Bukkit.getTPS()[0], (at.getWorld() == null ? "?" : at.getWorld().getName())
                            + " " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ());
        }
        return decision;
    }

    /** Where every alert is also told, for whoever passes them on (a Discord feed); none tells nobody. */
    public void feedTo(de.raindancer.core.moderation.feed.ModerationFeed feed) {
        this.feed = feed;
    }

    static void tell(de.raindancer.core.moderation.feed.ModerationFeed feed, Player player, CheckType check, double level,
                     String detail, int ping) {
        if (feed == null) {
            return;
        }
        String brand = player.getClientBrandName();
        String client = de.raindancer.core.platform.bukkit.ClientVersions.of(player.getUniqueId())
                .map(de.raindancer.core.platform.bukkit.ClientVersions.Version::name).orElse(null);
        feed.anticheat(new de.raindancer.core.moderation.feed.AnticheatAlert(player.getUniqueId(), player.getName(),
                check.title(), level, detail, ping, client, brand));
    }

    private void halve(PlayerTrack track, CheckType check) {
        double before = track.violations().level(check);
        track.violations().scale(check, 0.5);
        gather(track.id(), check, track.violations().level(check) - before);
    }

    /** For tests that hold no player: what a flag would have gathered for the network. */
    void gatherForTest(java.util.UUID player, CheckType check, double delta) {
        gather(player, check, delta);
    }

    private void gather(java.util.UUID player, CheckType check, double delta) {
        de.raindancer.modules.moderation.store.ViolationLedger network = ledger;
        if (network != null) {
            network.add(player, check, delta);
        }
    }

    /** What the checks of one kind hold against them together, after decay. */
    private static double levelOf(PlayerTrack track, de.raindancer.modules.moderation.model.ReportKind kind) {
        double sum = 0;
        for (java.util.Map.Entry<CheckType, Double> entry : track.violations().snapshot().entrySet()) {
            if (de.raindancer.modules.moderation.model.ReportKind.of(entry.getKey()).orElse(null) == kind) {
                sum += entry.getValue();
            }
        }
        return sum;
    }

    /** Where levels go to be shared with the other servers of the network; without one, they stay on this server. */
    public void miningTo(MiningRecords records) {
        this.mining = records;
    }

    public void ledgerTo(de.raindancer.modules.moderation.store.ViolationLedger network) {
        this.ledger = network;
    }

    /**
     * A player arrived: what the network already holds against them (from the servers they were on) becomes their level
     * here. Reads the database — off the server's threads.
     */
    public void arrived(PlayerTrack track) {
        de.raindancer.modules.moderation.store.ViolationLedger network = ledger;
        if (network != null) {
            network.levels(track.id()).ifPresent(levels -> levels.forEach((check, level) -> track.violations().raiseTo(check, level)));
        }
    }

    /** A player left: what this server gathered about them goes to the network now, for the server they hop to. Off the server's threads. */
    public void departed() {
        de.raindancer.modules.moderation.store.ViolationLedger network = ledger;
        if (network != null) {
            network.flush();
        }
    }

    public void forget(java.util.UUID player) {
        AutoReportService toReport = reports;
        if (toReport != null) {
            toReport.forget(player);
        }
    }

    /**
     * Forgets everything the anti-cheat holds against a player: levels, evidence and their mining ledger. The track
     * is null for a player who is offline, who has no levels to clear.
     */
    public void freshStart(java.util.UUID player, PlayerTrack track) {
        if (track != null) {
            track.violations().resetAll();
        }
        gatherClear(player);
        evidence.clear(player);
        MiningRecords toClear = mining;
        if (toClear != null) {
            toClear.clear(player);
        }
        if (punishments != null) {
            punishments.forgive(player);
        }
        forget(player);
    }

    /**
     * Measures a player afresh from {@code nowMillis}: the checks see somebody new — no levels, no buffers, no
     * statistics, nothing building towards a staff report — while their evidence stays as history
     * and every punishment, and the memory of which rule offence they are on, stays as it is.
     */
    public void measureAfresh(java.util.UUID player, PlayerTrack track, long nowMillis) {
        if (track != null) {
            track.measureAfresh();
        }
        gatherClear(player);
        evidence.measureFrom(player, nowMillis);
        MiningRecords toMeasure = mining;
        if (toMeasure != null) {
            toMeasure.measureAfresh(player, nowMillis);
        }
        forget(player);
    }

    private void gatherClear(java.util.UUID player) {
        de.raindancer.modules.moderation.store.ViolationLedger network = ledger;
        if (network != null) {
            network.clear(player);
        }
    }

    /** Where flags go to be weighed for a staff report; without one, none is filed. */
    public void reportsTo(AutoReportService service) {
        this.reports = service;
    }

    public AntiCheatSettings settings() {
        return settings;
    }

    @Override
    public void settings(ModerationSettings fresh) {
        settings(fresh.antiCheat());
    }

    public void settings(AntiCheatSettings fresh) {
        this.settings = fresh == null ? AntiCheatSettings.DEFAULTS : fresh;
        evidence.capacity(this.settings.evidencePerPlayer());
    }

    @Override
    public String describe() {
        return "turning Grim's flags into levels, evidence, alerts, reports, kicks and bans";
    }
}
