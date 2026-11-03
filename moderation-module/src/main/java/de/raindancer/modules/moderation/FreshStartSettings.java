package de.raindancer.modules.moderation;

import de.raindancer.core.data.settings.Describe;
import de.raindancer.core.data.settings.In;
import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Settings;
import de.raindancer.core.data.settings.Title;
import de.raindancer.core.data.settings.Topic;
import org.bukkit.Material;

import java.util.List;

/** Which rule bans wipe the anti-cheat's counts on a player, so they start afresh — their record keeps everything. */
@Settings(id = "moderation-fresh-start", topics = {
        @Topic(path = "moderation/anticheat/fresh-start", title = "Fresh start after a ban", icon = Material.CLOCK),
})
public record FreshStartSettings(

        @In("moderation/anticheat/fresh-start") @Title("Rules whose ban starts the anti-cheat afresh")
        @Describe("Rules by title as players read them (no-cheating for \"No cheating\") or by id, one per line. "
                + "A ban for one of these — by a moderator or by the anti-cheat — keeps every "
                + "punishment on the record but wipes the anti-cheat's counts (violations, x-ray ledger), as "
                + "/ac reset does, so a player back from the ban is judged on what they do from then on.")
        @Key("anticheat.fresh-start-rules")
        List<String> rules) {

    public static final FreshStartSettings DEFAULTS = new FreshStartSettings(List.of("no-cheating"));

    public FreshStartSettings {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
