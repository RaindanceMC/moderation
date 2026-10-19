package de.raindancer.modules.moderation;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.permission.Grants;
import de.raindancer.core.platform.permission.LocalGroups;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.model.StaffRank;
import de.raindancer.modules.moderation.store.StaffRoster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The roster is a view of the staff groups: who is in which, kept in step with the groups themselves. */
class StaffRosterTest {

    @TempDir
    Path folder;

    private static final UUID ALEX = UUID.randomUUID();
    private static final UUID BO = UUID.randomUUID();
    private Grants grants;
    private LocalGroups groups;
    private StaffRoster roster;

    @BeforeEach
    void setUp() {
        grants = new Grants(folder);
        groups = new LocalGroups(new YamlStore(folder.resolve("groups.yml")), grants);
        roster = new StaffRoster(groups);
        roster.seedGroups();
    }

    @Nested
    @DisplayName("the ladder")
    class Ladder {

        @Test
        @DisplayName("promoting puts them in the rank's group, and they get its permissions")
        void promoted() {
            assertThat(roster.promote(ALEX, StaffRank.MOD)).isTrue();

            assertThat(roster.rankOf(ALEX)).contains(StaffRank.MOD);
            assertThat(groups.directGroupsOf(ALEX)).containsExactly("mod");
            assertThat(grants.nodesFor(ALEX)).contains(ModerationPermission.MUTE.node(),
                    ModerationPermission.WARN.node());
        }

        @Test
        @DisplayName("a new rung replaces the old one rather than stacking on it")
        void oneRung() {
            roster.promote(ALEX, StaffRank.ADMIN);
            roster.promote(ALEX, StaffRank.SUPPORT);

            assertThat(roster.rankOf(ALEX)).contains(StaffRank.SUPPORT);
            assertThat(groups.directGroupsOf(ALEX)).containsExactly("support");
            assertThat(grants.nodesFor(ALEX)).doesNotContain(ModerationPermission.MUTE.node());
        }

        @Test
        @DisplayName("promoting to the rank they already hold changes nothing")
        void same() {
            roster.promote(ALEX, StaffRank.MOD);
            assertThat(roster.promote(ALEX, StaffRank.MOD)).isFalse();
        }

        @Test
        @DisplayName("demoting takes every staff group and every permission with it")
        void demoted() {
            roster.promote(ALEX, StaffRank.MOD);
            roster.promote(ALEX, StaffRank.BUILDER);

            assertThat(roster.demote(ALEX)).isTrue();

            assertThat(roster.isStaff(ALEX)).isFalse();
            assertThat(groups.directGroupsOf(ALEX)).isEmpty();
            assertThat(grants.nodesFor(ALEX)).isEmpty();
            assertThat(roster.demote(ALEX)).isFalse();
        }

        @Test
        @DisplayName("null players or ranks change nothing")
        void nulls() {
            assertThat(roster.promote(null, StaffRank.MOD)).isFalse();
            assertThat(roster.promote(ALEX, null)).isFalse();
            assertThat(roster.isStaff(ALEX)).isFalse();
        }
    }

    @Nested
    @DisplayName("the departments")
    class Departments {

        @Test
        @DisplayName("a department stacks on a rung, and on other departments")
        void stacks() {
            roster.promote(ALEX, StaffRank.MOD);
            roster.promote(ALEX, StaffRank.BUILDER);
            roster.promote(ALEX, StaffRank.DEV);

            assertThat(roster.rankOf(ALEX)).contains(StaffRank.MOD);
            assertThat(roster.departmentsOf(ALEX)).containsExactlyInAnyOrder(StaffRank.BUILDER, StaffRank.DEV);
        }

        @Test
        @DisplayName("somebody only in a department is staff without a rung")
        void departmentOnly() {
            roster.promote(BO, StaffRank.CREATOR);

            assertThat(roster.isStaff(BO)).isTrue();
            assertThat(roster.rankOf(BO)).isEmpty();
            assertThat(roster.ofRank(StaffRank.CREATOR)).containsExactly(BO);
        }

        @Test
        @DisplayName("one department can be taken away and the rest stays")
        void takeOne() {
            roster.promote(ALEX, StaffRank.MOD);
            roster.promote(ALEX, StaffRank.BUILDER);

            assertThat(roster.takeAway(ALEX, StaffRank.BUILDER)).isTrue();
            assertThat(roster.takeAway(ALEX, StaffRank.BUILDER)).isFalse();

            assertThat(roster.rankOf(ALEX)).contains(StaffRank.MOD);
            assertThat(roster.departmentsOf(ALEX)).isEmpty();
        }
    }

    @Nested
    @DisplayName("kept in step with the groups")
    class InStep {

        @Test
        @DisplayName("a membership added outside the roster shows up once it is refreshed")
        void outsideChange() {
            groups.join(ALEX, "admin");
            assertThat(roster.rankOf(ALEX)).isEmpty();

            roster.refresh(ALEX);

            assertThat(roster.rankOf(ALEX)).contains(StaffRank.ADMIN);
        }

        @Test
        @DisplayName("loading reads everybody from the groups, so a restart loses nobody")
        void load() {
            roster.promote(ALEX, StaffRank.MOD);
            roster.promote(BO, StaffRank.TESTER);

            StaffRoster again = new StaffRoster(groups);
            again.load();

            assertThat(again.rankOf(ALEX)).contains(StaffRank.MOD);
            assertThat(again.departmentsOf(BO)).containsExactly(StaffRank.TESTER);
            assertThat(again.everybody()).containsExactlyInAnyOrder(ALEX, BO);
        }

        @Test
        @DisplayName("seeding again never changes a group that is already there")
        void seedingIsOnce() {
            assertThat(groups.seed(StaffRank.MOD.seed())).isFalse();
        }
    }

    @Test
    @DisplayName("a dev group that lost /dev gets it back on every start; the dev keeps it")
    void devAlwaysHasDevMode() {
        LocalGroups bare = new LocalGroups(new YamlStore(folder.resolve("bare.yml")), grants);
        bare.seed(de.raindancer.core.platform.permission.GroupSeed.named("dev").granting("rains.moderation.staffchat"));
        bare.join(BO, "dev");
        assertThat(grants.nodesFor(BO)).doesNotContain(StaffRank.DEV_MODE);

        new StaffRoster(bare).seedGroups();

        assertThat(grants.nodesFor(BO)).contains(StaffRank.DEV_MODE);
        assertThat(StaffRank.DEV.mustHold()).containsExactly(StaffRank.DEV_MODE);
        assertThat(StaffRank.MOD.mustHold()).isEmpty();
    }
}
