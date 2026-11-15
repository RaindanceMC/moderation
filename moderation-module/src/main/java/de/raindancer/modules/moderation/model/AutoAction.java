package de.raindancer.modules.moderation.model;

import de.raindancer.core.moderation.punishment.Durations;
import de.raindancer.core.moderation.punishment.PunishmentKind;

import java.time.Duration;
import java.util.Locale;

/**
 * What an owner set to happen to a suspect on its own: nothing, a kick, a freeze (quarantine) or a ban, with a
 * length or until lifted. Anything unreadable is nothing, so a typo never punishes.
 */
public record AutoAction(PunishmentKind kind, Duration length) {

    public static final AutoAction NONE = new AutoAction(null, null);

    public static AutoAction parse(String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }
        String[] words = text.trim().toLowerCase(Locale.ROOT).split("\\s+", 2);
        PunishmentKind kind = switch (words[0]) {
            case "kick" -> PunishmentKind.KICK;
            case "freeze", "quarantine" -> PunishmentKind.FREEZE;
            case "ban" -> PunishmentKind.BAN;
            default -> null;
        };
        if (kind == null) {
            return NONE;
        }
        if (words.length == 1 || kind == PunishmentKind.KICK || Durations.isForEver(words[1])) {
            return new AutoAction(kind, null);
        }
        return Durations.parse(words[1]).map(length -> new AutoAction(kind, length)).orElse(NONE);
    }

    public boolean isNone() {
        return kind == null;
    }

    /** "freeze for 1 hour", "ban until lifted", "kick". */
    public String describe() {
        if (kind == null) {
            return "none";
        }
        String word = kind.name().toLowerCase(Locale.ROOT);
        if (kind == PunishmentKind.KICK) {
            return word;
        }
        return length == null ? word + " until lifted" : word + " for " + Durations.describe(length);
    }
}
