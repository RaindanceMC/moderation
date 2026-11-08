package de.raindancer.modules.moderation.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AltRuleTest {

    private final AltRule rule = new AltRule();
    private final UUID me = UUID.randomUUID();
    private final long now = 1_000_000_000L;

    private AltRule.Seen seen(String name, long ago, boolean banned) {
        return new AltRule.Seen(UUID.randomUUID(), name, now - ago, banned);
    }

    @Test
    @DisplayName("an address of my own, or shared with a friend or two, is nothing")
    void nothingUsual() {
        assertThat(rule.judge(me, List.of(), now).isEmpty()).isTrue();
        assertThat(rule.judge(me, List.of(new AltRule.Seen(me, "Me", now, true)), now).isEmpty()).isTrue();
        assertThat(rule.judge(me, List.of(seen("Sister", 1000, false), seen("Mate", 2000, false)), now).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a banned account from the same address lately is named")
    void aBannedAccountIsNamed() {
        AltRule.Suspicion suspicion = rule.judge(me, List.of(seen("Cheater", 1000, true), seen("Mate", 2000, false)), now);
        assertThat(suspicion.bannedNames()).containsExactly("Cheater");
        assertThat(suspicion.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("a sighting older than a month is forgotten")
    void oldSightingsDoNotCount() {
        AltRule.Suspicion suspicion = rule.judge(me, List.of(seen("OldCheater", AltRule.REMEMBER_MILLIS + 1, true)), now);
        assertThat(suspicion.isEmpty()).isTrue();
        assertThat(suspicion.accounts()).isEqualTo(1);
    }

    @Test
    @DisplayName("a crowd of accounts behind one address is worth a line")
    void aCrowdIsWorthALine() {
        AltRule.Suspicion suspicion = rule.judge(me, List.of(seen("A", 1, false), seen("B", 2, false), seen("C", 3, false)), now);
        assertThat(suspicion.accounts()).isEqualTo(AltRule.CROWD);
        assertThat(suspicion.isEmpty()).isFalse();
    }
}
