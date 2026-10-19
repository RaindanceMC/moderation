package de.raindancer.modules.moderation.store;

import de.raindancer.core.platform.permission.PermissionGroups;
import de.raindancer.modules.moderation.model.StaffRank;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is staff, read from the staff groups (LuckPerms, or Core's local groups without it).
 *
 * <p>The groups are the truth; this is a cache of who directly holds which, so screens and rules can ask
 * on the server thread. It follows changes through {@link #refresh} — the module wires that to
 * {@link PermissionGroups#onChange}, so a membership changed in {@code /lp editor} or on another server
 * shows up here too.
 *
 * <p>Writing methods and {@link #load} go to the groups and may block: never on the server thread.
 */
public final class StaffRoster {

    private final PermissionGroups groups;
    private final Map<UUID, Set<StaffRank>> held = new ConcurrentHashMap<>();

    public StaffRoster(PermissionGroups groups) {
        this.groups = groups;
    }

    /** Makes every staff group that does not exist yet, with its defaults. Existing ones are left alone. */
    public void seedGroups() {
        // Ladder first, so a group's parent exists before it.
        for (StaffRank rank : StaffRank.values()) {
            groups.seed(rank.seed());
        }
    }

    public boolean editable() {
        return groups.editable();
    }

    /** A rung replaces their current rung; a department is added to whatever they hold. */
    public synchronized boolean promote(UUID who, StaffRank rank) {
        if (who == null || rank == null) {
            return false;
        }
        boolean changed = false;
        if (rank.onLadder()) {
            for (StaffRank rung : StaffRank.ladder()) {
                if (rung != rank) {
                    changed |= groups.leave(who, rung.group());
                }
            }
        }
        changed |= groups.join(who, rank.group());
        refresh(who);
        return changed;
    }

    /** Takes one rank — a rung or a department — away. */
    public synchronized boolean takeAway(UUID who, StaffRank rank) {
        if (who == null || rank == null) {
            return false;
        }
        boolean changed = groups.leave(who, rank.group());
        refresh(who);
        return changed;
    }

    /** Off the staff: every staff group they are in. */
    public synchronized boolean demote(UUID who) {
        if (who == null) {
            return false;
        }
        boolean changed = false;
        for (StaffRank rank : StaffRank.values()) {
            changed |= groups.leave(who, rank.group());
        }
        refresh(who);
        return changed;
    }

    /** Re-reads one player's groups; {@code null} re-reads everybody. */
    public void refresh(UUID who) {
        if (who == null) {
            load();
            return;
        }
        Set<StaffRank> theirs = ranksIn(groups.directGroupsOf(who));
        if (theirs.isEmpty()) {
            held.remove(who);
        } else {
            held.put(who, theirs);
        }
    }

    /** Reads every staff group's members. */
    public synchronized void load() {
        Map<UUID, Set<StaffRank>> fresh = new HashMap<>();
        for (StaffRank rank : StaffRank.values()) {
            for (UUID member : groups.membersOf(rank.group())) {
                fresh.computeIfAbsent(member, id -> EnumSet.noneOf(StaffRank.class)).add(rank);
            }
        }
        held.keySet().retainAll(fresh.keySet());
        fresh.forEach((who, ranks) -> held.put(who, Set.copyOf(ranks)));
    }

    /** Their rung on the ladder — the highest, should they somehow be in two. */
    public Optional<StaffRank> rankOf(UUID who) {
        return ranksOf(who).stream().filter(StaffRank::onLadder)
                .max((a, b) -> Integer.compare(a.weight(), b.weight()));
    }

    public Set<StaffRank> departmentsOf(UUID who) {
        Set<StaffRank> departments = EnumSet.noneOf(StaffRank.class);
        ranksOf(who).stream().filter(rank -> !rank.onLadder()).forEach(departments::add);
        return Set.copyOf(departments);
    }

    /** Every staff rank they hold directly. */
    public Set<StaffRank> ranksOf(UUID who) {
        Set<StaffRank> theirs = who == null ? null : held.get(who);
        return theirs == null ? Set.of() : theirs;
    }

    public boolean isStaff(UUID who) {
        return who != null && held.containsKey(who);
    }

    public Set<UUID> everybody() {
        return Set.copyOf(held.keySet());
    }

    public List<UUID> ofRank(StaffRank rank) {
        List<UUID> found = new ArrayList<>();
        held.forEach((who, theirs) -> {
            if (theirs.contains(rank)) {
                found.add(who);
            }
        });
        return found;
    }

    public int size() {
        return held.size();
    }

    private static Set<StaffRank> ranksIn(Set<String> groupNames) {
        Set<StaffRank> ranks = EnumSet.noneOf(StaffRank.class);
        for (StaffRank rank : StaffRank.values()) {
            if (groupNames.contains(rank.group())) {
                ranks.add(rank);
            }
        }
        return Set.copyOf(ranks);
    }
}
