package de.raindancer.modules.moderation.rules;

import de.raindancer.core.moderation.rules.RulePenalty;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A command nobody but staff has any use for and a cheater reaches for — {@code /seed}, to crack the world for ore
 * and structure finders: which one was typed, and what the next try costs.
 */
public final class CheatAttemptRule implements IModerationRule {

    /** @return the watched command typed, without slash or namespace; empty for anything else */
    public Optional<String> matched(String typed, Collection<String> watched) {
        if (typed == null || watched == null) {
            return Optional.empty();
        }
        String first = typed.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (first.startsWith("/")) {
            first = first.substring(1);
        }
        String root = first.substring(first.indexOf(':') + 1);
        return watched.stream().map(command -> command.strip().toLowerCase(Locale.ROOT).replaceFirst("^/", ""))
                .filter(root::equals).findFirst();
    }

    /** What the try after {@code prior} earlier ones costs; past the top the top again. */
    public RulePenalty penalty(int prior, List<RulePenalty> ladder) {
        return ladder.get(Math.max(0, Math.min(ladder.size() - 1, prior)));
    }

    @Override
    public String describe() {
        return "which typed command is a cheat attempt, and what the next try costs";
    }
}
