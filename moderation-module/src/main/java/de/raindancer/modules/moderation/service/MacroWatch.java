package de.raindancer.modules.moderation.service;

import de.raindancer.modules.moderation.BotGuardSettings;
import de.raindancer.modules.moderation.ModerationSettings;
import de.raindancer.modules.moderation.model.AutoAction;
import de.raindancer.modules.moderation.model.ClickCadence;
import de.raindancer.modules.moderation.model.SuspectCase;
import de.raindancer.modules.moderation.rules.MacroRule;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Macros and autoclickers, from the rhythm of a player's clicks. Measured on the input itself, because Essentials
 * counts clicks as activity: a macro keeps its player "not AFK", so AFK status says nothing here. Tells staff with
 * the numbers, writes a case, files a report if wanted, and acts only if an owner set an action.
 */
public final class MacroWatch implements IModerationService {

    /** One finding per player in this long. */
    public static final long QUIET_MILLIS = 10 * 60_000L;
    /** Judged every this many counted clicks: sorting a few hundred gaps on every click is waste. */
    static final int JUDGE_EVERY = 5;
    static final int CAPACITY = 1_000;

    private static final class Entry {
        final ClickCadence cadence = new ClickCadence(CAPACITY);
        volatile String name;
        volatile long lastTold = Long.MIN_VALUE / 2;

        Entry(String name) {
            this.name = name;
        }
    }

    private final Supplier<BotGuardSettings> settings;
    private final LongSupplier clock;
    private final BotGuard.Staff staff;
    private final BotGuard.Cases cases;
    private final BotGuard.Hands hands;
    private final BotGuard.Reporter reports;
    private final MacroRule rule = new MacroRule();
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public MacroWatch(Supplier<BotGuardSettings> settings, LongSupplier clock, BotGuard.Staff staff, BotGuard.Cases cases,
                      BotGuard.Hands hands, BotGuard.Reporter reports) {
        this.settings = settings;
        this.clock = clock;
        this.staff = staff;
        this.cases = cases;
        this.hands = hands;
        this.reports = reports;
    }

    /** An arm swing; what mining, dropping or a right click explains is left out. */
    public void swing(UUID player, String name, float yaw, float pitch) {
        Entry entry = entry(player, name);
        if (entry != null && entry.cadence.swing(clock.getAsLong(), yaw, pitch)) {
            consider(player, entry, ClickCadence.Hand.LEFT);
        }
    }

    public void rightClick(UUID player, String name, float yaw, float pitch) {
        Entry entry = entry(player, name);
        if (entry != null) {
            entry.cadence.rightClick(clock.getAsLong(), yaw, pitch);
            consider(player, entry, ClickCadence.Hand.RIGHT);
        }
    }

    public void digging(UUID player, boolean on) {
        Entry entry = entry(player, null);
        if (entry != null) {
            entry.cadence.digging(clock.getAsLong(), on);
        }
    }

    public void dropped(UUID player) {
        Entry entry = entry(player, null);
        if (entry != null) {
            entry.cadence.dropped(clock.getAsLong());
        }
    }

    private Entry entry(UUID player, String name) {
        if (!settings.get().macros()) {
            return null;
        }
        Entry entry = entries.computeIfAbsent(player, id -> new Entry(name == null ? "?" : name));
        if (name != null) {
            entry.name = name;
        }
        return entry;
    }

    private void consider(UUID player, Entry entry, ClickCadence.Hand hand) {
        if (entry.cadence.counted(hand) % JUDGE_EVERY != 0) {
            return;
        }
        BotGuardSettings now = settings.get();
        long at = clock.getAsLong();
        if (at - entry.lastTold < QUIET_MILLIS) {
            return;
        }
        Optional<MacroRule.Verdict> verdict = rule.judge(entry.cadence.sample(hand, at, now.macroMinutes() * 60_000L),
                now.macroPolicy());
        if (verdict.isEmpty()) {
            return;
        }
        entry.lastTold = at;
        String evidence = evidence(verdict.get());
        String name = entry.name;
        staff.tell("anticheat.macro-suspect", "player", name, "evidence", evidence);
        cases.record(player, name, SuspectCase.Kind.MACRO, at, evidence);
        if (now.macroReports()) {
            reports.file(player, name, evidence);
        }
        AutoAction action = AutoAction.parse(now.macroAction());
        if (!action.isNone()) {
            hands.act(player, name, action, evidence);
            staff.tell("anticheat.suspect-acted", "player", name, "action", action.describe());
        }
    }

    /** What staff read: plain numbers. */
    static String evidence(MacroRule.Verdict verdict) {
        String hand = verdict.hand() == ClickCadence.Hand.LEFT ? "left" : "right";
        if (verdict.fast()) {
            return String.format(Locale.ROOT, "%.1f %s clicks a second for 3 s", verdict.cps(), hand);
        }
        return String.format(Locale.ROOT, "%s clicks every %d ms for %d min, varying %.1f %%, %d clicks, head still in %d %%",
                hand, verdict.medianMillis(), verdict.minutes(), verdict.variationPercent(), verdict.intervals() + 1,
                verdict.stillPercent());
    }

    /** Players measured right now. */
    public int watched() {
        return entries.size();
    }

    public void forget(UUID player) {
        entries.remove(player);
    }

    @Override
    public void settings(ModerationSettings fresh) {
        // Reads its own settings (BotGuardSettings) at every judgement, so a change takes at once.
    }

    @Override
    public String describe() {
        return "telling staff about click rhythms no hand makes";
    }
}
