package de.raindancer.modules.moderation.service;

import de.raindancer.modules.moderation.BotGuardSettings;
import de.raindancer.modules.moderation.model.AutoAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MacroWatchTest {

    private final AtomicLong clock = new AtomicLong(0);
    private final List<String> told = new ArrayList<>();
    private final List<String> cases = new ArrayList<>();
    private final List<String> reports = new ArrayList<>();
    private final List<String> acted = new ArrayList<>();
    private final AtomicReference<BotGuardSettings> settings = new AtomicReference<>(BotGuardSettings.DEFAULTS);
    private final UUID player = UUID.randomUUID();

    private MacroWatch watch() {
        return new MacroWatch(settings::get, clock::get,
                (key, values) -> told.add(key + " " + Arrays.toString(values)),
                (id, name, kind, at, detail) -> cases.add(name + " " + kind + " " + detail),
                new BotGuard.Hands() {
                    @Override
                    public void refuse(UUID id) {
                    }

                    @Override
                    public void act(UUID id, String name, AutoAction action, String why) {
                        acted.add(name + ":" + action.kind());
                    }
                },
                (id, name, text) -> reports.add(name + ": " + text));
    }

    private void click(MacroWatch watch, long every, double spread, long duration, long seed) {
        Random random = new Random(seed);
        long start = clock.get();
        float yaw = 0;
        while (clock.get() - start < duration) {
            clock.addAndGet(Math.max(50, Math.round(every * (1 + random.nextGaussian() * spread))));
            if (spread > 0) {
                yaw += (float) random.nextGaussian() * 3;
            }
            watch.swing(player, "Farmer", yaw, 0);
        }
    }

    @Test
    @DisplayName("an AFK macro is told once, written down and reported, and nobody is punished by default")
    void macroIsTold() {
        MacroWatch watch = watch();
        click(watch, 625, 0, 6 * 60_000L, 1);
        assertThat(told).filteredOn(line -> line.startsWith("anticheat.macro-suspect ")).hasSize(1);
        assertThat(cases).singleElement().asString().startsWith("Farmer MACRO");
        assertThat(reports).hasSize(1);
        assertThat(acted).isEmpty();
    }

    @Test
    @DisplayName("an owner who set an action gets it, once per finding")
    void action() {
        settings.set(BotGuardSettings.DEFAULTS.withMacroAction("kick"));
        MacroWatch watch = watch();
        click(watch, 625, 0, 4 * 60_000L, 2);
        assertThat(acted).containsExactly("Farmer:KICK");
    }

    @Test
    @DisplayName("without reports switched on only staff hear it")
    void noReport() {
        settings.set(BotGuardSettings.DEFAULTS.withMacroReports(false));
        MacroWatch watch = watch();
        click(watch, 625, 0, 4 * 60_000L, 3);
        assertThat(reports).isEmpty();
        assertThat(cases).hasSize(1);
    }

    @Test
    @DisplayName("a person grinding by hand is left alone")
    void humanIsLeftAlone() {
        MacroWatch watch = watch();
        click(watch, 625, 0.15, 6 * 60_000L, 4);
        assertThat(told).isEmpty();
        assertThat(cases).isEmpty();
    }

    @Test
    @DisplayName("mining swings are not clicks")
    void mining() {
        MacroWatch watch = watch();
        watch.digging(player, true);
        click(watch, 50, 0, 4 * 60_000L, 5);
        assertThat(told).isEmpty();
    }

    @Test
    @DisplayName("switched off, nothing is measured; leaving forgets the measurements")
    void offAndForget() {
        settings.set(BotGuardSettings.DEFAULTS.withMacros(false));
        MacroWatch watch = watch();
        click(watch, 625, 0, 4 * 60_000L, 6);
        assertThat(told).isEmpty();
        assertThat(watch.watched()).isZero();
        settings.set(BotGuardSettings.DEFAULTS);
        watch.swing(player, "Farmer", 0, 0);
        assertThat(watch.watched()).isEqualTo(1);
        watch.forget(player);
        assertThat(watch.watched()).isZero();
    }
}
