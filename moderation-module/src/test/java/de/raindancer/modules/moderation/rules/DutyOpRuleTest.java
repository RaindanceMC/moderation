package de.raindancer.modules.moderation.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DutyOpRuleTest {

    private final DutyOpRule rule = new DutyOpRule();

    private static DutyOpRule.Situation of(boolean adminRank, boolean adminsAreOp, boolean onDutyOp,
                                           boolean powersNeedAdminMode, boolean inAdminMode) {
        return new DutyOpRule.Situation(adminRank, adminsAreOp, onDutyOp, powersNeedAdminMode, inAdminMode);
    }

    @Test
    @DisplayName("with powers needing admin mode, an op is op only in admin mode — hand-made ops too")
    void onlyOnDuty() {
        assertThat(rule.decide(of(false, false, true, true, false))).isEqualTo(DutyOpRule.Decision.DEOP);
        assertThat(rule.decide(of(false, false, true, true, true))).isEqualTo(DutyOpRule.Decision.OP);
        assertThat(rule.decide(of(true, true, false, true, false))).isEqualTo(DutyOpRule.Decision.DEOP);
        assertThat(rule.decide(of(true, true, false, true, true))).isEqualTo(DutyOpRule.Decision.OP);
    }

    @Test
    @DisplayName("somebody who is no op on duty and no admin with admins-are-op is never opped")
    void nobodyElse() {
        assertThat(rule.decide(of(false, false, false, true, true))).isEqualTo(DutyOpRule.Decision.LEAVE);
        assertThat(rule.decide(of(true, false, false, true, true))).isEqualTo(DutyOpRule.Decision.LEAVE);
    }

    @Test
    @DisplayName("with the switch off: admins are op if the server says so, operators kept for duty get their op back, the rest is left")
    void switchOff() {
        assertThat(rule.decide(of(true, true, false, false, false))).isEqualTo(DutyOpRule.Decision.OP);
        assertThat(rule.decide(of(false, true, false, false, false))).isEqualTo(DutyOpRule.Decision.DEOP_IF_OURS);
        assertThat(rule.decide(of(false, false, true, false, false))).as("an operator kept for duty is opped back")
                .isEqualTo(DutyOpRule.Decision.OP);
        assertThat(rule.decide(of(false, false, false, false, false))).isEqualTo(DutyOpRule.Decision.LEAVE);
    }

    @Test
    @DisplayName("nobody who is neither an admin with admins-are-op nor an operator the owner made is ever opped")
    void neverATrial() {
        for (boolean adminRank : new boolean[] {false, true}) {
            for (boolean adminsAreOp : new boolean[] {false, true}) {
                for (boolean needAdminMode : new boolean[] {false, true}) {
                    for (boolean inAdminMode : new boolean[] {false, true}) {
                        if (adminRank && adminsAreOp) {
                            continue;
                        }
                        assertThat(rule.decide(of(adminRank, adminsAreOp, false, needAdminMode, inAdminMode)))
                                .isNotEqualTo(DutyOpRule.Decision.OP);
                    }
                }
            }
        }
    }
}
