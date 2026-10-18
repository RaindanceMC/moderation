package de.raindancer.modules.moderation.store;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.log.Log;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.permission.Grants;
import de.raindancer.core.platform.permission.PermissionGroups;
import de.raindancer.modules.moderation.model.StaffRank;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Optional;
import java.util.UUID;

/**
 * Moves the staff of moderation &lt; 3.1 into the staff groups, once.
 *
 * <p>Before, {@code staff.yml} said who held which rank and the rank's nodes were granted to each person
 * through Core's {@link Grants} (with LuckPerms: a {@code rains_<rank>} group kept in step by code, plus
 * the same nodes directly on the user). Those are cleared — {@link Grants#clear} only takes what Core
 * granted — together with every staff node still set directly on them, and the person joins the new
 * group. {@code trial_mod} becomes support. {@code staff.yml} is kept as a record and marked
 * {@code moved-to-groups}, which stops it running again.
 */
public final class LegacyStaffImport {

    private static final LogChannel log = Log.of("moderation");
    static final String DONE = "moved-to-groups";

    private LegacyStaffImport() {
    }

    /** @return how many people were moved */
    public static int run(YamlStore legacy, Grants grants, PermissionGroups groups) {
        var read = legacy.readIfReadable();
        if (read.isEmpty() || read.get().getBoolean(DONE)) {
            return 0;
        }
        ConfigurationSection staff = read.get().getConfigurationSection("staff");
        int moved = 0;
        if (staff != null) {
            for (String id : staff.getKeys(false)) {
                UUID who;
                try {
                    who = UUID.fromString(id);
                } catch (IllegalArgumentException notAnId) {
                    continue;
                }
                Optional<StaffRank> rank = StaffRank.byName(staff.getString(id));
                if (rank.isEmpty()) {
                    log.warn("staff.yml: {} held '{}', which is no staff rank any more — not moved.",
                            id, staff.getString(id));
                    continue;
                }
                grants.clear(who);
                // Core's ledger of what it granted is not complete for staff promoted before RainsCore 2.0,
                // so the staff nodes set directly on them go by name. The group gives them from now on.
                var dropped = groups.dropDirect(who, StaffRank.everyGrantableNode());
                if (!dropped.isEmpty()) {
                    log.info("Took {} staff permission(s) set directly on {}: {}", dropped.size(), id,
                            String.join(", ", dropped));
                }
                if (groups.join(who, rank.get().group())) {
                    moved++;
                }
                log.info("Moved {} ({} in staff.yml) into the group {}.", id, staff.getString(id),
                        rank.get().group());
            }
        }
        if (!legacy.update(yaml -> yaml.set(DONE, true))) {
            log.warn("Could not mark staff.yml as moved; the move will be repeated (harmlessly) next start.");
        }
        return moved;
    }
}
