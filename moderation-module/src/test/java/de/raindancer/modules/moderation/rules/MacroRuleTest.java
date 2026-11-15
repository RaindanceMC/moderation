package de.raindancer.modules.moderation.rules;

import de.raindancer.modules.moderation.model.ClickCadence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MacroRuleTest {

    private final MacroRule rule = new MacroRule();
    private final MacroRule.Policy policy = new MacroRule.Policy(40, 5, 180_000, 22);

    /** Clicks at about every {@code every} ms with a spread of {@code spread} (a fraction), as the server sees them: on ticks. */
    private static long clicks(ClickCadence cadence, boolean left, long every, double spread, long duration, long seed,
                               boolean turning) {
        Random random = new Random(seed);
        long at = 0;
        float yaw = 0;
        while (at < duration) {
            long gap = Math.max(1, Math.round(every * (1 + random.nextGaussian() * spread)));
            at += gap;
            long onTick = (at / 50) * 50 + random.nextInt(3);
            if (turning) {
                yaw += (float) random.nextGaussian() * 3;
            }
            if (left) {
                cadence.swing(onTick, yaw, 0);
            } else {
                cadence.rightClick(onTick, yaw, 0);
            }
        }
        return at;
    }

    private Optional<MacroRule.Verdict> judge(ClickCadence cadence, ClickCadence.Hand hand, long now) {
        return rule.judge(cadence.sample(hand, now, policy.windowMillis()), policy);
    }

    @Test
    @DisplayName("a macro clicking every 600 ms for four minutes is caught, with the numbers for staff")
    void macroIsCaught() {
        ClickCadence cadence = new ClickCadence(1000);
        long end = clicks(cadence, true, 600, 0.03, 240_000, 1, false);
        Optional<MacroRule.Verdict> verdict = judge(cadence, ClickCadence.Hand.LEFT, end);
        assertThat(verdict).isPresent();
        assertThat(verdict.get().fast()).isFalse();
        assertThat(verdict.get().medianMillis()).isBetween(550L, 650L);
        assertThat(verdict.get().variationPercent()).isLessThan(5);
        assertThat(verdict.get().intervals()).isGreaterThanOrEqualTo(40);
        assertThat(verdict.get().minutes()).isEqualTo(4);
        assertThat(verdict.get().stillPercent()).isEqualTo(100);
    }

    @Test
    @DisplayName("a person clicking at the same pace is not: their gaps wander")
    void humanIsNot() {
        for (long seed = 0; seed < 20; seed++) {
            ClickCadence cadence = new ClickCadence(1000);
            long end = clicks(cadence, true, 600, 0.15, 240_000, seed, true);
            assertThat(judge(cadence, ClickCadence.Hand.LEFT, end)).as("seed %s", seed).isEmpty();
        }
    }

    @Test
    @DisplayName("a regular run shorter than the window is not enough yet")
    void tooShort() {
        ClickCadence cadence = new ClickCadence(1000);
        long end = clicks(cadence, true, 600, 0.0, 120_000, 2, false);
        assertThat(judge(cadence, ClickCadence.Hand.LEFT, end)).isEmpty();
    }

    @Test
    @DisplayName("holding the right button repeats every four ticks in vanilla, which is not a macro")
    void heldRightButton() {
        ClickCadence cadence = new ClickCadence(2000);
        long end = clicks(cadence, false, 200, 0.0, 240_000, 3, false);
        assertThat(judge(cadence, ClickCadence.Hand.RIGHT, end)).isEmpty();
    }

    @Test
    @DisplayName("a right-click macro at another pace is caught")
    void rightClickMacro() {
        ClickCadence cadence = new ClickCadence(1000);
        long end = clicks(cadence, false, 1500, 0.0, 240_000, 4, false);
        assertThat(judge(cadence, ClickCadence.Hand.RIGHT, end)).isPresent();
    }

    @Test
    @DisplayName("fast clicking is judged by its rate, not its rhythm, since ticks swallow the rhythm")
    void autoclicker() {
        ClickCadence fast = new ClickCadence(1000);
        long end = clicks(fast, true, 33, 0.0, 5_000, 5, true);
        Optional<MacroRule.Verdict> verdict = judge(fast, ClickCadence.Hand.LEFT, end);
        assertThat(verdict).isPresent();
        assertThat(verdict.get().fast()).isTrue();
        assertThat(verdict.get().cps()).isGreaterThan(22);

        ClickCadence quickHuman = new ClickCadence(1000);
        end = clicks(quickHuman, true, 80, 0.3, 5_000, 6, true);
        assertThat(judge(quickHuman, ClickCadence.Hand.LEFT, end)).isEmpty();
    }

    @Test
    @DisplayName("a burst shorter than three seconds is not fast clicking")
    void shortBurst() {
        ClickCadence cadence = new ClickCadence(1000);
        long end = clicks(cadence, true, 33, 0.0, 2_000, 7, true);
        assertThat(judge(cadence, ClickCadence.Hand.LEFT, end)).isEmpty();
    }

    @Test
    @DisplayName("right clicks are never judged by their rate: a held button is fast and honest")
    void rightClicksHaveNoRate() {
        ClickCadence cadence = new ClickCadence(1000);
        long end = clicks(cadence, false, 33, 0.3, 5_000, 8, true);
        assertThat(judge(cadence, ClickCadence.Hand.RIGHT, end)).isEmpty();
    }
}
