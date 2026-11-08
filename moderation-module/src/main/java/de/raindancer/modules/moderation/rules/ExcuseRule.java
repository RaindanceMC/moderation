package de.raindancer.modules.moderation.rules;

import de.raindancer.modules.moderation.model.Category;
import de.raindancer.modules.moderation.model.CheckType;

/**
 * What this server itself gives a player that makes an honest client look odd to Grim — and what the server is
 * doing to itself that makes every client look odd.
 *
 * <h2>Why the anti-cheat needs to know what the plugins do</h2>
 * Grim predicts vanilla movement. This server lets a player ride a flying companion (a pet that is an ender dragon,
 * made smaller, driven by Core), lends flight inside a claim or for a round of a game, and teleports people about.
 * None of that is cheating, all of it fails a movement prediction, and a level that climbs on it reaches a kick on an
 * honest player. The checks stay Grim's; this is the one place that says "not this one, not now".
 *
 * <p>Lag is the other honest cause: a server behind on ticks, or a connection far away, makes a vanilla client send
 * what looks like speed or timer. While either is bad, a flag still counts — half — and never kicks or bans.
 */
public final class ExcuseRule implements IModerationRule {

    /** How long after leaving a companion's back movement flags are still its fault: the dismount is a fall. */
    public static final long AFTER_RIDE_MILLIS = 3_000;
    /** A connection slower than this makes honest movement look wrong. */
    public static final int LAGGY_PING = 400;
    /** How much a flag counts while the server or the connection lags. */
    public static final double LAG_WEIGHT = 0.5;

    public enum Verdict {
        /** Counts as it is. */
        COUNTS,
        /** Counts half and leads to no kick or ban. */
        SOFTENED,
        /** Does not count: what the server gave the player explains it. */
        EXCUSED
    }

    /**
     * @param ridingCompanion  on the back of one of Core's companions right now
     * @param sinceRodeMillis  milliseconds since they last were, or {@link Long#MAX_VALUE}
     * @param flightLeased     Core lent them flight (a claim's perk, a game, /fly)
     * @param tps              the server's ticks a second now
     * @param minTps           below this the server, not the player, is to blame
     * @param ping             their connection, in milliseconds
     */
    public record Context(boolean ridingCompanion, long sinceRodeMillis, boolean flightLeased, double tps, double minTps,
                          int ping) {
        public static final Context NONE = new Context(false, Long.MAX_VALUE, false, 20, 0, 0);
    }

    public Verdict judge(CheckType check, Context context) {
        if (context == null) {
            return Verdict.COUNTS;
        }
        if (check.category() == Category.MOVEMENT
                && (context.ridingCompanion() || context.sinceRodeMillis() < AFTER_RIDE_MILLIS || context.flightLeased())) {
            return Verdict.EXCUSED;
        }
        if (context.tps() < context.minTps() || context.ping() > LAGGY_PING) {
            return Verdict.SOFTENED;
        }
        return Verdict.COUNTS;
    }

    /** Why a flag was excused, for the evidence — one phrase, the first reason that applies. */
    public String reason(Context context) {
        if (context.ridingCompanion()) {
            return "riding a companion";
        }
        if (context.sinceRodeMillis() < AFTER_RIDE_MILLIS) {
            return "just off a companion";
        }
        if (context.flightLeased()) {
            return "flight lent by the server";
        }
        if (context.tps() < context.minTps()) {
            return "server lag";
        }
        return "a slow connection";
    }

    @Override
    public String describe() {
        return "what the server gave a player that excuses a flag, and what lag softens";
    }
}
