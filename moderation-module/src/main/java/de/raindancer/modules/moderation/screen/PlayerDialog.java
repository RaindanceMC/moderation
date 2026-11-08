package de.raindancer.modules.moderation.screen;

import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.OverviewAccess;
import de.raindancer.modules.moderation.model.PlayerOverview;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * One player: who they are at a glance, and three doors — what has this person done, what do I do about it, or
 * something practical. Each door says on its face what is behind it, with the numbers that matter.
 */
public final class PlayerDialog extends PlayerRoomDialog {

    public PlayerDialog(ModerationServices services, Player viewer, Runnable back, UUID subject, String subjectName) {
        super(services, viewer, back, subject, subjectName);
    }

    @Override
    protected String name() {
        return subjectName;
    }

    @Override
    public DialogPage page() {
        PlayerOverview seen = seen();
        OverviewAccess access = access();
        StringBuilder look = new StringBuilder("<gray>Record, reports, mining evidence, attempts, chat, notes, audit.");
        if (access.record()) {
            look.append("\n<white>").append(seen.recordEntries()).append("</white> entries on the record.");
        }
        if (access.reports()) {
            look.append("\n<white>").append(seen.openReports()).append("</white> open report(s), <white>")
                    .append(seen.xray().finds()).append("</white> mining find(s).");
        }
        if (access.notes()) {
            look.append("\n<white>").append(seen.notes()).append("</white> staff note(s).");
        }
        return room()
                .button("<aqua>🔍 Look into", look.toString(),
                        answers -> new PlayerLookIntoDialog(services(), viewer, this::open, subject, subjectName).open())
                .button("<red>⚔ Punish", "<gray>Warn, kick, mute, freeze, ban, fine, broke a rule — or lift one.",
                        answers -> new PlayerPunishDialog(services(), viewer, this::open, subject, subjectName).open())
                .button("<green>⚒ Practical", (seen.where() != null ? "<green>Online now." : "<gray>Not online.")
                                + "\n<gray>Inventory, go to them, fines, tools, staff rank.",
                        answers -> new PlayerPracticalDialog(services(), viewer, this::open, subject, subjectName).open());
    }

    @Override
    public String describe() {
        return "one player at a glance, with a door each to looking into, punishing and practical things";
    }
}
