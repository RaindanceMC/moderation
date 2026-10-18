package de.raindancer.modules.moderation;

import de.raindancer.core.data.settings.SettingsSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class StaffDutySettingsTest {

    @Test
    @DisplayName("the record is a valid settings schema — the module refuses to start otherwise")
    void validSchema() {
        assertThatCode(() -> SettingsSchema.of(StaffDutySettings.class, StaffDutySettings.DEFAULTS))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("off by default: staff keep their powers until an owner switches it on")
    void offByDefault() {
        assertThat(StaffDutySettings.DEFAULTS.powersNeedAdminMode()).isFalse();
    }
}
