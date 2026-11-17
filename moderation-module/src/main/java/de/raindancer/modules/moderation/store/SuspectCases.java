package de.raindancer.modules.moderation.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.modules.moderation.model.SuspectCase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Bot and macro cases, in the network's anti-cheat database, so staff anywhere see them. Disk work: off the server's threads. */
public final class SuspectCases {

    private final Database database;
    private final String server;

    public SuspectCases(Database database, String server) {
        this.database = database;
        this.server = server == null ? "" : server;
    }

    public boolean record(UUID player, String name, SuspectCase.Kind kind, long at, String detail) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO ac_suspect (player, name, kind, at, server, detail) VALUES (?,?,?,?,?,?)")) {
                row.setString(1, player.toString());
                row.setString(2, name == null ? "" : name);
                row.setString(3, kind.name().toLowerCase(Locale.ROOT));
                row.setLong(4, at);
                row.setString(5, server);
                row.setString(6, detail == null ? "" : detail);
                row.executeUpdate();
            }
        });
    }

    public List<SuspectCase> newest(int limit) {
        return select(null, limit);
    }

    /** One player's cases from every server, newest first. */
    public List<SuspectCase> about(UUID player, int limit) {
        return select(player, limit);
    }

    public int count(UUID player) {
        return database.read(connection -> {
            try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*) FROM ac_suspect WHERE player = ?")) {
                query.setString(1, player.toString());
                try (ResultSet rows = query.executeQuery()) {
                    return rows.next() ? rows.getInt(1) : 0;
                }
            }
        }).orElse(0);
    }

    private List<SuspectCase> select(UUID player, int limit) {
        return database.read(connection -> {
            List<SuspectCase> found = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, player, name, kind, at, server, detail FROM ac_suspect"
                            + (player == null ? "" : " WHERE player = ?") + " ORDER BY at DESC, id DESC LIMIT ?")) {
                int at = 1;
                if (player != null) {
                    query.setString(at++, player.toString());
                }
                query.setInt(at, Math.max(1, limit));
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        found.add(new SuspectCase(rows.getLong(1), UUID.fromString(rows.getString(2)), rows.getString(3),
                                SuspectCase.Kind.of(rows.getString(4)), rows.getLong(5), rows.getString(6), rows.getString(7)));
                    }
                }
            }
            return found;
        }).orElse(List.of());
    }

    /** Staff looked at it: it leaves the list. */
    public boolean dismiss(long id) {
        return database.write(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM ac_suspect WHERE id = ?")) {
                delete.setLong(1, id);
                delete.executeUpdate();
            }
        });
    }

    /** Deletes every case from before {@code before}: the keeping time. */
    public boolean sweep(long before) {
        return database.write(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM ac_suspect WHERE at < ?")) {
                delete.setLong(1, before);
                delete.executeUpdate();
            }
        });
    }
}
