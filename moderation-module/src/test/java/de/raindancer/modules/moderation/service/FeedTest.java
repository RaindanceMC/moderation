package de.raindancer.modules.moderation.service;

import de.raindancer.core.moderation.feed.AnticheatAlert;
import de.raindancer.core.moderation.feed.ModerationFeed;
import de.raindancer.core.moderation.feed.ReportNotice;
import de.raindancer.modules.moderation.model.CheckType;
import de.raindancer.modules.moderation.model.Report;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** What the moderation feed is told: a flag past its alert level, a report a player filed — and not one a machine did. */
class FeedTest {

    private static Player fakePlayer(UUID id, String name, String brand) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "getClientBrandName" -> brand;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> id.hashCode();
                    default -> method.getReturnType().isPrimitive()
                            ? (method.getReturnType() == boolean.class ? false : 0) : null;
                });
    }

    @Test
    @DisplayName("an alert carries who, which check, the level, detail, ping and client brand")
    void alertIsTold() {
        ModerationFeed feed = new ModerationFeed();
        List<AnticheatAlert> heard = new ArrayList<>();
        feed.onAnticheat(heard::add);
        UUID id = UUID.randomUUID();

        ViolationService.tell(feed, fakePlayer(id, "Alice", "fabric"), CheckType.values()[0], 7.5, "off by 0.3", 42);

        assertThat(heard).hasSize(1);
        AnticheatAlert alert = heard.get(0);
        assertThat(alert.player()).isEqualTo(id);
        assertThat(alert.name()).isEqualTo("Alice");
        assertThat(alert.check()).isEqualTo(CheckType.values()[0].title());
        assertThat(alert.level()).isEqualTo(7.5);
        assertThat(alert.detail()).isEqualTo("off by 0.3");
        assertThat(alert.ping()).isEqualTo(42);
        assertThat(alert.brand()).isEqualTo("fabric");
    }

    @Test
    @DisplayName("without a feed nothing happens")
    void noFeedIsFine() {
        ViolationService.tell(null, fakePlayer(UUID.randomUUID(), "Alice", null), CheckType.values()[0], 1, "", 0);
    }

    @Test
    @DisplayName("a report a player filed is told with both names and the text; one a machine filed is not")
    void reportIsTold() {
        ModerationFeed feed = new ModerationFeed();
        List<ReportNotice> heard = new ArrayList<>();
        feed.onReport(heard::add);
        UUID reporter = UUID.randomUUID();
        UUID subject = UUID.randomUUID();

        ReportService.tell(feed, Report.filed("R7", reporter, "Alice", subject, "Bob", "flying", Instant.EPOCH));
        ReportService.tell(feed, Report.filed("R8", null, null, subject, "Bob", "xray: ores", Instant.EPOCH));

        assertThat(heard).containsExactly(new ReportNotice("R7", reporter, "Alice", subject, "Bob", "flying"));
    }
}
