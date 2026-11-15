package de.raindancer.modules.moderation.rules;

import de.raindancer.modules.moderation.AntiCheatSettings;
import de.raindancer.modules.moderation.model.BotSignal;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** What a new account's first minutes say about it: moved, looked around, chatted, and with which client. */
public final class NewcomerRule implements IModerationRule {

    /** Less than this many blocks from where they joined is standing still. */
    public static final double MOVED_BLOCKS = 1.0;
    /** Less than this many degrees of head turn is not looking around. */
    public static final double TURNED_DEGREES = 5.0;
    public static final int SAME_LINE = 3;
    public static final int MANY_LINES = 8;

    /**
     * @param movedBlocks   how far from where they joined
     * @param turnedDegrees how far the head turned, yaw and pitch together
     * @param mostRepeated  how often their most frequent chat line was sent
     * @param brand         the client brand, null if none was sent
     */
    public record Seen(double movedBlocks, double turnedDegrees, int chatLines, int mostRepeated, String brand) {
    }

    public Set<BotSignal> judge(Seen seen, List<String> botBrands) {
        Set<BotSignal> signals = EnumSet.noneOf(BotSignal.class);
        if (seen.movedBlocks() < MOVED_BLOCKS && seen.turnedDegrees() < TURNED_DEGREES) {
            signals.add(BotSignal.STILL);
        }
        if (seen.mostRepeated() >= SAME_LINE || seen.chatLines() >= MANY_LINES) {
            signals.add(BotSignal.SPAM);
        }
        if (seen.brand() == null || seen.brand().isBlank()) {
            signals.add(BotSignal.NO_BRAND);
        } else if (AntiCheatSettings.matching(botBrands, seen.brand()) != null) {
            signals.add(BotSignal.BOT_BRAND);
        }
        return signals;
    }

    @Override
    public String describe() {
        return "what a new account's first minutes say about it";
    }
}
