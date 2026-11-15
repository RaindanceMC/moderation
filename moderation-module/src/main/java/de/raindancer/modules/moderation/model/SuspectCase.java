package de.raindancer.modules.moderation.model;

import java.util.UUID;

/** A suspected bot account or macro user, for staff to look at. Holds no address. */
public record SuspectCase(long id, UUID player, String name, Kind kind, long at, String server, String detail) {

    public enum Kind {
        BOT, MACRO;

        public static Kind of(String text) {
            return "macro".equalsIgnoreCase(text) ? MACRO : BOT;
        }
    }
}
