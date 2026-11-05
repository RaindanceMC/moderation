package de.raindancer.modules.moderation.service;

import de.raindancer.core.ui.screen.LeaveScreen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KickScreenTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @AfterEach
    void unframe() {
        LeaveScreen.configure(() -> "", () -> "");
    }

    @Test
    @DisplayName("a kick says so and gives the moderator's reason, in the server's join/kick frame")
    void framedWithReason() {
        LeaveScreen.configure(() -> "Lilly's SMP", () -> "");
        assertThat(plain(PunishmentService.kickScreen("spamming")))
                .isEqualTo("✦ LILLY'S SMP ✦\n━━━━━━━━━━━━━━\n\nYou were kicked\n\nReason: spamming");
    }

    @Test
    @DisplayName("no reason, no empty reason line")
    void noReason() {
        assertThat(plain(PunishmentService.kickScreen(" "))).isEqualTo("You were kicked");
    }
}
