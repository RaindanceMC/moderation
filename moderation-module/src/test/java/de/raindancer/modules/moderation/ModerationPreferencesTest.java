package de.raindancer.modules.moderation;

import de.raindancer.core.ui.preference.Preference;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.service.AlertService;
import de.raindancer.modules.moderation.util.CheatPermissions;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Staff alerts on /preferences")
class ModerationPreferencesTest {

    private Plugin plugin;
    private AlertService alerts;
    private Player player;

    @BeforeEach
    void setUp() {
        alerts = mock(AlertService.class);
        plugin = mock(Plugin.class);
        when(plugin.namespace()).thenReturn("rainsmoderation");
        player = mock(Player.class);
        when(player.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
    }

    @Test
    void anticheatAlertsAreBuiltRight() {
        Preference preference = ModerationPreferences.anticheatAlerts(() -> alerts);

        assertThat(preference.id()).isEqualTo("moderation:anticheat-alerts");
        assertThat(preference.title()).isEqualTo("Anticheat alerts");
        assertThat(preference.group()).isEqualTo("Staff alerts");
        assertThat(preference.options()).containsExactly("On", "Off");
    }

    @Test
    void anticheatAlertsAreOnlyOfferedToHoldersOfThePermission() {
        Preference preference = ModerationPreferences.anticheatAlerts(() -> alerts);

        when(player.hasPermission(CheatPermissions.ALERTS)).thenReturn(false);
        assertThat(preference.shown().test(player)).isFalse();
        when(player.hasPermission(CheatPermissions.ALERTS)).thenReturn(true);
        assertThat(preference.shown().test(player)).isTrue();
    }

    @Test
    void anticheatAlertsSwitchOffThroughTheServiceAndNeverTwice() {
        Preference preference = ModerationPreferences.anticheatAlerts(() -> alerts);

        assertThat(preference.current().apply(player)).isZero();
        preference.choose().accept(player, 1);
        verify(alerts).toggle(player);
    }

    @Test
    void anticheatAlertsAlreadyOnAreLeftAlone() {
        Preference preference = ModerationPreferences.anticheatAlerts(() -> alerts);

        preference.choose().accept(player, 0);
        verify(alerts, never()).toggle(any());
    }

    @Test
    void attemptAlertsAreBuiltRight() {
        Preference preference = ModerationPreferences.attemptAlerts(() -> plugin);

        assertThat(preference.id()).isEqualTo("moderation:attempt-alerts");
        assertThat(preference.title()).isEqualTo("Alerts about attempts");
        assertThat(preference.group()).isEqualTo("Staff alerts");
        assertThat(preference.options()).containsExactly("On", "Off");
    }

    @Test
    void attemptAlertsAreOnlyOfferedToHoldersOfThePermission() {
        Preference preference = ModerationPreferences.attemptAlerts(() -> plugin);
        String node = ModerationPermission.ATTEMPTS.node();

        when(player.hasPermission(node)).thenReturn(false);
        assertThat(preference.shown().test(player)).isFalse();
        when(player.hasPermission(node)).thenReturn(true);
        assertThat(preference.shown().test(player)).isTrue();
    }

    @Test
    void attemptAlertsAreKeptInTheirSwitch() {
        Preference preference = ModerationPreferences.attemptAlerts(() -> plugin);
        PersistentDataContainer data = player.getPersistentDataContainer();

        assertThat(preference.current().apply(player)).isZero();
        preference.choose().accept(player, 1);
        verify(data).set(org.mockito.ArgumentMatchers.<org.bukkit.NamespacedKey>any(), org.mockito.ArgumentMatchers.<org.bukkit.persistence.PersistentDataType<Boolean, Boolean>>any(), org.mockito.ArgumentMatchers.eq(false));
    }
}
