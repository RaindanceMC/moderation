package de.raindancer.modules.moderation.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.modules.moderation.rules.AltRule;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Which accounts came from which address, as a keyed hash that cannot be read back — kept a month, in the network's
 * anti-cheat database, so an alt that hops servers is the same alt. Disk work: call off the server's threads.
 */
public final class KnownAddresses {

    private final Database database;

    public KnownAddresses(Database database) {
        this.database = database;
    }

    /** A sighting: this account came from this address now. */
    public boolean record(UUID player, String name, String addressHash, long now) {
        if (addressHash == null || addressHash.isBlank()) {
            return false;
        }
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO ac_address (player, name, address, last_seen) VALUES (?,?,?,?) "
                            + "ON CONFLICT(player, address) DO UPDATE SET name = excluded.name, last_seen = excluded.last_seen")) {
                row.setString(1, player.toString());
                row.setString(2, name == null ? "" : name);
                row.setString(3, addressHash);
                row.setLong(4, now);
                row.executeUpdate();
            }
            try (PreparedStatement sweep = connection.prepareStatement("DELETE FROM ac_address WHERE last_seen < ?")) {
                sweep.setLong(1, now - AltRule.REMEMBER_MILLIS);
                sweep.executeUpdate();
            }
        });
    }

    /** Everybody seen from this address, the joiner included; whether a ban stands is for the caller to add. */
    public List<AltRule.Seen> sharing(String addressHash) {
        if (addressHash == null || addressHash.isBlank()) {
            return List.of();
        }
        return database.read(connection -> {
            List<AltRule.Seen> seen = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT player, name, last_seen FROM ac_address WHERE address = ? ORDER BY last_seen DESC LIMIT 50")) {
                query.setString(1, addressHash);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        seen.add(new AltRule.Seen(UUID.fromString(rows.getString(1)), rows.getString(2), rows.getLong(3), false));
                    }
                }
            }
            return seen;
        }).orElse(List.of());
    }
}
