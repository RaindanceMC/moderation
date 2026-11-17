package de.raindancer.modules.moderation.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.testkit.TestDatabases;
import de.raindancer.modules.moderation.model.SuspectCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Bot and macro suspects are one list for the whole network, and nothing in it outlives its keeping time. */
@Tag("database")
class SuspectCasesTest {

    @TempDir
    Path folder;

    private final List<Database> databases = new ArrayList<>();

    private SuspectCases server(String name) {
        Database database = TestDatabases.open(folder.resolve("anticheat.db"), AntiCheatDatabase.SCHEMA);
        databases.add(database);
        return new SuspectCases(database, name);
    }

    @AfterEach
    void close() {
        databases.forEach(Database::close);
    }

    @Test
    @DisplayName("a case written on one server is read on another, newest first, with where it happened")
    void sharedAcrossServers() {
        SuspectCases hub = server("hub");
        SuspectCases smp = server("smp");
        UUID bot = UUID.randomUUID();
        UUID clicker = UUID.randomUUID();
        assertThat(hub.record(bot, "Bot_1", SuspectCase.Kind.BOT, 1000, "stood still; no client brand")).isTrue();
        assertThat(smp.record(clicker, "Clicker", SuspectCase.Kind.MACRO, 2000, "left clicks every 600 ms")).isTrue();

        List<SuspectCase> seen = hub.newest(10);
        assertThat(seen).extracting(SuspectCase::name).containsExactly("Clicker", "Bot_1");
        assertThat(seen.get(0).server()).isEqualTo("smp");
        assertThat(seen.get(0).kind()).isEqualTo(SuspectCase.Kind.MACRO);
        assertThat(seen.get(1).player()).isEqualTo(bot);
        assertThat(seen.get(1).detail()).isEqualTo("stood still; no client brand");
    }

    @Test
    @DisplayName("a dismissed case is gone")
    void dismiss() {
        SuspectCases hub = server("hub");
        hub.record(UUID.randomUUID(), "Bot_1", SuspectCase.Kind.BOT, 1000, "x");
        long id = hub.newest(1).get(0).id();
        assertThat(hub.dismiss(id)).isTrue();
        assertThat(hub.newest(10)).isEmpty();
    }

    @Test
    @DisplayName("cases older than the keeping time are deleted")
    void sweep() {
        SuspectCases hub = server("hub");
        hub.record(UUID.randomUUID(), "Old", SuspectCase.Kind.BOT, 1000, "x");
        hub.record(UUID.randomUUID(), "New", SuspectCase.Kind.MACRO, 9000, "y");
        assertThat(hub.sweep(5000)).isTrue();
        assertThat(hub.newest(10)).extracting(SuspectCase::name).containsExactly("New");
    }

    @Test
    @DisplayName("one player's cases, from every server, newest first, and how many there are")
    void aboutOnePlayer() {
        SuspectCases hub = server("hub");
        SuspectCases smp = server("smp");
        UUID clicker = UUID.randomUUID();
        hub.record(clicker, "Clicker", SuspectCase.Kind.MACRO, 1000, "first");
        hub.record(UUID.randomUUID(), "Other", SuspectCase.Kind.BOT, 1500, "not theirs");
        smp.record(clicker, "Clicker", SuspectCase.Kind.MACRO, 2000, "second");

        assertThat(hub.about(clicker, 10)).extracting(SuspectCase::detail).containsExactly("second", "first");
        assertThat(hub.count(clicker)).isEqualTo(2);
        assertThat(hub.count(UUID.randomUUID())).isZero();
    }
}
