package de.raindancer.modules.moderation;

import de.raindancer.core.ui.preference.Preference;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.service.AlertService;
import de.raindancer.modules.moderation.util.AttemptAlertSwitch;
import de.raindancer.modules.moderation.util.CheatPermissions;

import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/** What /preferences offers staff: the two alert switches that /anticheat alerts and /attempts alerts flip. */
public final class ModerationPreferences {

    static final String GROUP = "Staff alerts";

    private ModerationPreferences() {
    }

    public static Preference anticheatAlerts(Supplier<AlertService> alerts) {
        return Preference.toggle("moderation:anticheat-alerts", GROUP, "Anticheat alerts",
                        "Tells you in chat when somebody fails an anticheat check.",
                        AlertService.ALERTS::isOn,
                        (player, on) -> {
                            if (AlertService.ALERTS.isOn(player) != on) {
                                alerts.get().toggle(player);
                            }
                        })
                .needing(CheatPermissions.ALERTS);
    }

    public static Preference attemptAlerts(Supplier<Plugin> plugin) {
        return Preference.toggle("moderation:attempt-alerts", GROUP, "Alerts about attempts",
                        "Tells you in chat when a punished player tries to join, chat or act anyway.",
                        player -> new AttemptAlertSwitch(plugin.get()).isOn(player),
                        (player, on) -> new AttemptAlertSwitch(plugin.get()).set(player, on))
                .needing(ModerationPermission.ATTEMPTS.node());
    }
}
