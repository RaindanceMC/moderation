package de.raindancer.modules.moderation.rules;

import de.raindancer.core.moderation.punishment.PunishmentKind;

import java.util.Collection;
import java.util.Locale;

/**
 * Whether a punishment wipes the anti-cheat's counts on somebody: a ban for one of the cheating rules. The record of
 * punishments keeps everything; it is only the anti-cheat that starts counting afresh, so a player who comes back from
 * a cheating ban is judged on what they do from then on, not on the violations that got them banned.
 */
public final class FreshStartRule implements IModerationRule {

    /**
     * @param rules the rules whose bans start the anti-cheat afresh — by id, or by title as players read it
     *              ({@code no-cheating} for "No cheating": any case, spaces as hyphens)
     */
    public boolean startsFresh(PunishmentKind kind, String ruleId, String ruleTitle, Collection<String> rules) {
        if (kind != PunishmentKind.BAN || rules == null) {
            return false;
        }
        String id = slug(ruleId);
        String title = slug(ruleTitle);
        return rules.stream().map(FreshStartRule::slug)
                .anyMatch(rule -> !rule.isEmpty() && (rule.equals(id) || rule.equals(title)));
    }

    private static String slug(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    @Override
    public String describe() {
        return "whether a ban wipes the anti-cheat's counts: a ban for a cheating rule";
    }
}
