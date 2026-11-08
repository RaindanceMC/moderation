package de.raindancer.modules.moderation.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a shared address says when somebody joins: nothing, usually — a family, a school, a carrier that puts a
 * town behind one address. Two things are worth a line to staff: a banned account came from here lately, and an
 * address with a crowd of accounts behind it, which is what somebody cycling alts looks like.
 *
 * <p>Nobody is refused by this. It is a line in the staff chat; the decision stays theirs.
 */
public final class AltRule implements IModerationRule {

    /** Accounts behind one address before it is worth mentioning. */
    public static final int CROWD = 4;
    /** How long a sighting of an address counts. */
    public static final long REMEMBER_MILLIS = 30L * 24 * 60 * 60 * 1000;

    /** Another account seen from the same address: who, when, and whether a ban stands against it. */
    public record Seen(UUID player, String name, long lastSeenMillis, boolean banned) {
    }

    public record Suspicion(List<String> bannedNames, int accounts) {
        public boolean isEmpty() {
            return bannedNames.isEmpty() && accounts < CROWD;
        }
    }

    /**
     * @param joining the account joining now, which does not count against itself
     * @param others  everybody seen from the address, the joiner included or not
     * @param now     the moment, to leave stale sightings out
     */
    public Suspicion judge(UUID joining, List<Seen> others, long now) {
        List<String> banned = new ArrayList<>();
        int accounts = 1;
        for (Seen seen : others) {
            if (seen.player().equals(joining) || now - seen.lastSeenMillis() > REMEMBER_MILLIS) {
                continue;
            }
            accounts++;
            if (seen.banned()) {
                banned.add(seen.name());
            }
        }
        return new Suspicion(List.copyOf(banned), accounts);
    }

    @Override
    public String describe() {
        return "what a shared address is worth telling staff";
    }
}
