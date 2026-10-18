package de.raindancer.modules.moderation;

import de.raindancer.core.platform.permission.GroupSeed;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.model.StaffRank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Staff ranks: the ladder support → mod → admin → owner, and the departments next to it (builder,
 * creator, dev, tester). Each is a permission group; what is tested here is what the group starts with.
 */
class StaffRankTest {

    private static final List<StaffRank> WORKING_RANKS = List.of(StaffRank.SUPPORT, StaffRank.MOD, StaffRank.ADMIN);

    private static List<String> seeded(GroupSeed seed) {
        return seed.nodes().stream().map(GroupSeed.Node::permission).toList();
    }

    @Nested
    @DisplayName("the ladder")
    class Ladder {

        @Test
        @DisplayName("support, mod, admin, owner — in the order they are climbed")
        void theLadder() {
            assertThat(StaffRank.ladder())
                    .containsExactly(StaffRank.SUPPORT, StaffRank.MOD, StaffRank.ADMIN, StaffRank.OWNER);
            assertThat(StaffRank.ladder()).allMatch(StaffRank::onLadder);
        }

        @Test
        @DisplayName("each rank contains everything the one below it has, and more")
        void eachContainsTheLast() {
            for (int i = 1; i < WORKING_RANKS.size(); i++) {
                Set<String> below = WORKING_RANKS.get(i - 1).nodes();
                Set<String> here = WORKING_RANKS.get(i).nodes();
                assertThat(here).containsAll(below);
                assertThat(here.size()).isGreaterThan(below.size());
            }
        }

        @Test
        @DisplayName("each knows the rung above and below it")
        void neighbours() {
            assertThat(StaffRank.SUPPORT.below()).isEmpty();
            assertThat(StaffRank.MOD.below()).contains(StaffRank.SUPPORT);
            assertThat(StaffRank.ADMIN.above()).contains(StaffRank.OWNER);
            assertThat(StaffRank.OWNER.above()).isEmpty();
        }

        @Test
        @DisplayName("each group inherits the one below, and seeds only what it adds")
        void groupsInherit() {
            assertThat(StaffRank.SUPPORT.seed().parents()).isEmpty();
            assertThat(StaffRank.MOD.seed().parents()).containsExactly("support");
            assertThat(StaffRank.ADMIN.seed().parents()).containsExactly("mod");
            assertThat(StaffRank.OWNER.seed().parents()).containsExactly("admin");
            assertThat(seeded(StaffRank.MOD.seed())).doesNotContain(ModerationPermission.WARN.node())
                    .contains(ModerationPermission.MUTE.node());
        }

        @Test
        @DisplayName("higher ranks weigh more in LuckPerms")
        void weights() {
            assertThat(StaffRank.MOD.seed().weight()).isGreaterThan(StaffRank.SUPPORT.seed().weight());
            assertThat(StaffRank.OWNER.seed().weight()).isGreaterThan(StaffRank.ADMIN.seed().weight());
        }
    }

    @Nested
    @DisplayName("what each rank may do")
    class Powers {

        @Test
        @DisplayName("support helps and warns, and stops nobody")
        void support() {
            assertThat(StaffRank.SUPPORT.nodes()).contains(
                    ModerationPermission.WARN.node(), ModerationPermission.HISTORY.node(),
                    ModerationPermission.STAFF_CHAT.node(), ModerationPermission.REPORTS.node());
            assertThat(StaffRank.SUPPORT.nodes()).doesNotContain(
                    ModerationPermission.MUTE.node(), ModerationPermission.KICK.node(),
                    ModerationPermission.TEMPBAN.node(), ModerationPermission.INVSEE.node());
        }

        @Test
        @DisplayName("a mod does the chat and the anticheat as well")
        void modsDoChatAndAnticheat() {
            assertThat(StaffRank.MOD.nodes()).contains(
                    ModerationPermission.MUTE.node(), ModerationPermission.CHATLOG.node(),
                    ModerationPermission.TEMPBAN.node(), StaffRank.ANTICHEAT_LOOK, StaffRank.ANTICHEAT_ALERTS,
                    StaffRank.TELEPORT);
            assertThat(StaffRank.MOD.nodes()).doesNotContain(StaffRank.ANTICHEAT_MANAGE);
        }

        @Test
        @DisplayName("only an admin changes the rules: config, permanent bans, the anticheat's checks")
        void adminUpward() {
            assertThat(StaffRank.MOD.nodes()).doesNotContain(ModerationPermission.CONFIG.node(),
                    ModerationPermission.BAN.node());
            assertThat(StaffRank.ADMIN.nodes()).contains(ModerationPermission.CONFIG.node(),
                    ModerationPermission.BAN.node(), StaffRank.ANTICHEAT_MANAGE);
        }

        @Test
        @DisplayName("the owner is an operator and adds no nodes")
        void owner() {
            assertThat(StaffRank.OWNER.isOperator()).isTrue();
            assertThat(StaffRank.values()).filteredOn(StaffRank::isOperator).containsExactly(StaffRank.OWNER);
            assertThat(seeded(StaffRank.OWNER.seed())).isEmpty();
        }

        @Test
        @DisplayName("claims: a mod administrates, the bypasses arrive with admin")
        void claims() {
            assertThat(StaffRank.MOD.nodes()).contains(StaffRank.CLAIM_ADMIN)
                    .doesNotContainAnyElementsOf(StaffRank.CLAIM_BYPASSES);
            assertThat(StaffRank.ADMIN.nodes()).containsAll(StaffRank.CLAIM_BYPASSES);
        }

        @Test
        @DisplayName("the Hunger Games: a mod runs a round, an admin owns the tournament")
        void hungerGames() {
            assertThat(StaffRank.SUPPORT.nodes()).doesNotContain(StaffRank.HUNGERGAMES_GAMEMASTER);
            assertThat(StaffRank.MOD.nodes()).contains(StaffRank.HUNGERGAMES_GAMEMASTER)
                    .doesNotContain(StaffRank.HUNGERGAMES_ADMIN);
            assertThat(StaffRank.ADMIN.nodes()).contains(StaffRank.HUNGERGAMES_ADMIN);
        }

        @Test
        @DisplayName("no rank grants a protection bypass")
        void noProtectionBypass() {
            for (StaffRank rank : StaffRank.values()) {
                assertThat(rank.nodes()).doesNotContain("hungergames.protection.bypass");
            }
        }
    }

    @Nested
    @DisplayName("the departments")
    class Departments {

        @Test
        @DisplayName("builder, creator, dev and tester stand next to the ladder, not on it")
        void offTheLadder() {
            assertThat(StaffRank.departments())
                    .containsExactly(StaffRank.BUILDER, StaffRank.CREATOR, StaffRank.DEV, StaffRank.TESTER);
            for (StaffRank department : StaffRank.departments()) {
                assertThat(department.onLadder()).isFalse();
                assertThat(department.below()).isEmpty();
                assertThat(department.above()).isEmpty();
                assertThat(department.seed().parents()).isEmpty();
                assertThat(department.isAtLeast(StaffRank.SUPPORT)).isFalse();
            }
        }

        @Test
        @DisplayName("none of them can punish anybody")
        void noPunishment() {
            for (StaffRank department : StaffRank.departments()) {
                assertThat(department.nodes()).doesNotContain(ModerationPermission.WARN.node(),
                        ModerationPermission.MUTE.node(), ModerationPermission.KICK.node(),
                        ModerationPermission.TEMPBAN.node(), ModerationPermission.BAN.node());
            }
        }

        @Test
        @DisplayName("a builder's world tools only apply in the build world")
        void builderTools() {
            GroupSeed seed = StaffRank.BUILDER.seed();
            assertThat(seed.nodes()).anySatisfy(node -> {
                assertThat(node.permission()).isEqualTo("minecraft.command.gamemode");
                assertThat(node.context()).containsEntry("world", StaffRank.BUILD_WORLD);
            });
            assertThat(StaffRank.BUILDER.nodes()).doesNotContain("minecraft.command.gamemode");
        }

        @Test
        @DisplayName("a dev gets the performance tools and maintenance")
        void dev() {
            assertThat(StaffRank.DEV.nodes()).contains("rainsperformance.inspect", "rainscore.maintenance");
        }
    }

    @Nested
    @DisplayName("reading one back")
    class Parsing {

        @Test
        @DisplayName("a rank can be named however it is typed")
        void byName() {
            assertThat(StaffRank.byName("mod")).contains(StaffRank.MOD);
            assertThat(StaffRank.byName("  Admin ")).contains(StaffRank.ADMIN);
            assertThat(StaffRank.byName("support")).contains(StaffRank.SUPPORT);
            assertThat(StaffRank.byName("builder")).contains(StaffRank.BUILDER);
            assertThat(StaffRank.byName("nonsense")).isEmpty();
            assertThat(StaffRank.byName(null)).isEmpty();
        }

        @Test
        @DisplayName("the words people type, and the old trial rank, are understood")
        void theWordsPeopleUse() {
            assertThat(StaffRank.byName("trial_mod")).contains(StaffRank.SUPPORT);
            assertThat(StaffRank.byName("trial")).contains(StaffRank.SUPPORT);
            assertThat(StaffRank.byName("helper")).contains(StaffRank.SUPPORT);
            assertThat(StaffRank.byName("moderator")).contains(StaffRank.MOD);
            assertThat(StaffRank.byName("developer")).contains(StaffRank.DEV);
            assertThat(StaffRank.byName("op")).contains(StaffRank.OWNER);
        }

        @Test
        @DisplayName("the names are what somebody would type, ladder first")
        void names() {
            assertThat(new ArrayList<>(StaffRank.names()))
                    .containsExactly("support", "mod", "admin", "owner", "builder", "creator", "dev", "tester");
        }

        @Test
        @DisplayName("each rank's group is named like the rank")
        void groupNames() {
            for (StaffRank rank : StaffRank.values()) {
                assertThat(rank.group()).isEqualTo(rank.key());
                assertThat(rank.seed().name()).isEqualTo(rank.group());
            }
        }
    }

    @Test
    @DisplayName("no node is seeded twice within one group")
    void noDuplicates() {
        for (StaffRank rank : StaffRank.values()) {
            assertThat(rank.seed().nodes()).doesNotHaveDuplicates();
        }
    }
}
