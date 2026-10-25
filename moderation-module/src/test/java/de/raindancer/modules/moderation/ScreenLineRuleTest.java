package de.raindancer.modules.moderation;

import de.raindancer.modules.moderation.model.Standing;
import de.raindancer.modules.moderation.rules.ScreenLineRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What Moderation's screens say")
class ScreenLineRuleTest {

    private final ScreenLineRule rule = new ScreenLineRule();

    @Test
    @DisplayName("nobody on says so")
    void nobody() {
        assertThat(rule.staff(List.of())).isEqualTo("<gold><bold>Staff online</bold></gold>\n<gray>Nobody right now");
    }

    @Test
    @DisplayName("staff are listed highest rank first, then by name, in the rank's colour")
    void staff() {
        String text = rule.staff(List.of(
                new ScreenLineRule.Seen("zed", "Support", "green", 1),
                new ScreenLineRule.Seen("Bob", "Moderator", "aqua", 2),
                new ScreenLineRule.Seen("Amy", "Moderator", "aqua", 2),
                new ScreenLineRule.Seen("Boss", "Owner", "red", 4)));
        assertThat(text).isEqualTo("<gold><bold>Staff online</bold></gold>\n<red>Owner <white>Boss\n"
                + "<aqua>Moderator <white>Amy\n<aqua>Moderator <white>Bob\n<green>Support <white>zed");
    }

    @Test
    @DisplayName("ten at most, the rest counted; names cannot inject tags")
    void many() {
        var seen = IntStream.range(0, 14).mapToObj(n -> new ScreenLineRule.Seen("N" + n, "Support", "green", 1)).toList();
        String text = rule.staff(seen);
        assertThat(text.lines().count()).isEqualTo(1 + 10 + 1);
        assertThat(text).endsWith("<dark_gray>+ 4 more");
        assertThat(rule.staff(List.of(new ScreenLineRule.Seen("<red>x", "Mod", "aqua", 2)))).contains("\\<red>x");
    }

    @Test
    @DisplayName("a restriction says what, and how long is left — or that it is for good")
    void restriction() {
        assertThat(rule.restriction("muted", Duration.ofHours(2))).isEqualTo("<gold>muted <gray>· 2 hours left");
        assertThat(rule.restriction("frozen", null)).isEqualTo("<gold>frozen <gray>· until lifted");
    }

    @Test
    @DisplayName("your own standing: the word, recent warnings, restrictions, what you owe")
    void you() {
        assertThat(rule.you(Standing.GOOD, 0, List.of(), null)).isEqualTo(
                "<gold><bold>Your standing</bold></gold>\n<gray>Standing <green>in good standing\n<gray>Warnings <white>0 recently");
        assertThat(rule.you(Standing.RESTRICTED, 1, List.of("<gold>muted <gray>· 1 hour left"), "12.00 $")).isEqualTo(
                "<gold><bold>Your standing</bold></gold>\n<gray>Standing <gold>under a restriction\n"
                        + "<gray>Warnings <white>1 recently\n<gold>muted <gray>· 1 hour left\n<gray>Fines owed <red>12.00 $");
    }
}
