package de.raindancer.modules.moderation;

import de.raindancer.core.data.settings.Describe;
import de.raindancer.core.data.settings.In;
import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Settings;
import de.raindancer.core.data.settings.Title;
import de.raindancer.core.data.settings.Topic;
import org.bukkit.Material;

import java.util.List;

/** Commands only a cheater reaches for, and what each try costs. */
@Settings(id = "moderation-cheat-attempts", topics = {
        @Topic(path = "moderation/anticheat/attempts", title = "Cheat attempts", icon = Material.TRIPWIRE_HOOK),
})
public record CheatAttemptSettings(

        @In("moderation/anticheat/attempts") @Title("Punish cheat attempts")
        @Describe("Anybody typing one of the commands below is punished, by the ladder below — staff too. Only the "
                + "server's owners never are: the owner rank and its operators.")
        @Key("attempts.enabled")
        boolean enabled,

        @In("moderation/anticheat/attempts") @Title("Commands")
        @Describe("One per line, without the slash. /seed gives away the world's seed, which ore and structure "
                + "finders need.")
        @Key("attempts.commands")
        List<String> commands,

        @In("moderation/anticheat/attempts") @Title("What each try costs")
        @Describe("The 1st, 2nd, … try, like a rule's punishments: warn, kick, mute 1h, ban 1d, ban (for good). "
                + "Past the last, the last again.")
        @Key("attempts.punishments")
        String punishments) {

    public static final CheatAttemptSettings DEFAULTS = new CheatAttemptSettings(true, List.of("seed"), "warn, ban 1d");

    public CheatAttemptSettings {
        commands = commands == null ? List.of() : List.copyOf(commands);
        punishments = punishments == null ? "" : punishments;
    }
}
