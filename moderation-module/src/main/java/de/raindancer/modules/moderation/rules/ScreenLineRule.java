package de.raindancer.modules.moderation.rules;

import de.raindancer.core.world.time.Times;
import de.raindancer.modules.moderation.model.Standing;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/** What Moderation's screens count and say. */
public final class ScreenLineRule implements IModerationRule {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int STAFF_SHOWN = 10;

    /** One staff member who is on and not vanished. */
    public record Seen(String name, String rank, String colour, int weight) {
    }

    public String staff(List<Seen> online) {
        String head = "<gold><bold>Staff online</bold></gold>";
        if (online.isEmpty()) {
            return head + "\n<gray>Nobody right now";
        }
        StringBuilder text = new StringBuilder(head);
        online.stream()
                .sorted(Comparator.comparingInt(Seen::weight).reversed()
                        .thenComparing(seen -> seen.name().toLowerCase()))
                .limit(STAFF_SHOWN)
                .forEach(seen -> text.append("\n<").append(seen.colour()).append(">").append(seen.rank())
                        .append(" <white>").append(MINI.escapeTags(seen.name())));
        if (online.size() > STAFF_SHOWN) {
            text.append("\n<dark_gray>+ ").append(online.size() - STAFF_SHOWN).append(" more");
        }
        return text.toString();
    }

    /** @param remaining null for one that lasts until a moderator lifts it */
    public String restriction(String what, Duration remaining) {
        return "<gold>" + what + " <gray>· " + (remaining == null ? "until lifted" : Times.describe(remaining) + " left");
    }

    /**
     * @param restrictions lines as {@link #restriction} wrote them
     * @param owed         the fines still owed, written in the currency, or null when none are
     */
    public String you(Standing standing, int recentWarnings, List<String> restrictions, String owed) {
        StringBuilder text = new StringBuilder("<gold><bold>Your standing</bold></gold>")
                .append("\n<gray>Standing <").append(standing.colour()).append(">").append(standing.describe())
                .append("\n<gray>Warnings <white>").append(recentWarnings).append(" recently");
        restrictions.forEach(line -> text.append("\n").append(line));
        if (owed != null) {
            text.append("\n<gray>Fines owed <red>").append(owed);
        }
        return text.toString();
    }

    @Override
    public String describe() {
        return "what Moderation's screens count and say";
    }
}
