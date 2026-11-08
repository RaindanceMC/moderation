package de.raindancer.modules.moderation.rules;

import de.raindancer.modules.moderation.model.CheckType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcuseRuleTest {

    private final ExcuseRule rule = new ExcuseRule();

    @Test
    @DisplayName("riding a companion, or just off one, excuses movement and nothing else")
    void ridingExcusesMovement() {
        ExcuseRule.Context riding = new ExcuseRule.Context(true, 0, false, 20, 15, 30);
        assertThat(rule.judge(CheckType.GRIM_MOVEMENT, riding)).isEqualTo(ExcuseRule.Verdict.EXCUSED);
        assertThat(rule.judge(CheckType.GRIM_COMBAT, riding)).isEqualTo(ExcuseRule.Verdict.COUNTS);
        ExcuseRule.Context justOff = new ExcuseRule.Context(false, ExcuseRule.AFTER_RIDE_MILLIS - 1, false, 20, 15, 30);
        assertThat(rule.judge(CheckType.GRIM_MOVEMENT, justOff)).isEqualTo(ExcuseRule.Verdict.EXCUSED);
        ExcuseRule.Context longOff = new ExcuseRule.Context(false, ExcuseRule.AFTER_RIDE_MILLIS, false, 20, 15, 30);
        assertThat(rule.judge(CheckType.GRIM_MOVEMENT, longOff)).isEqualTo(ExcuseRule.Verdict.COUNTS);
    }

    @Test
    @DisplayName("flight the server lent excuses movement")
    void lentFlightExcusesMovement() {
        ExcuseRule.Context flying = new ExcuseRule.Context(false, Long.MAX_VALUE, true, 20, 15, 30);
        assertThat(rule.judge(CheckType.GRIM_MOVEMENT, flying)).isEqualTo(ExcuseRule.Verdict.EXCUSED);
        assertThat(rule.judge(CheckType.GRIM_WORLD, flying)).isEqualTo(ExcuseRule.Verdict.COUNTS);
        assertThat(rule.reason(flying)).isEqualTo("flight lent by the server");
    }

    @Test
    @DisplayName("a lagging server or a slow connection softens every flag")
    void lagSoftens() {
        ExcuseRule.Context slowServer = new ExcuseRule.Context(false, Long.MAX_VALUE, false, 12, 15, 30);
        assertThat(rule.judge(CheckType.GRIM_COMBAT, slowServer)).isEqualTo(ExcuseRule.Verdict.SOFTENED);
        assertThat(rule.reason(slowServer)).isEqualTo("server lag");
        ExcuseRule.Context slowLine = new ExcuseRule.Context(false, Long.MAX_VALUE, false, 20, 15, ExcuseRule.LAGGY_PING + 1);
        assertThat(rule.judge(CheckType.GRIM_PACKETS, slowLine)).isEqualTo(ExcuseRule.Verdict.SOFTENED);
        assertThat(rule.reason(slowLine)).isEqualTo("a slow connection");
    }

    @Test
    @DisplayName("nothing to say, nothing changes")
    void otherwiseCounts() {
        assertThat(rule.judge(CheckType.GRIM_MOVEMENT, ExcuseRule.Context.NONE)).isEqualTo(ExcuseRule.Verdict.COUNTS);
        assertThat(rule.judge(CheckType.GRIM_MOVEMENT, null)).isEqualTo(ExcuseRule.Verdict.COUNTS);
    }
}
