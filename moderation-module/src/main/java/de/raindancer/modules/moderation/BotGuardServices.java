package de.raindancer.modules.moderation;

import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.modules.moderation.service.BotGuard;
import de.raindancer.modules.moderation.service.MacroWatch;
import de.raindancer.modules.moderation.store.SuspectCases;

/** The bot and macro part of the anti-cheat, for its command and screens. */
public record BotGuardServices(SettingsStore<BotGuardSettings> settings, BotGuard guard, MacroWatch macros,
                               SuspectCases cases) {

    public BotGuardSettings config() {
        return settings.current();
    }
}
