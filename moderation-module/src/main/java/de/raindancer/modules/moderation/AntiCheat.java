package de.raindancer.modules.moderation;

import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.data.sql.Database;
import de.raindancer.core.moderation.punishment.BlockedAttempts;
import de.raindancer.core.moderation.punishment.PunishmentKind;
import de.raindancer.core.moderation.reports.AutoReports;
import de.raindancer.core.platform.network.NetworkBus;
import de.raindancer.core.ui.screen.LeaveScreen;
import de.raindancer.modules.moderation.listener.BotGuardListener;
import de.raindancer.modules.moderation.listener.MacroListener;
import de.raindancer.modules.moderation.model.AutoAction;
import de.raindancer.modules.moderation.service.BotGuard;
import de.raindancer.modules.moderation.service.MacroWatch;
import de.raindancer.modules.moderation.store.SuspectCases;
import java.time.Instant;
import java.util.UUID;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.moderation.listener.ConnectionListener;
import de.raindancer.modules.moderation.listener.IModerationListener;
import de.raindancer.modules.moderation.rules.ActionRule;
import de.raindancer.modules.moderation.service.AlertService;
import de.raindancer.modules.moderation.service.AutoReportService;
import de.raindancer.modules.moderation.service.EspShield;
import de.raindancer.modules.moderation.util.GrimBridge;
import de.raindancer.modules.moderation.service.GrimFlags;
import de.raindancer.modules.moderation.service.IModerationService;
import de.raindancer.modules.moderation.service.PacketTap;
import de.raindancer.modules.moderation.service.PunishService;
import de.raindancer.modules.moderation.service.Tracks;
import de.raindancer.modules.moderation.service.ViolationService;
import de.raindancer.modules.moderation.store.AlertMutes;
import de.raindancer.modules.moderation.store.AntiCheatDatabase;
import de.raindancer.modules.moderation.store.EvidenceLog;
import de.raindancer.modules.moderation.store.KnownAddresses;
import de.raindancer.modules.moderation.service.AltWatch;
import de.raindancer.modules.moderation.store.ViolationLedger;
import de.raindancer.modules.moderation.util.CheatPermissions;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The moderation module's anti-cheat: Grim detects, this keeps the network's account of it — levels that follow
 * a player across servers, evidence, alerts to staff on every server, staff reports, kicks at the levels the owner
 * sets — plus the anti-ESP shield and the client policy, which Grim does not do.
 *
 * <p>Its settings, database and alert mutes are where the separate anti-cheat plugin kept them before 2.0
 * ({@code RainsAntiCheat}), so an owner's choices and the evidence so far carry over untouched.
 */
public final class AntiCheat {

    /** Where the separate anti-cheat plugin kept its files; its documents carry over from there. */
    static final String FORMER_PLUGIN = "RainsAntiCheat";

    private AntiCheat() {
    }

    /**
     * Bot accounts and macros: their settings, the network's case list, the lockdown and what touches players.
     * Cases are written off the server's threads and deleted after their keeping time.
     */
    private static BotGuardServices startBotGuard(ModuleContext context, Database database, NetworkBus bus,
                                                  AlertService alerts, byte[] addressKey) {
        var plugin = context.plugin();
        var server = plugin.getServer();
        var core = context.core();
        var messages = core.messages();
        SettingsStore<BotGuardSettings> settings = context.settings(BotGuardSettings.class, BotGuardSettings.DEFAULTS);
        SuspectCases cases = new SuspectCases(database, bus.server());
        BotGuard.Cases record = (player, name, kind, at, detail) ->
                Scheduling.async(plugin, () -> cases.record(player, name, kind, at, detail));
        BotGuard.Hands hands = new BotGuard.Hands() {
            @Override
            public void refuse(UUID id) {
                Player player = server.getPlayer(id);
                if (player != null) {
                    Scheduling.entity(plugin, player, () -> player.kick(LeaveScreen.framed(messages.get("anticheat.lockdown-screen"))));
                }
            }

            @Override
            public void act(UUID id, String name, AutoAction action, String why) {
                String reason = "Automatic: " + (why.length() > 120 ? why.substring(0, 120) : why);
                Player player = server.getPlayer(id);
                Runnable punish = () -> {
                    core.punishments().punish(id, action.kind(), null, reason, action.length());
                    if (action.kind() == PunishmentKind.BAN) {
                        core.banBridge().mirrorBan(id, reason, action.length() == null ? null : Instant.now().plus(action.length()));
                    }
                    if (player != null && player.isOnline()
                            && (action.kind() == PunishmentKind.KICK || action.kind() == PunishmentKind.BAN)) {
                        player.kick(LeaveScreen.framed(messages.get("anticheat.suspect-screen")));
                    }
                };
                if (player != null) {
                    Scheduling.entity(plugin, player, punish);
                } else {
                    Scheduling.global(plugin, punish);
                }
            }
        };
        BotGuard.Staff staff = alerts::staff;
        // Only a keyed hash of the network is ever held, and only for the wave window. Without the key, nothing.
        BotGuard guard = new BotGuard(settings::current, System::currentTimeMillis,
                network -> addressKey.length == 0 ? null : BlockedAttempts.hashAddress(addressKey, network),
                staff, record, hands, (id, name, text) -> AutoReports.file(id, name, "bot", text), bus);
        context.closeWith(guard::close);
        MacroWatch macros = new MacroWatch(settings::current, System::currentTimeMillis, staff, record, hands,
                (id, name, text) -> AutoReports.file(id, name, "macro", text));
        var ticking = Scheduling.asyncTimer(plugin, 1, 1, task -> guard.tick());
        context.closeWith(ticking::cancel);
        var retention = Scheduling.asyncTimer(plugin, 120, 6 * 60 * 60, task ->
                cases.sweep(System.currentTimeMillis() - settings.current().keepDays() * 86_400_000L));
        context.closeWith(retention::cancel);
        return new BotGuardServices(settings, guard, macros, cases);
    }

    /**
     * Starts it inside the moderation module, on the module's own settings (their anti-cheat part). Everything it
     * starts is stopped through {@code context}.
     */
    /** @return the anti-cheat's services, for what else in the module reaches into it (a cheating ban's fresh start) */
    public static AntiCheatServices start(ModuleContext context, SettingsStore<ModerationSettings> settings,
                                          ViolationService.MiningRecords mining) {
        Path folder = context.plugin().getDataFolder().toPath().getParent().resolve(FORMER_PLUGIN);
        LegacySettings.carryOver(context.core(), folder, settings, context.log());
        var messages = context.core().messages();
        int registered = CheatPermissions.register(context.plugin().getServer());
        if (registered > 0) {
            context.log().info("{} anti-cheat permission(s) registered.", registered);
        }

        Tracks tracks = new Tracks(System::currentTimeMillis);
        var bus = context.core().bus();
        var database = context.core().databases().shared(AntiCheatDatabase.NAME, AntiCheatDatabase.SCHEMA);
        AlertService alerts = new AlertService(context.plugin().getServer(), messages, context.log(), System::currentTimeMillis, bus);
        context.closeWith(alerts::close);
        AlertMutes mutes = new AlertMutes(folder);
        mutes.load();
        context.closeWith(mutes.connect());
        alerts.mutesFrom(mutes);
        EvidenceLog evidence = new EvidenceLog(database, bus, settings.current().antiCheat().evidencePerPlayer());
        evidence.importLegacy(new YamlStore(folder.resolve("evidence.yml")));
        evidence.load();
        context.closeWith(evidence.connect(bus));
        ViolationLedger ledger = new ViolationLedger(database, System::currentTimeMillis);
        PunishService punishments = new PunishService(context.plugin(), context.core(), messages, alerts);
        ViolationService violations = new ViolationService(new ActionRule(), alerts, punishments, evidence);
        violations.ledgerTo(ledger);
        violations.feedTo(context.core().moderationFeed());
        violations.miningTo(mining);
        // What this server lends a player that Grim cannot know about: a companion's back, flight, and its own lag.
        violations.excusesWith(player -> {
            var track = tracks.of(player);
            org.bukkit.entity.Entity vehicle = player.getVehicle();
            boolean riding = vehicle != null && context.core().companions().isCompanion(vehicle.getUniqueId());
            long now = System.currentTimeMillis();
            if (riding) {
                track.lastRodeCompanion = now;
            }
            boolean flying = context.core().flight() != null && context.core().flight().isGranted(player.getUniqueId());
            return new de.raindancer.modules.moderation.rules.ExcuseRule.Context(riding,
                    track.lastRodeCompanion == 0 ? Long.MAX_VALUE : now - track.lastRodeCompanion, flying,
                    org.bukkit.Bukkit.getTPS()[0], settings.current().antiCheat().minTps(), player.getPing());
        });
        KnownAddresses addresses = new KnownAddresses(database);
        AutoReportService autoReports = new AutoReportService(System::currentTimeMillis);
        violations.reportsTo(autoReports);
        PacketTap tap = new PacketTap(context.log());
        EspShield shield = new EspShield(context.plugin(), context.log(),
                id -> context.core().vanish() != null && context.core().vanish().isVanished(id));
        shield.tapWith(tap);
        tap.shieldWith(shield);

        List<IModerationService> all = List.of(tracks, alerts, punishments, violations, autoReports, tap, shield);
        all.forEach(service -> service.settings(settings.current()));
        settings.onChange(fresh -> all.forEach(service -> service.settings(fresh)));

        // The network's address key, the one blocked joins are hashed with, so a sighting here matches one anywhere.
        byte[] addressKey = context.core().secrets().bytes("address-hash", 32,
                folder.getParent().resolve("RainsCore").resolve("address-hash.key")).orElse(new byte[0]);
        BotGuardServices bots = startBotGuard(context, database, bus, alerts, addressKey);
        AntiCheatServices services = new AntiCheatServices(context.plugin(), context.plugin().getServer(), context.core(),
                context.log(), messages, context.chat(), () -> settings.current().antiCheat(), settings, tracks, violations, alerts, punishments,
                evidence, tap, shield, bots);
        AltWatch alts = new AltWatch(addresses, addressKey, context.core().punishments(), alerts, context.log());

        List<IModerationListener> listeners = new ArrayList<>();
        listeners.add(new ConnectionListener(services, listeners, alts));
        listeners.add(new BotGuardListener(context.plugin(), bots.guard(), bots::config, context.core()::isNewPlayer));
        listeners.add(new MacroListener(bots.macros(), tracks));
        listeners.forEach(context::listener);

        // Players already online when the module starts (a reload) get what joining would have given them.
        for (Player online : context.plugin().getServer().getOnlinePlayers()) {
            Scheduling.entity(context.plugin(), online, () -> {
                ConnectionListener.refreshPermissions(online, tracks.of(online));
                alerts.joined(online);
                tap.inject(online);
            });
        }

        var digests = Scheduling.asyncTimer(context.plugin(), 20, 20, task -> alerts.flushDigests());
        context.closeWith(digests::cancel);
        // What the network shares is written often, so staff on other servers see it soon.
        var sharing = Scheduling.asyncTimer(context.plugin(), 5, 5, task -> {
            mutes.flush();
            evidence.flush();
            ledger.flush();
        });
        context.closeWith(sharing::cancel);
        context.closeWith(evidence::flush);
        context.closeWith(ledger::flush);
        context.closeWith(mutes::flush);
        context.closeWith(() -> context.plugin().getServer().getOnlinePlayers().forEach(tap::eject));

        // What a player's page in moderation shows of them.
        de.raindancer.core.moderation.players.CheatRecords.Source records = who -> de.raindancer.modules.moderation
                .model.CheatSummary.of(tracks.find(who).orElse(null), evidence.since(who), System.currentTimeMillis());
        de.raindancer.core.moderation.players.CheatRecords.provide(records);
        context.closeWith(() -> de.raindancer.core.moderation.players.CheatRecords.withdraw(records));

        shield.start();
        context.closeWith(shield::stop);

        boolean grim = GrimBridge.available(context.plugin().getServer());
        if (grim) {
            GrimFlags flags = new GrimFlags(id -> Optional.ofNullable(context.plugin().getServer().getPlayer(id)),
                    (player, task) -> Scheduling.entity(context.plugin(), player, task),
                    (player, flag) -> {
                        var track = tracks.of(player);
                        ConnectionListener.refreshPermissions(player, track);
                        violations.flag(player, track, flag);
                    });
            flags.settings(settings.current());
            settings.onChange(flags::settings);
            context.closeWith(GrimBridge.listen(context.plugin(), flags)::run);
        } else {
            context.log().warn("GrimAC is not installed: no cheat checks run on this server. Install Grim on every "
                    + "server; its flags come here, to every server's staff and the player's network-wide record.");
        }
        AntiCheatCommands.ready(services);
        context.closeWith(AntiCheatCommands::stopped);
        context.log().info("Anti-cheat is up: {}, anti-ESP {}.", grim ? "Grim's flags are taken in" : "no Grim",
                settings.current().antiCheat().antiEsp() ? "on" : "off");
        return services;
    }
}
