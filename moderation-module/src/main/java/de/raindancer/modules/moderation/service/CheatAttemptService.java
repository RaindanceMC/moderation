package de.raindancer.modules.moderation.service;

import de.raindancer.core.moderation.rules.RulePenalty;
import de.raindancer.modules.moderation.CheatAttemptSettings;
import de.raindancer.modules.moderation.ModerationSettings;
import de.raindancer.modules.moderation.model.Sentence;
import de.raindancer.modules.moderation.rules.CheatAttemptRule;
import de.raindancer.modules.moderation.store.RuleOffences;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Punishes a command only a cheater reaches for ({@code /seed}): the first try is a warning, the next a ban, by the
 * server's ladder — on the record like any punishment, from the console. Tries are counted per player in their own
 * file, apart from rule offences.
 */
public final class CheatAttemptService implements IModerationService {

    private final PunishmentService punishments;
    private final RuleOffences tries;
    private final Supplier<CheatAttemptSettings> settings;
    private final CheatAttemptRule rule = new CheatAttemptRule();

    public CheatAttemptService(PunishmentService punishments, RuleOffences tries, Supplier<CheatAttemptSettings> settings) {
        this.punishments = punishments;
        this.tries = tries;
        this.settings = settings;
    }

    @Override
    public void settings(ModerationSettings fresh) {
        // Reads its own settings (CheatAttemptSettings) at each try, so a change takes at once.
    }

    /**
     * Somebody who is not an owner typed {@code typed}: punishes it when it is a cheat attempt.
     *
     * @return the punishment handed out, if any
     */
    public Optional<String> check(UUID player, String name, String typed) {
        CheatAttemptSettings live = settings.get();
        if (!live.enabled()) {
            return Optional.empty();
        }
        Optional<String> command = rule.matched(typed, live.commands());
        Optional<List<RulePenalty>> ladder = RulePenalty.ladder(live.punishments());
        if (command.isEmpty() || ladder.isEmpty() || ladder.get().isEmpty()) {
            return Optional.empty();
        }
        String key = "attempt:" + command.get();
        RulePenalty penalty = rule.penalty(tries.count(player, key), ladder.get());
        String reason = "Trying to cheat (/" + command.get() + ")";
        Sentence sentence = penalty.length() == null ? Sentence.forEver() : Sentence.of(penalty.length());
        punishments.punish(null, "Console", player, name, penalty.kind(), sentence, reason);
        tries.add(player, key);
        return Optional.of(penalty.describe());
    }
}
