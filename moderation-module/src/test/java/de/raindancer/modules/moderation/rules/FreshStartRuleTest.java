package de.raindancer.modules.moderation.rules;

import de.raindancer.core.moderation.punishment.PunishmentKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FreshStartRuleTest {

    private final FreshStartRule rule = new FreshStartRule();

    @Test
    @DisplayName("a ban for a cheating rule wipes the anti-cheat's counts; any case of the rule id will do")
    void banForCheating() {
        assertThat(rule.startsFresh(PunishmentKind.BAN, "k3x9q1", "No cheating", List.of("no-cheating")))
                .as("by the rule's title, as players read it").isTrue();
        assertThat(rule.startsFresh(PunishmentKind.BAN, "k3x9q1", "Something else", List.of("k3x9q1")))
                .as("by its id").isTrue();
        assertThat(rule.startsFresh(PunishmentKind.BAN, "k3x9q1", "No  Cheating", List.of("No Cheating"))).isTrue();
    }

    @Test
    @DisplayName("anything short of a ban, or another rule, leaves the counts as they are")
    void otherwise() {
        assertThat(rule.startsFresh(PunishmentKind.MUTE, "a1", "No cheating", List.of("no-cheating"))).isFalse();
        assertThat(rule.startsFresh(PunishmentKind.WARNING, "a1", "No cheating", List.of("no-cheating"))).isFalse();
        assertThat(rule.startsFresh(PunishmentKind.BAN, "a1", "Be kind", List.of("no-cheating"))).isFalse();
        assertThat(rule.startsFresh(PunishmentKind.BAN, "a1", "No cheating", List.of())).isFalse();
        assertThat(rule.startsFresh(PunishmentKind.BAN, null, null, List.of("no-cheating"))).isFalse();
    }
}
