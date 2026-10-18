package de.raindancer.modules.moderation.store;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.permission.Grants;
import de.raindancer.core.platform.permission.LocalGroups;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.model.StaffRank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The staff from before the groups — staff.yml and the nodes granted to each of them — moved into groups once. */
class LegacyStaffImportTest {

    @TempDir
    Path folder;

    private static final UUID TRIAL = UUID.fromString("00000000-0000-0000-0009-01f51b030beb");
    private static final UUID MOD = UUID.fromString("9e065f9e-8953-4fab-93f3-7df2844983b2");
    private Grants grants;
    private LocalGroups groups;
    private YamlStore legacy;

    @BeforeEach
    void setUp() throws Exception {
        grants = new Grants(folder);
        groups = new LocalGroups(new YamlStore(folder.resolve("groups.yml")), grants);
        new StaffRoster(groups).seedGroups();
        Files.writeString(folder.resolve("staff.yml"), """
                staff:
                  %s: trial_mod
                  %s: mod
                """.formatted(TRIAL, MOD));
        legacy = new YamlStore(folder.resolve("staff.yml"));
        // What the old presets had granted each of them directly.
        grants.grant(TRIAL, ModerationPermission.CHATLOG.node());
        grants.grant(MOD, ModerationPermission.TEMPBAN.node());
    }

    @Test
    @DisplayName("each person lands in their group; the old trial rank becomes support")
    void movedIntoGroups() {
        int moved = LegacyStaffImport.run(legacy, grants, groups);

        assertThat(moved).isEqualTo(2);
        assertThat(groups.directGroupsOf(TRIAL)).containsExactly(StaffRank.SUPPORT.group());
        assertThat(groups.directGroupsOf(MOD)).containsExactly(StaffRank.MOD.group());
    }

    @Test
    @DisplayName("the nodes the old preset granted directly are cleared, the group gives them now")
    void directNodesCleared() {
        LegacyStaffImport.run(legacy, grants, groups);

        // support no longer reads the chat log; that was the old trial preset
        assertThat(grants.nodesFor(TRIAL)).doesNotContain(ModerationPermission.CHATLOG.node())
                .contains(ModerationPermission.WARN.node());
        assertThat(grants.nodesFor(MOD)).contains(ModerationPermission.TEMPBAN.node());
    }

    @Test
    @DisplayName("it runs once: staff.yml is marked, not deleted, and a second run moves nobody")
    void once() throws Exception {
        LegacyStaffImport.run(legacy, grants, groups);
        groups.leave(MOD, StaffRank.MOD.group());

        assertThat(LegacyStaffImport.run(legacy, grants, groups)).isZero();
        assertThat(groups.directGroupsOf(MOD)).isEmpty();
        assertThat(Files.readString(folder.resolve("staff.yml"))).contains("moved-to-groups: true").contains("mod");
    }

    @Test
    @DisplayName("no staff.yml at all is nothing to do")
    void nothing() {
        assertThat(LegacyStaffImport.run(new YamlStore(folder.resolve("absent.yml")), grants, groups)).isZero();
    }

    @Test
    @DisplayName("staff nodes set directly on them are dropped even where Core's ledger forgot them")
    void directStaffNodesDropped() {
        LocalGroups watched = org.mockito.Mockito.spy(groups);

        LegacyStaffImport.run(legacy, grants, watched);

        org.mockito.Mockito.verify(watched).dropDirect(org.mockito.ArgumentMatchers.eq(MOD),
                org.mockito.ArgumentMatchers.argThat(nodes -> nodes.contains(ModerationPermission.TEMPBAN.node())
                        && nodes.contains(ModerationPermission.CHATLOG.node())));
    }
}
