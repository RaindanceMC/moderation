package de.raindancer.modules.moderation.store;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DutyOpStoreTest {

    @Test
    @DisplayName("a server's operators kept for duty are written and read back; nothing is nobody")
    void roundTrip() throws Exception {
        Set<UUID> ops = Set.of(UUID.randomUUID(), UUID.randomUUID());
        YamlConfiguration yaml = new YamlConfiguration();
        DutyOpStore.write(yaml, "servers.smp", ops);
        YamlConfiguration reread = new YamlConfiguration();
        reread.loadFromString(yaml.saveToString());
        assertThat(DutyOpStore.read(reread, "servers.smp")).isEqualTo(ops);
        assertThat(DutyOpStore.read(reread, "servers.hub")).isNull();
    }
}
