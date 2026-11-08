package de.raindancer.modules.moderation.store;

import de.raindancer.modules.moderation.model.MiningLedger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class XrayLedgersTest {

    private static final UUID WHO = UUID.fromString("e7b725cc-d75e-4eb7-9d2b-5b2652fc435b");

    @Test
    @DisplayName("a find's time survives a restart")
    void timeSurvivesRestart(@TempDir Path folder) {
        XrayLedgers saved = new XrayLedgers(folder, () -> 0L);
        saved.of(WHO, "Miner").dug("world, with comma", 7, 30, -7, MiningLedger.BAIT, 1_760_000_000_000L);
        assertThat(saved.flush()).isTrue();

        XrayLedgers loaded = new XrayLedgers(folder, () -> 0L);
        loaded.load();

        assertThat(loaded.find(WHO).finds()).containsExactly(
                new MiningLedger.Find("world, with comma", 7, 30, -7, MiningLedger.BAIT, 1_760_000_000_000L));
    }

    @Test
    @DisplayName("finds saved before times were kept still load, with no time")
    void oldFindsLoad(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve("xray-ledgers.yml"), """
                players:
                  %s:
                    name: Miner
                    updated: 0
                    trail-world: world
                    finds:
                    - 1,-50,2,-2,world
                """.formatted(WHO));

        XrayLedgers loaded = new XrayLedgers(folder, () -> 0L);
        loaded.load();

        assertThat(loaded.find(WHO).finds()).containsExactly(new MiningLedger.Find("world", 1, -50, 2, MiningLedger.BAIT, 0));
    }

    @Test
    @DisplayName("when somebody was measured afresh survives a restart")
    void freshFromSurvivesRestart(@TempDir Path folder) {
        XrayLedgers saved = new XrayLedgers(folder, () -> 0L);
        saved.of(WHO, "Miner").measureAfresh(1_760_000_000_000L);
        assertThat(saved.flush()).isTrue();

        XrayLedgers loaded = new XrayLedgers(folder, () -> 0L);
        loaded.load();

        assertThat(loaded.find(WHO).freshFromMillis()).isEqualTo(1_760_000_000_000L);
    }

    @Test
    @DisplayName("a forgotten ledger is gone from disk too, and nobody else's goes with it")
    void forgottenIsGoneFromDisk(@TempDir Path folder) {
        UUID other = UUID.fromString("0f0c1a2b-3c4d-4e5f-8a9b-0c1d2e3f4a5b");
        XrayLedgers saved = new XrayLedgers(folder, () -> 0L);
        saved.of(WHO, "Miner").dug("world", 1, -50, 1, MiningLedger.BAIT, 0);
        saved.of(other, "Other").dug("world", 2, -50, 2, MiningLedger.BAIT, 0);
        assertThat(saved.flush()).isTrue();

        saved.forget(WHO);
        assertThat(saved.flush()).isTrue();

        XrayLedgers loaded = new XrayLedgers(folder, () -> 0L);
        loaded.load();
        assertThat(loaded.find(WHO)).isNull();
        assertThat(loaded.find(other)).isNotNull();
    }

    @Test
    @DisplayName("a flush with nothing changed since the last one leaves the file alone")
    void cleanFlushWritesNothing(@TempDir Path folder) throws Exception {
        XrayLedgers saved = new XrayLedgers(folder, () -> 0L);
        saved.of(WHO, "Miner").dug("world", 7, 30, -7, MiningLedger.BAIT, 5L);
        assertThat(saved.flush()).isTrue();
        Files.delete(folder.resolve("xray-ledgers.yml"));

        assertThat(saved.flush()).isTrue();

        assertThat(folder.resolve("xray-ledgers.yml")).doesNotExist();
    }

    @Test
    @DisplayName("only a changed ledger is rebuilt, and the file still holds everybody's")
    void changedAndUnchangedTogether(@TempDir Path folder) {
        UUID other = UUID.fromString("0f0c1a2b-3c4d-4e5f-8a9b-0c1d2e3f4a5b");
        XrayLedgers saved = new XrayLedgers(folder, () -> 0L);
        saved.of(WHO, "Miner").dug("world", 1, -50, 1, MiningLedger.BAIT, 1L);
        saved.of(other, "Other").dug("world", 2, -50, 2, MiningLedger.BAIT, 2L);
        assertThat(saved.flush()).isTrue();
        saved.of(WHO, "Miner").dug("world", 3, -50, 3, MiningLedger.BAIT, 3L);
        assertThat(saved.flush()).isTrue();

        XrayLedgers loaded = new XrayLedgers(folder, () -> 0L);
        loaded.load();

        assertThat(loaded.find(WHO).finds()).hasSize(2);
        assertThat(loaded.find(other).finds()).hasSize(1);
    }

    @Test
    @DisplayName("a ledger written on one server is read on another through the network's database")
    void theNetworkCarriesALedger(@TempDir Path folder) throws Exception {
        try (de.raindancer.core.data.sql.Database shared = de.raindancer.core.data.sql.Database.open(
                folder.resolve("anticheat.db"), AntiCheatDatabase.SCHEMA, () -> false)) {
            XrayLedgers smp = new XrayLedgers(folder.resolve("smp"), () -> 0L);
            smp.mirrorTo(shared);
            smp.of(WHO, "Miner").dug("world", 1, 2, 3, MiningLedger.BAIT, 1_760_000_000_000L);
            assertThat(smp.flush()).isTrue();

            XrayLedgers oneblock = new XrayLedgers(folder.resolve("oneblock"), () -> 0L);
            oneblock.mirrorTo(shared);
            assertThat(oneblock.find(WHO)).isNull();
            oneblock.arriving(WHO);
            assertThat(oneblock.find(WHO)).isNotNull();
            assertThat(oneblock.find(WHO).finds()).containsExactly(
                    new MiningLedger.Find("world", 1, 2, 3, MiningLedger.BAIT, 1_760_000_000_000L));
            assertThat(oneblock.nameOf(WHO)).isEqualTo("Miner");
        }
    }
}
