package de.raindancer.modules.moderation.service;

import de.raindancer.core.platform.network.LocalBus;
import de.raindancer.core.platform.network.NetworkBus;
import de.raindancer.modules.moderation.BotGuardSettings;
import de.raindancer.modules.moderation.model.AutoAction;
import de.raindancer.modules.moderation.model.SuspectCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BotGuardTest {

    private final AtomicLong clock = new AtomicLong(1_000_000_000L);
    private final List<String> told = new ArrayList<>();
    private final List<String> cases = new ArrayList<>();
    private final List<UUID> refused = new ArrayList<>();
    private final List<String> acted = new ArrayList<>();
    private final List<String> reports = new ArrayList<>();
    private final AtomicReference<BotGuardSettings> settings = new AtomicReference<>(BotGuardSettings.DEFAULTS);

    private final BotGuard.Hands hands = new BotGuard.Hands() {
        @Override
        public void refuse(UUID player) {
            refused.add(player);
        }

        @Override
        public void act(UUID player, String name, AutoAction action, String why) {
            acted.add(name + ":" + action.kind() + ":" + why);
        }
    };

    private BotGuard guard(NetworkBus bus) {
        return new BotGuard(settings::get, clock::get, address -> address,
                (key, values) -> told.add(key + " " + Arrays.toString(values)),
                (player, name, kind, at, detail) -> cases.add(name + " " + kind + " " + detail),
                hands, (player, name, text) -> reports.add(name + ": " + text), bus, Runnable::run);
    }

    private BotGuard guard() {
        return guard(NetworkBus.NONE);
    }

    private UUID join(BotGuard guard, String name, String address) {
        UUID id = UUID.randomUUID();
        guard.joined(id, name, address, true, false);
        clock.addAndGet(2_000);
        return id;
    }

    private List<String> lines(String key) {
        return told.stream().filter(line -> line.startsWith(key + " ")).toList();
    }

    @Test
    @DisplayName("a wave with a shared network locks new accounts out; old ones and exempt ones still come in")
    void waveLocksDown() {
        BotGuard guard = guard();
        join(guard, "Anna", "1.1.1.1");
        join(guard, "Ben", "2.2.2.2");
        join(guard, "Carla", "3.3.3.3");
        join(guard, "xQ7", "9.9.9.9");
        join(guard, "Lp2", "9.9.9.9");
        join(guard, "Zt9", "9.9.9.9");
        assertThat(lines("anticheat.bot-wave")).hasSize(1);
        assertThat(guard.lockedDown()).isTrue();
        assertThat(lines("anticheat.lockdown-on")).hasSize(1);

        UUID next = join(guard, "Another", "4.4.4.4");
        assertThat(refused).containsExactly(next);
        UUID veteran = UUID.randomUUID();
        guard.joined(veteran, "Veteran", "9.9.9.9", false, false);
        UUID exempt = UUID.randomUUID();
        guard.joined(exempt, "Helper", "9.9.9.9", true, true);
        assertThat(refused).containsExactly(next);
        assertThat(guard.watching(veteran)).isFalse();
        assertThat(guard.watching(exempt)).isFalse();
    }

    @Test
    @DisplayName("the lockdown ends by itself and staff hear how many it kept out")
    void lockdownEnds() {
        BotGuard guard = guard();
        guard.lockdown(10, "Tom");
        join(guard, "One", "1.1.1.1");
        join(guard, "Two", "2.2.2.2");
        clock.addAndGet(10 * 60_000L);
        guard.tick();
        assertThat(guard.lockedDown()).isFalse();
        assertThat(lines("anticheat.lockdown-over")).singleElement().asString().contains("2");
        guard.tick();
        assertThat(lines("anticheat.lockdown-over")).hasSize(1);
    }

    @Test
    @DisplayName("a raid from everywhere is a line to staff and nothing more")
    void raidIsOnlyALine() {
        BotGuard guard = guard();
        for (int i = 0; i < 8; i++) {
            join(guard, "Viewer" + (char) ('A' + i) + "x", i + ".0.0.1");
        }
        assertThat(lines("anticheat.bot-wave")).hasSize(1);
        assertThat(guard.lockedDown()).isFalse();
        assertThat(refused).isEmpty();
    }

    @Test
    @DisplayName("without automatic lockdown a wave is only told")
    void noAutoLockdown() {
        settings.set(BotGuardSettings.DEFAULTS.withAutoLockdown(false));
        BotGuard guard = guard();
        for (int i = 0; i < 6; i++) {
            join(guard, "Bot" + i + "x", "9.9.9.9");
        }
        assertThat(lines("anticheat.bot-wave")).hasSize(1);
        assertThat(guard.lockedDown()).isFalse();
    }

    @Test
    @DisplayName("a newcomer who stands still and spams becomes a case; with no action set nobody is touched")
    void spamBotIsACase() {
        BotGuard guard = guard();
        UUID bot = join(guard, "Spammer", "1.1.1.1");
        for (int i = 0; i < 4; i++) {
            guard.chatted(bot, "cheap coins at example.org");
        }
        guard.observed(bot, 0, 0, "vanilla");
        assertThat(cases).singleElement().asString().startsWith("Spammer BOT");
        assertThat(lines("anticheat.bot-suspect")).hasSize(1);
        assertThat(acted).isEmpty();
        assertThat(guard.watching(bot)).isFalse();
        assertThat(reports).singleElement().asString().startsWith("Spammer: ").contains("spammed the chat");
    }

    @Test
    @DisplayName("with bot reports off a case is written and told, but nothing goes into the report queue")
    void noBotReports() {
        settings.set(BotGuardSettings.DEFAULTS.withBotReports(false));
        BotGuard guard = guard();
        UUID bot = join(guard, "Spammer", "1.1.1.1");
        for (int i = 0; i < 4; i++) {
            guard.chatted(bot, "cheap coins at example.org");
        }
        guard.observed(bot, 0, 0, "vanilla");
        assertThat(cases).hasSize(1);
        assertThat(reports).isEmpty();
    }

    @Test
    @DisplayName("one signal alone is no case: a newcomer reading the rules stands still too")
    void oneSignalIsNothing() {
        BotGuard guard = guard();
        UUID reader = join(guard, "Reader", "1.1.1.1");
        guard.observed(reader, 0, 0, "vanilla");
        assertThat(cases).isEmpty();
        assertThat(told).isEmpty();
        assertThat(reports).isEmpty();
    }

    @Test
    @DisplayName("accounts from one network share the signal, the first ones too, and the action applies at its count")
    void networkGroupAndAction() {
        settings.set(BotGuardSettings.DEFAULTS.withBotAction("freeze", 3));
        BotGuard guard = guard();
        UUID first = join(guard, "Alpha", "9.9.9.1");
        UUID second = join(guard, "Bravo", "9.9.9.2");
        UUID third = join(guard, "Charlie", "9.9.9.3");
        guard.observed(first, 0, 0, null);
        guard.observed(second, 12, 50, "vanilla");
        guard.observed(third, 0, 0, "vanilla");
        assertThat(cases).hasSize(2);
        assertThat(cases.get(0)).startsWith("Alpha BOT");
        assertThat(cases.get(1)).startsWith("Charlie BOT");
        assertThat(acted).singleElement().asString().startsWith("Alpha:FREEZE:");
    }

    @Test
    @DisplayName("network hashes are held only for the wave window, then forgotten")
    void addressesAreForgotten() {
        BotGuard guard = guard();
        join(guard, "One", "1.1.1.1");
        join(guard, "Two", "2.2.2.2");
        assertThat(guard.remembered()).isEqualTo(2);
        clock.addAndGet(BotGuardSettings.DEFAULTS.waveSeconds() * 1000L + 1);
        guard.tick();
        assertThat(guard.remembered()).isZero();
    }

    @Test
    @DisplayName("a lockdown on one server holds on every server, and lifting it anywhere lifts it everywhere")
    void lockdownAcrossTheNetwork() {
        LocalBus.Hub hub = new LocalBus.Hub();
        BotGuard lobby = guard(hub.bus("lobby"));
        BotGuard smp = guard(hub.bus("smp"));
        lobby.lockdown(15, "Tom");
        hub.drain();
        assertThat(smp.lockedDown()).isTrue();
        assertThat(smp.lockdownLeftMillis()).isEqualTo(15 * 60_000L);
        assertThat(smp.lift("Mia")).isTrue();
        hub.drain();
        assertThat(lobby.lockedDown()).isFalse();
        assertThat(lines("anticheat.lockdown-on")).hasSize(1);
        assertThat(lines("anticheat.lockdown-off")).hasSize(1);
        lobby.close();
        smp.close();
    }

    @Test
    @DisplayName("switched off it watches nobody and refuses nobody")
    void off() {
        settings.set(BotGuardSettings.DEFAULTS.withBots(false));
        BotGuard guard = guard();
        guard.lockdown(10, "Tom");
        UUID id = join(guard, "Bot", "9.9.9.9");
        assertThat(refused).isEmpty();
        assertThat(guard.watching(id)).isFalse();
    }

    @Test
    @DisplayName("somebody who leaves is forgotten")
    void forget() {
        BotGuard guard = guard();
        UUID id = join(guard, "Gone", "1.1.1.1");
        assertThat(guard.watching(id)).isTrue();
        guard.forget(id);
        assertThat(guard.watching(id)).isFalse();
        guard.chatted(id, "late");
        guard.observed(id, 0, 0, null);
        assertThat(cases).isEmpty();
        assertThat(SuspectCase.Kind.BOT).isNotNull();
    }
}
