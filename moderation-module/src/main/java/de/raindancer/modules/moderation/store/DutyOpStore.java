package de.raindancer.modules.moderation.store;

import de.raindancer.core.data.store.KeyedDocument;
import de.raindancer.core.data.store.YamlStore;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Each server's operators kept for duty: while staff powers need admin mode they are op only in admin mode, and
 * when the switch goes off again they are opped back. In {@code duty-ops.yml}, one entry per server id.
 */
public final class DutyOpStore {

    private final KeyedDocument<Set<UUID>> document;

    public DutyOpStore(Path dataFolder) {
        this(YamlStore.network(dataFolder.resolve("duty-ops.yml")));
    }

    public DutyOpStore(YamlStore store) {
        this.document = new KeyedDocument<>(store, "servers", DutyOpStore::read, DutyOpStore::write);
    }

    public boolean load() {
        return document.load();
    }

    public Set<UUID> of(String server) {
        return document.get(server).orElse(Set.of());
    }

    /** @return whether it was written */
    public boolean add(String server, Collection<UUID> operators) {
        return document.change(server, current -> {
            Set<UUID> now = new HashSet<>(current == null ? Set.of() : current);
            now.addAll(operators);
            return now;
        });
    }

    /** @return whether it was written */
    public boolean clear(String server) {
        return document.change(server, current -> null);
    }

    static Set<UUID> read(ConfigurationSection yaml, String path) {
        Set<UUID> ops = new HashSet<>();
        for (String text : yaml.getStringList(path)) {
            try {
                ops.add(UUID.fromString(text));
            } catch (IllegalArgumentException damaged) {
                // A hand edit that names nobody.
            }
        }
        return ops.isEmpty() ? null : ops;
    }

    static void write(ConfigurationSection yaml, String path, Set<UUID> ops) {
        yaml.set(path, ops.stream().map(UUID::toString).sorted().toList());
    }
}
