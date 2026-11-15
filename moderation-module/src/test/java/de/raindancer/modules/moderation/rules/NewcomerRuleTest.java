package de.raindancer.modules.moderation.rules;

import de.raindancer.modules.moderation.model.BotSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewcomerRuleTest {

    private final NewcomerRule rule = new NewcomerRule();
    private final List<String> botBrands = List.of("mineflayer");

    @Test
    @DisplayName("somebody who walked, looked about and said hello is nothing")
    void ordinaryNewcomer() {
        assertThat(rule.judge(new NewcomerRule.Seen(14.0, 90.0, 2, 1, "vanilla"), botBrands)).isEmpty();
    }

    @Test
    @DisplayName("standing still alone is one signal, not enough on its own (people read the rules)")
    void stillIsOneSignal() {
        assertThat(rule.judge(new NewcomerRule.Seen(0.2, 1.0, 0, 0, "fabric"), botBrands))
                .containsExactly(BotSignal.STILL);
    }

    @Test
    @DisplayName("the same line three times, or eight lines in the first minutes, is spam")
    void spam() {
        assertThat(rule.judge(new NewcomerRule.Seen(5, 40, 3, 3, "vanilla"), botBrands)).containsExactly(BotSignal.SPAM);
        assertThat(rule.judge(new NewcomerRule.Seen(5, 40, 8, 1, "vanilla"), botBrands)).containsExactly(BotSignal.SPAM);
        assertThat(rule.judge(new NewcomerRule.Seen(5, 40, 7, 2, "vanilla"), botBrands)).isEmpty();
    }

    @Test
    @DisplayName("no client brand, or a known bot library, is a signal")
    void brands() {
        assertThat(rule.judge(new NewcomerRule.Seen(5, 40, 0, 0, null), botBrands)).containsExactly(BotSignal.NO_BRAND);
        assertThat(rule.judge(new NewcomerRule.Seen(5, 40, 0, 0, " "), botBrands)).containsExactly(BotSignal.NO_BRAND);
        assertThat(rule.judge(new NewcomerRule.Seen(5, 40, 0, 0, "Mineflayer/4.2"), botBrands))
                .containsExactly(BotSignal.BOT_BRAND);
    }

    @Test
    @DisplayName("a spam bot that stands still collects both")
    void spamBot() {
        assertThat(rule.judge(new NewcomerRule.Seen(0, 0, 12, 12, "vanilla"), botBrands))
                .containsExactlyInAnyOrder(BotSignal.STILL, BotSignal.SPAM);
    }
}
