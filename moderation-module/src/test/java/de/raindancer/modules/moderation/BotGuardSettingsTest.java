package de.raindancer.modules.moderation;

import de.raindancer.core.data.settings.SettingsSchema;
import de.raindancer.modules.moderation.model.AutoAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class BotGuardSettingsTest {

    @Test
    @DisplayName("the record is a valid settings schema, the module refuses to start otherwise")
    void validSchema() {
        assertThatCode(() -> SettingsSchema.of(BotGuardSettings.class, BotGuardSettings.DEFAULTS))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("by default it tells staff and punishes nobody")
    void noPunishmentByDefault() {
        assertThat(AutoAction.parse(BotGuardSettings.DEFAULTS.botAction()).isNone()).isTrue();
        assertThat(AutoAction.parse(BotGuardSettings.DEFAULTS.macroAction()).isNone()).isTrue();
        assertThat(BotGuardSettings.DEFAULTS.bots()).isTrue();
        assertThat(BotGuardSettings.DEFAULTS.macros()).isTrue();
    }

    @Test
    @DisplayName("cases are kept for a limited time")
    void keepingTime() {
        assertThat(BotGuardSettings.DEFAULTS.keepDays()).isBetween(1, 90);
    }
}
