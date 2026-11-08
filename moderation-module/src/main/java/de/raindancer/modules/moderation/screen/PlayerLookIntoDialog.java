package de.raindancer.modules.moderation.screen;

import de.raindancer.core.moderation.players.CheatRecords;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.model.OverviewAccess;
import de.raindancer.modules.moderation.model.PlayerOverview;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What a player has done: their record, reports, mining evidence, the anti-cheat, attempts while punished, chat,
 * notes and the audit trail. Each is a long list and stays a paged chest; Back on it comes back here.
 */
public final class PlayerLookIntoDialog extends PlayerRoomDialog {

    /** The anti-cheat's own command node: its screens are its to guard. */
    static final String ANTICHEAT = "rainsanticheat.command";

    public PlayerLookIntoDialog(ModerationServices services, Player viewer, Runnable back, UUID subject, String subjectName) {
        super(services, viewer, back, subject, subjectName);
    }

    @Override
    protected String name() {
        return "Look into";
    }

    @Override
    public DialogPage page() {
        PlayerOverview seen = seen();
        OverviewAccess access = access();
        DialogPage page = room().columns(2);
        page.button(may(ModerationPermission.HISTORY), "<yellow>Record", (access.record() ? "<gray>" + seen.recordEntries()
                        + " entries, every kind." : "<gray>Every kind of entry.") + "\n<dark_gray>Newest first.",
                "For whoever may read a record",
                answers -> new HistoryMenu(services(), viewer, here(), subject, subjectName).open());
        page.button(may(ModerationPermission.HISTORY), "<yellow>Audit trail",
                "<gray>Joins, teleports, gamemode, every moderation action.", "For whoever may read a record",
                answers -> new AuditMenu(services(), viewer, here(), subject, subjectName).open());
        page.button(may(ModerationPermission.REPORTS), "<yellow>Reports", access.reports() ? "<gray>" + seen.openReports()
                        + " open of " + seen.reports() + " about them." : "<gray>Reports about them.",
                "For whoever may read reports",
                answers -> new ReportListMenu(services(), viewer, here(), ReportListMenu.Filter.INVOLVING, subject,
                        subjectName).open());
        page.button(may(ModerationPermission.REPORTS), "<yellow>Mining evidence", (access.reports() ? "<gray>"
                        + seen.xray().finds() + " vein(s) and bait(s) on record." : "<gray>Veins and baits on record.")
                        + "\n<dark_gray>The x-ray tests, and where and when each find was.",
                "For whoever may read reports",
                answers -> new XrayReviewMenu(services(), viewer, here(), subject, subjectName).open());
        if (CheatRecords.available()) {
            boolean allowed = viewer.hasPermission(ANTICHEAT);
            page.button(allowed, "<yellow>Anti-cheat", allowed ? antiCheatSummary(subject) : "<gray>What the checks saw of them.",
                    "For whoever may use the anti-cheat", answers -> openAntiCheat(viewer, here(), subject, subjectName));
        } else {
            page.button(false, "<gray>Anti-cheat", "<gray>What the checks saw of them.", "None on this server", answers -> { });
        }
        page.button(may(ModerationPermission.ATTEMPTS), "<yellow>Attempts while punished", (access.attempts()
                        ? "<gray>" + seen.attempts() + " kept: joins, chat, building." : "<gray>Joins, chat, building while punished.")
                        + "\n<dark_gray>/attempts searches everybody's.",
                "For whoever may see attempts",
                answers -> new AttemptsMenu(services(), viewer, here(), subject, subjectName).open());
        page.button(may(ModerationPermission.CHATLOG), "<yellow>Chat", "<gray>Their public chat, newest first, and a search.",
                "For whoever may read the chat log",
                answers -> new ChatLogMenu(services(), viewer, here(), subject, subjectName, "").open());
        page.button(may(ModerationPermission.NOTES), "<yellow>Staff notes", (access.notes() ? "<gray>" + seen.notes()
                        + " written." : "<gray>What staff wrote.") + "\n<dark_gray>They never see these.",
                "For whoever may read the notes",
                answers -> new NotesDialog(services(), viewer, this::open, subject, subjectName).open());
        return page;
    }

    /**
     * Their page in the anti-cheat, as a page under this one. Somebody not on this server has no live levels here;
     * their evidence log answers instead.
     */
    static void openAntiCheat(Player viewer, Menu parent, UUID subject, String subjectName) {
        var live = de.raindancer.modules.moderation.AntiCheatCommands.running();
        Player online = viewer.getServer().getPlayer(subject);
        if (live.isPresent() && online != null) {
            new PlayerChecksMenu(live.get(), viewer, parent, live.get().tracks().of(online)).open();
        } else {
            viewer.closeInventory();
            viewer.closeDialog();
            viewer.performCommand("anticheat log " + subjectName);
        }
    }

    /** The anti-cheat's summary of somebody, as lines — shared with the report card and the queue. */
    static List<String> antiCheatLore(UUID subject) {
        return CheatRecords.of(subject).map(record -> {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Level <white>" + String.format(Locale.ROOT, "%.1f", record.level()) + "</white>"
                    + (record.worstCheck() == null ? "" : ", mostly <white>" + record.worstCheck() + "</white>")
                    + " <dark_gray>(" + record.flaggedChecks() + " check(s))");
            if (record.lastCheck() != null) {
                lore.add("<gray>Last flag: <white>" + record.lastCheck() + "</white>, "
                        + ReportListMenu.ago(java.time.Instant.ofEpochMilli(record.lastAtMillis())));
            }
            lore.add("<gray>" + record.replays() + " replay(s) kept.");
            lore.add("");
            lore.add("<dark_gray>Click: their checks, log and replays.");
            return lore;
        }).orElse(List.of("<green>Nothing on record.", "", "<dark_gray>Click: their checks."));
    }

    static String antiCheatSummary(UUID subject) {
        return String.join("\n", antiCheatLore(subject));
    }

    @Override
    public String describe() {
        return "what a player has done: record, reports, mining evidence, attempts, chat, notes, audit";
    }
}
