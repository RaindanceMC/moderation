package de.raindancer.modules.moderation.model;

import de.raindancer.core.moderation.punishment.PunishmentKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutoActionTest {

    @Test
    @DisplayName("none, blank and rubbish all mean: only tell staff")
    void nothing() {
        assertThat(AutoAction.parse("none").isNone()).isTrue();
        assertThat(AutoAction.parse("").isNone()).isTrue();
        assertThat(AutoAction.parse(null).isNone()).isTrue();
        assertThat(AutoAction.parse("explode").isNone()).isTrue();
        assertThat(AutoAction.parse("ban soon").isNone()).isTrue();
    }

    @Test
    @DisplayName("kick, freeze and ban are read with their length, without one they last until lifted")
    void actions() {
        assertThat(AutoAction.parse("kick").kind()).isEqualTo(PunishmentKind.KICK);
        AutoAction freeze = AutoAction.parse("Freeze 1h");
        assertThat(freeze.kind()).isEqualTo(PunishmentKind.FREEZE);
        assertThat(freeze.length()).hasHours(1);
        AutoAction ban = AutoAction.parse("ban 7d");
        assertThat(ban.kind()).isEqualTo(PunishmentKind.BAN);
        assertThat(ban.length()).hasDays(7);
        assertThat(AutoAction.parse("ban").length()).isNull();
        assertThat(AutoAction.parse("ban forever").length()).isNull();
        assertThat(AutoAction.parse("ban forever").kind()).isEqualTo(PunishmentKind.BAN);
    }
}
