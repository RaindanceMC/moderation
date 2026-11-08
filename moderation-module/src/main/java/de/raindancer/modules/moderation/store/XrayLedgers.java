package de.raindancer.modules.moderation.store;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.modules.moderation.model.MiningLedger;
import de.raindancer.modules.moderation.model.OreKind;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Every player's mining ledger, kept across restarts in {@code xray-ledgers.yml}. */
public final class XrayLedgers {

    private final YamlStore store;
    private final LongSupplier clock;
    private final Map<UUID, MiningLedger> ledgers = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    /** Whose ledger may have changed since it was last written; everybody else's saved rows are reused. */
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Map<String, Object>> rows = new ConcurrentHashMap<>();
    private volatile boolean membership = true;

    public XrayLedgers(Path dataFolder, LongSupplier clock) {
        this.store = new YamlStore(dataFolder.resolve("xray-ledgers.yml"));
        this.clock = clock;
    }

    public MiningLedger of(UUID who, String name) {
        if (name != null) {
            names.put(who, name);
        }
        dirty.add(who);
        return ledgers.computeIfAbsent(who, ignored -> new MiningLedger(clock.getAsLong()));
    }

    /** Read-mostly, but callers also decay or restart a ledger through it, so it counts as possibly changed. */
    public MiningLedger find(UUID who) {
        MiningLedger found = ledgers.get(who);
        if (found != null) {
            dirty.add(who);
        }
        return found;
    }

    public String nameOf(UUID who) {
        return names.get(who);
    }

    public Set<UUID> everybody() {
        return Set.copyOf(ledgers.keySet());
    }

    public void forget(UUID who) {
        ledgers.remove(who);
        rows.remove(who);
        dirty.remove(who);
        membership = true;
    }

    public void load() {
        ledgers.clear();
        rows.clear();
        dirty.clear();
        ConfigurationSection root = store.read().getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            UUID who;
            try {
                who = UUID.fromString(id);
            } catch (IllegalArgumentException notAnId) {
                continue;
            }
            ConfigurationSection row = root.getConfigurationSection(id);
            if (row == null) {
                continue;
            }
            try {
                Map<String, Double> revealed = new HashMap<>();
                ConfigurationSection bands = row.getConfigurationSection("revealed");
                if (bands != null) {
                    for (String band : bands.getKeys(false)) {
                        revealed.put(YamlStore.fromPathPart(band), bands.getDouble(band));
                    }
                }
                Map<String, Map<OreKind, Double>> veins = new HashMap<>();
                ConfigurationSection veinBands = row.getConfigurationSection("veins");
                if (veinBands != null) {
                    for (String band : veinBands.getKeys(false)) {
                        ConfigurationSection kinds = veinBands.getConfigurationSection(band);
                        Map<OreKind, Double> counts = new EnumMap<>(OreKind.class);
                        if (kinds != null) {
                            for (String kind : kinds.getKeys(false)) {
                                counts.put(OreKind.valueOf(kind), kinds.getDouble(kind));
                            }
                        }
                        veins.put(YamlStore.fromPathPart(band), counts);
                    }
                }
                List<int[]> trail = new ArrayList<>();
                for (String step : row.getStringList("trail")) {
                    String[] parts = step.split(",");
                    if (parts.length == 4) {
                        trail.add(new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]), Integer.parseInt(parts[3])});
                    }
                }
                List<MiningLedger.Find> finds = null;
                if (row.isList("found")) {
                    finds = new ArrayList<>();
                    for (String find : row.getStringList("found")) {
                        String[] parts = find.split(",", 6);
                        if (parts.length == 6) {
                            finds.add(new MiningLedger.Find(parts[5], Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Long.parseLong(parts[4])));
                        }
                    }
                } else if (row.isList("finds")) {
                    // Saved before finds kept their time.
                    finds = new ArrayList<>();
                    for (String find : row.getStringList("finds")) {
                        // World last, so a comma in a world name stays in the name.
                        String[] parts = find.split(",", 5);
                        if (parts.length == 5) {
                            finds.add(new MiningLedger.Find(parts[4], Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), 0));
                        }
                    }
                }
                MiningLedger ledger = new MiningLedger(row.getLong("updated"));
                ledger.restore(revealed, veins, row.getDouble("bait-reached"), row.getDouble("bait-expected"),
                        row.getDouble("toward"), row.getDouble("away"), row.getLong("updated"),
                        row.getString("trail-world", ""), trail, finds);
                ledger.restoreFreshFrom(row.getLong("fresh-from"));
                ledgers.put(who, ledger);
                String name = row.getString("name");
                if (name != null && !name.isEmpty()) {
                    names.put(who, name);
                }
            } catch (RuntimeException malformed) {
                // One unreadable player costs that player's ledger, not everybody's.
            }
        }
    }

    /** @return whether the file is as it should be; with nothing changed since the last write it is not touched */
    public boolean flush() {
        if (dirty.isEmpty() && !membership) {
            return true;
        }
        boolean complete = membership;
        Set<UUID> changed = Set.copyOf(dirty);
        membership = false;
        dirty.removeAll(changed);
        Map<UUID, Map<String, Object>> built = new HashMap<>();
        for (UUID who : changed) {
            MiningLedger ledger = ledgers.get(who);
            if (ledger != null) {
                built.put(who, row(who, ledger));
            }
        }
        rows.putAll(built);
        for (UUID who : ledgers.keySet()) {
            rows.computeIfAbsent(who, id -> row(id, ledgers.get(id)));
        }
        rows.keySet().retainAll(ledgers.keySet());
        Map<UUID, Map<String, Object>> snapshot = new HashMap<>(rows);
        boolean written = store.write(yaml -> snapshot.forEach((who, row) ->
                row.forEach((path, value) -> yaml.set("players." + who + "." + path, value))));
        if (!written) {
            dirty.addAll(changed);
            membership = membership || complete;
        }
        return written;
    }

    private Map<String, Object> row(UUID who, MiningLedger ledger) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("name", names.getOrDefault(who, ""));
        row.put("updated", ledger.updatedMillis());
        if (ledger.freshFromMillis() > 0) {
            row.put("fresh-from", ledger.freshFromMillis());
        }
        row.put("bait-reached", ledger.baitReached());
        row.put("bait-expected", ledger.baitExpected());
        row.put("toward", ledger.turnsToward());
        row.put("away", ledger.turnsAway());
        ledger.revealedByBand().forEach((band, value) -> row.put("revealed." + YamlStore.asPathPart(band), value));
        ledger.veinsByBand().forEach((band, kinds) -> kinds.forEach((kind, value) ->
                row.put("veins." + YamlStore.asPathPart(band) + "." + kind.name(), value)));
        row.put("trail-world", ledger.trailWorld());
        List<String> trail = new ArrayList<>();
        for (int[] step : ledger.trail()) {
            trail.add(step[0] + "," + step[1] + "," + step[2] + "," + step[3]);
        }
        row.put("trail", trail);
        List<String> finds = new ArrayList<>();
        for (MiningLedger.Find find : ledger.finds()) {
            finds.add(find.x() + "," + find.y() + "," + find.z() + "," + find.kind() + "," + find.atMillis() + "," + find.world());
        }
        row.put("found", finds);
        return row;
    }
}
