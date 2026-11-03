package de.raindancer.modules.moderation.rules;

import de.raindancer.core.moderation.punishment.PunishmentKind;
import de.raindancer.core.moderation.rules.RulePenalty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheatAttemptRuleTest {

    private final CheatAttemptRule rule = new CheatAttemptRule();

    @Test
    @DisplayName("/seed is a cheat attempt whatever its case, namespace or arguments; other commands are not")
    void matched() {
        List<String> watched = List.of("seed");
        assertThat(rule.matched("/seed", watched)).contains("seed");
        assertThat(rule.matched("/SEED", watched)).contains("seed");
        assertThat(rule.matched("/minecraft:seed", watched)).contains("seed");
        assertThat(rule.matched("/seed please", watched)).contains("seed");
        assertThat(rule.matched("/seeds", watched)).isEmpty();
        assertThat(rule.matched("/home seed", watched)).isEmpty();
        assertThat(rule.matched("/seed", List.of())).isEmpty();
    }

    @Test
    @DisplayName("the first try is a warning, the second a ban for a day, and it stays there")
    void ladder() {
        List<RulePenalty> ladder = RulePenalty.ladder("warn, ban 1d").orElseThrow();
        assertThat(rule.penalty(0, ladder).kind()).isEqualTo(PunishmentKind.WARNING);
        RulePenalty second = rule.penalty(1, ladder);
        assertThat(second.kind()).isEqualTo(PunishmentKind.BAN);
        assertThat(second.length()).isEqualTo(Duration.ofDays(1));
        assertThat(rule.penalty(7, ladder)).isEqualTo(second);
    }
}
