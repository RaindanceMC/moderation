package de.raindancer.modules.moderation.service;

import de.raindancer.core.moderation.punishment.ExternalBans;
import de.raindancer.core.moderation.punishment.PunishmentKind;
import de.raindancer.modules.moderation.model.Sentence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A ban from Discord (Hush's ban sync) is a ban like any other: recorded, mirrored, kicked, announced. */
class ExternalBanServiceTest {

    private final UUID griefer = UUID.nameUUIDFromBytes("Griefer".getBytes());
    private final PunishmentService punishments = mock(PunishmentService.class);
    private final ExternalBanService service = new ExternalBanService(punishments, Runnable::run);

    @Test
    @DisplayName("a ban from Discord is handed out for good, by the console in the name of who asked, with their reason")
    void bans() {
        assertThat(service.ban(griefer, "Griefer", "Hush", "Banned on Discord by Mod: raid"))
                .isEqualTo(ExternalBans.Outcome.DONE);
        verify(punishments).punish(isNull(), eq("Hush"), eq(griefer), eq("Griefer"), eq(PunishmentKind.BAN),
                eq(Sentence.forEver()), eq("Banned on Discord by Mod: raid"));
    }

    @Test
    @DisplayName("somebody already banned is not banned twice, so their record keeps the ban they have")
    void alreadyBanned() {
        when(punishments.isActive(griefer, PunishmentKind.BAN)).thenReturn(true);
        assertThat(service.ban(griefer, "Griefer", "Hush", "raid")).isEqualTo(ExternalBans.Outcome.ALREADY);
        verify(punishments, never()).punish(any(), anyString(), any(), anyString(), any(PunishmentKind.class),
                any(Sentence.class), anyString());
    }

    @Test
    @DisplayName("an unban lifts the ban; nothing to lift is already done")
    void unbans() {
        when(punishments.lift(null, "Hush", griefer, "Griefer", PunishmentKind.BAN, "appeal")).thenReturn(true);
        assertThat(service.unban(griefer, "Griefer", "Hush", "appeal")).isEqualTo(ExternalBans.Outcome.DONE);
        assertThat(service.unban(griefer, "Griefer", "Hush", "again")).isEqualTo(ExternalBans.Outcome.ALREADY);
    }

    @Test
    @DisplayName("no reason and no name still make a readable record")
    void blanks() {
        service.ban(griefer, "", " ", "");
        verify(punishments).punish(isNull(), eq("Discord"), eq(griefer), eq(griefer.toString()),
                eq(PunishmentKind.BAN), eq(Sentence.forEver()), eq("Banned on Discord"));
    }

    @Test
    @DisplayName("the work runs where it is handed, and a main thread that never answers is a failure, not a hang")
    void mainThread() {
        Executor never = task -> { };
        ExternalBanService stuck = new ExternalBanService(punishments, never, 50);
        assertThat(stuck.ban(griefer, "Griefer", "Hush", "raid")).isEqualTo(ExternalBans.Outcome.FAILED);
        verify(punishments, never()).isActive(any(), any());
    }

    @Test
    @DisplayName("a punishment that throws is a failure, not the caller's problem")
    void throwing() {
        when(punishments.isActive(griefer, PunishmentKind.BAN)).thenThrow(new IllegalStateException("db down"));
        assertThat(service.ban(griefer, "Griefer", "Hush", "raid")).isEqualTo(ExternalBans.Outcome.FAILED);
    }
}
