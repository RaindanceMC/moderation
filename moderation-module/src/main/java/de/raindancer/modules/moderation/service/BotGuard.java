package de.raindancer.modules.moderation.service;

import de.raindancer.core.platform.network.NetworkBus;
import de.raindancer.core.platform.network.NetworkEvent;
import de.raindancer.modules.moderation.BotGuardSettings;
import de.raindancer.modules.moderation.ModerationSettings;
import de.raindancer.modules.moderation.model.AutoAction;
import de.raindancer.modules.moderation.model.BotSignal;
import de.raindancer.modules.moderation.model.SuspectCase;
import de.raindancer.modules.moderation.rules.JoinWaveRule;
import de.raindancer.modules.moderation.rules.NewcomerRule;
import de.raindancer.modules.moderation.util.AsyncPublisher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Bot accounts: join waves of new accounts, accounts from one network or with similar names, and what each new
 * account does in its first minutes. Tells staff, keeps a case, and on a bot wave locks new accounts out of the
 * whole network for a while. Punishes only if an owner set an action.
 *
 * <p>Network addresses are kept only as the caller's keyed hash of the /24 (or /48), only in memory, and only for
 * the wave window. Called from any thread.
 */
public final class BotGuard implements IModerationService {

    public static final String TOPIC = "bot-lockdown";
    /** Signals before staff hear of an account. One alone is too common: newcomers reading the rules stand still. */
    public static final int ALERT_SIGNALS = 2;

    /** Telling staff, network-wide: a message key and its placeholders. */
    @FunctionalInterface
    public interface Staff {
        void tell(String key, Object... values);
    }

    /** Writing a case down. */
    @FunctionalInterface
    public interface Cases {
        void record(UUID player, String name, SuspectCase.Kind kind, long at, String detail);
    }

    /** Filing a report for staff; the queue puts the kind ("bot: ", "macro: ") in front. */
    @FunctionalInterface
    public interface Reporter {
        Reporter NONE = (player, name, text) -> { };

        void file(UUID player, String name, String text);
    }

    /** What touches players: called from any thread, so implementations hop to the player's own. */
    public interface Hands {
        /** A new account during a lockdown. */
        void refuse(UUID player);

        void act(UUID player, String name, AutoAction action, String why);
    }

    /** A new account under watch. Guarded by itself. */
    private static final class Watch {
        final String name;
        final Set<BotSignal> signals = EnumSet.noneOf(BotSignal.class);
        final Map<String, Integer> said = new HashMap<>();
        int lines;
        int mostRepeated;

        Watch(String name) {
            this.name = name;
        }
    }

    private final Supplier<BotGuardSettings> settings;
    private final LongSupplier clock;
    private final UnaryOperator<String> hash;
    private final Staff staff;
    private final Cases cases;
    private final Hands hands;
    private final Reporter reports;
    private final NetworkBus bus;
    private final AsyncPublisher publisher;
    private final NetworkBus.Subscription heard;
    private final JoinWaveRule waves = new JoinWaveRule();
    private final NewcomerRule newcomers = new NewcomerRule();
    private final Map<UUID, Watch> watched = new ConcurrentHashMap<>();
    /** Guarded by this. */
    private final Deque<JoinWaveRule.Join> recent = new ArrayDeque<>();
    private long lastWaveLine = Long.MIN_VALUE / 2;
    private volatile long lockedUntil;
    /** Whether this server started the current lockdown, and so announces its end. Guarded by this. */
    private boolean ownLockdown;
    private int refused;

    /** @param hash turns a network ("203.0.113.0/24") into what is kept of it: a keyed hash */
    public BotGuard(Supplier<BotGuardSettings> settings, LongSupplier clock, UnaryOperator<String> hash, Staff staff,
                    Cases cases, Hands hands, Reporter reports, NetworkBus bus) {
        this(settings, clock, hash, staff, cases, hands, reports, bus, null);
    }

    /** @param publishing runs the publishing; null for a thread of its own, a direct executor for tests */
    public BotGuard(Supplier<BotGuardSettings> settings, LongSupplier clock, UnaryOperator<String> hash, Staff staff,
                    Cases cases, Hands hands, Reporter reports, NetworkBus bus, Executor publishing) {
        this.settings = settings;
        this.clock = clock;
        this.hash = hash;
        this.staff = staff;
        this.cases = cases;
        this.hands = hands;
        this.reports = reports == null ? Reporter.NONE : reports;
        this.bus = bus == null ? NetworkBus.NONE : bus;
        this.publisher = publishing == null ? new AsyncPublisher(this.bus, "bot-guard") : new AsyncPublisher(this.bus, publishing);
        this.heard = this.bus.subscribe(TOPIC, this::heard);
    }

    public void close() {
        heard.close();
        publisher.close();
    }

    /**
     * Somebody joined; whether they are new to the network is known by now.
     *
     * @param exempt holds the anti-cheat's bypass: never refused, never watched
     */
    public void joined(UUID player, String name, String address, boolean newcomer, boolean exempt) {
        BotGuardSettings now = settings.get();
        if (!now.bots() || !newcomer || exempt) {
            return;
        }
        long at = clock.getAsLong();
        String network = JoinWaveRule.network(address);
        JoinWaveRule.Join join = new JoinWaveRule.Join(player, name, network == null ? null : hash.apply(network), at);
        JoinWaveRule.Wave wave;
        boolean waveLine = false;
        boolean lockdown = false;
        synchronized (this) {
            if (at < lockedUntil) {
                refused++;
                hands.refuse(player);
                return;
            }
            forgetOldJoins(at, now);
            wave = waves.judge(List.copyOf(recent), join, at, now.wavePolicy());
            recent.add(join);
            watched.put(player, new Watch(name));
            if (!wave.sameNetwork().isEmpty()) {
                mark(BotSignal.SHARED_NETWORK, other -> Objects.equals(other.network(), join.network()));
            }
            if (!wave.sameNames().isEmpty()) {
                mark(BotSignal.SHARED_NAMES, other -> Objects.equals(JoinWaveRule.stem(other.name()), wave.stem()));
            }
            if (wave.isWave() && at - lastWaveLine >= now.waveSeconds() * 1000L) {
                lastWaveLine = at;
                waveLine = true;
            }
            lockdown = wave.lockdown() && now.autoLockdown();
        }
        if (waveLine) {
            staff.tell("anticheat.bot-wave", "count", wave.newcomers(), "seconds", now.waveSeconds(), "groups", groups(wave));
        }
        if (lockdown && !lockedDown()) {
            lockdown(now.lockdownMinutes(), "bot wave");
        }
    }

    private void mark(BotSignal signal, java.util.function.Predicate<JoinWaveRule.Join> member) {
        for (JoinWaveRule.Join other : recent) {
            Watch watch = watched.get(other.player());
            if (watch != null && member.test(other)) {
                synchronized (watch) {
                    watch.signals.add(signal);
                }
            }
        }
    }

    private static String groups(JoinWaveRule.Wave wave) {
        StringJoiner text = new StringJoiner("; ");
        if (!wave.sameNetwork().isEmpty()) {
            text.add(wave.sameNetwork().size() + " from one network: " + String.join(", ", wave.sameNetwork()));
        }
        if (!wave.sameNames().isEmpty()) {
            text.add(wave.sameNames().size() + " named " + wave.stem() + "…: " + String.join(", ", wave.sameNames()));
        }
        return text.length() == 0 ? "no shared network or names" : text.toString();
    }

    /** A chat line from somebody under watch. */
    public void chatted(UUID player, String line) {
        Watch watch = watched.get(player);
        if (watch == null || line == null) {
            return;
        }
        String said = line.trim().toLowerCase(Locale.ROOT);
        synchronized (watch) {
            watch.lines++;
            if (watch.said.size() < 50 || watch.said.containsKey(said)) {
                int times = watch.said.merge(said, 1, Integer::sum);
                watch.mostRepeated = Math.max(watch.mostRepeated, times);
            }
        }
    }

    /** Their first minutes are over: what they did, judged. */
    public void observed(UUID player, double movedBlocks, double turnedDegrees, String brand) {
        Watch watch = watched.remove(player);
        BotGuardSettings now = settings.get();
        if (watch == null || !now.bots()) {
            return;
        }
        Set<BotSignal> signals;
        NewcomerRule.Seen seen;
        synchronized (watch) {
            seen = new NewcomerRule.Seen(movedBlocks, turnedDegrees, watch.lines, watch.mostRepeated, brand);
            signals = EnumSet.copyOf(newcomers.judge(seen, now.botBrands()));
            signals.addAll(watch.signals);
        }
        if (signals.size() < ALERT_SIGNALS) {
            return;
        }
        List<String> words = new ArrayList<>();
        signals.forEach(signal -> words.add(signal.words()));
        String detail = String.join("; ", words);
        cases.record(player, watch.name, SuspectCase.Kind.BOT, clock.getAsLong(), detail);
        staff.tell("anticheat.bot-suspect", "player", watch.name, "count", signals.size(), "signals", detail);
        if (now.botReports()) {
            reports.file(player, watch.name, detail);
        }
        AutoAction action = AutoAction.parse(now.botAction());
        if (!action.isNone() && signals.size() >= now.actionSignals()) {
            hands.act(player, watch.name, action, detail);
            staff.tell("anticheat.suspect-acted", "player", watch.name, "action", action.describe());
        }
    }

    public boolean watching(UUID player) {
        return watched.containsKey(player);
    }

    /** New accounts are refused everywhere for {@code minutes}. */
    public void lockdown(int minutes, String by) {
        long until = clock.getAsLong() + Math.max(1, minutes) * 60_000L;
        synchronized (this) {
            lockedUntil = until;
            ownLockdown = true;
            refused = 0;
        }
        staff.tell("anticheat.lockdown-on", "minutes", minutes, "by", by);
        publisher.publish(TOPIC, "on", until + ";" + by);
    }

    /** @return whether there was a lockdown to lift */
    public boolean lift(String by) {
        int kept;
        synchronized (this) {
            if (!lockedDown()) {
                return false;
            }
            lockedUntil = 0;
            ownLockdown = false;
            kept = refused;
            refused = 0;
        }
        staff.tell("anticheat.lockdown-off", "by", by, "refused", kept);
        publisher.publish(TOPIC, "off", by);
        return true;
    }

    public boolean lockedDown() {
        return clock.getAsLong() < lockedUntil;
    }

    public long lockdownLeftMillis() {
        return Math.max(0, lockedUntil - clock.getAsLong());
    }

    private void heard(NetworkEvent event) {
        if (event.server().equals(bus.server())) {
            return;
        }
        synchronized (this) {
            ownLockdown = false;
            refused = 0;
            if ("on".equals(event.key())) {
                try {
                    lockedUntil = Long.parseLong(event.payload().split(";", 2)[0]);
                } catch (NumberFormatException broken) {
                    // A message this version cannot read changes nothing.
                }
            } else if ("off".equals(event.key())) {
                lockedUntil = 0;
            }
        }
    }

    /** About once a second: ends a lockdown that ran out, forgets joins past the window. */
    public void tick() {
        long now = clock.getAsLong();
        int kept = -1;
        synchronized (this) {
            forgetOldJoins(now, settings.get());
            if (lockedUntil != 0 && now >= lockedUntil) {
                lockedUntil = 0;
                if (ownLockdown) {
                    kept = refused;
                }
                ownLockdown = false;
                refused = 0;
            }
        }
        if (kept >= 0) {
            staff.tell("anticheat.lockdown-over", "refused", kept);
        }
    }

    private void forgetOldJoins(long now, BotGuardSettings current) {
        long window = current.waveSeconds() * 1000L;
        while (!recent.isEmpty() && now - recent.peekFirst().at() > window) {
            recent.pollFirst();
        }
    }

    /** How many joins, each with its network hash, are held right now. */
    public synchronized int remembered() {
        return recent.size();
    }

    public void forget(UUID player) {
        watched.remove(player);
    }

    @Override
    public void settings(ModerationSettings fresh) {
        // Reads its own settings (BotGuardSettings) at every join, so a change takes at once.
    }

    @Override
    public String describe() {
        return "watching new accounts for bots, and locking them out during a bot wave";
    }
}
