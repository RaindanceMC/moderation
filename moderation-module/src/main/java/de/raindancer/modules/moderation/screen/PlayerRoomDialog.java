package de.raindancer.modules.moderation.screen;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.OverviewAccess;
import de.raindancer.modules.moderation.model.PlayerOverview;
import de.raindancer.modules.moderation.service.LogSearchService;
import de.raindancer.modules.moderation.service.PlayerOverviewService;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A window about one player: the hub and the three rooms off it. Each gathers the overview afresh when it opens —
 * part of it lives in databases, read off the server's threads — and starts with the same head and lines: who they
 * are, online or not, what is in force.
 */
public abstract class PlayerRoomDialog extends ModerationDialog {

    /** Vanilla's own node: going to somebody is the teleport command's power, not a moderation one. */
    static final String TELEPORT = "minecraft.command.teleport";

    protected final UUID subject;
    protected final String subjectName;
    private volatile PlayerOverview overview;

    protected PlayerRoomDialog(ModerationServices services, Player viewer, Runnable back, UUID subject, String subjectName) {
        super(services, viewer, back);
        this.subject = subject;
        this.subjectName = subjectName == null || subjectName.isBlank() ? "somebody" : subjectName;
    }

    @Override
    public void open() {
        new PlayerOverviewService(services()).gather(subject, subjectName, gathered -> Scheduling.entity(
                services().plugin(), viewer, () -> {
                    overview = gathered;
                    super.open();   // fetches the face, then shows it
                }));
    }

    /** What is known; the bare name until gathered. Set by a test that has no databases. */
    protected PlayerOverview seen() {
        PlayerOverview gathered = overview;
        return gathered != null ? gathered : PlayerOverview.of(subject, subjectName).build();
    }

    void seen(PlayerOverview gathered) {
        this.overview = gathered;
    }

    @Override
    protected UUID face() {
        return subject;
    }

    protected OverviewAccess access() {
        return OverviewAccess.of(this::may, viewer.hasPermission(TELEPORT), viewer.isOp());
    }

    /** The title — Moderation › Steve › this room — and their head with what matters about them. */
    protected DialogPage room() {
        DialogPage page = titled(subjectName, name().equals(subjectName) ? null : name());
        PlayerOverview seen = seen();
        Instant now = Instant.now();
        OverviewAccess access = access();
        List<String> lines = new ArrayList<>();
        lines.add("<white><bold>" + literal(subjectName) + "</bold>");
        lines.add(seen.presence().title());
        lines.addAll(seen.presenceLore(LogSearchService.ZONE, now, access));
        if (services().vanish().isVanished(subject)) {
            lines.add("<dark_gray>Vanished");
        }
        lines.addAll(seen.standingLore(now));
        return page.face(subject, () -> Icons.head(subject, "<white>" + subjectName), String.join("\n", lines));
    }
}
