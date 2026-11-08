package de.raindancer.modules.moderation.screen;

import de.raindancer.core.moderation.players.CheatRecords;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.dialog.DialogAnswers;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.model.Report;
import de.raindancer.modules.moderation.model.ReportGroup;
import de.raindancer.modules.moderation.model.ReportPlace;
import de.raindancer.modules.moderation.model.ReportPreset;
import de.raindancer.modules.moderation.model.ReportSeverity;
import de.raindancer.modules.moderation.rules.ReportSeverityRule;
import de.raindancer.modules.moderation.service.LogSearchService;
import de.raindancer.modules.moderation.visual.XrayReplay;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One report, and everything needed to decide it without leaving the window: how bad it looks, who it is about and
 * who filed it, the whole text, when and where it stands; their page, the chat around it, their other reports and
 * the evidence; where both stood; and the decision. The outcome is a sentence typed into the box — it is what the
 * player who filed it is told, and "resolved" tells them nothing.
 */
public final class ReportDialog extends ModerationDialog {

    static final String OUTCOME = "outcome";
    private static final ReportSeverityRule SEVERITY = new ReportSeverityRule();
    private static final DateTimeFormatter TO_THE_SECOND = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.ROOT)
            .withZone(LogSearchService.ZONE);

    private final String reportId;

    public ReportDialog(ModerationServices services, Player viewer, Runnable back, String reportId) {
        super(services, viewer, back);
        this.reportId = reportId;
    }

    /** Asked here, not only by the door that led here: a report link in chat or a typed id reaches this too. */
    @Override
    public void open() {
        if (!may(ModerationPermission.REPORTS)) {
            tell("moderation.no-permission");
            return;
        }
        super.open();
    }

    @Override
    protected String name() {
        return "Report " + reportId;
    }

    @Override
    public DialogPage page() {
        Optional<Report> found = services().reports().byId(reportId);
        if (found.isEmpty()) {
            return titled("Reports", name()).text("<gray>This report is gone. Somebody removed it, or the file was edited.");
        }
        Report report = found.get();
        List<Report> all = services().reports().all();
        ReportSeverity severity = SEVERITY.judge(report, all);
        ReportPreset preset = ReportPreset.of(report);
        List<Report> aboutThem = all.stream().filter(other -> other.subject().equals(report.subject())).toList();
        boolean allowed = may(ModerationPermission.REPORTS);

        StringBuilder head = new StringBuilder();
        head.append(severity.colour()).append("<bold>").append(severity.title()).append("</bold> ")
                .append(severity.bar()).append("\n<yellow>").append(preset.title())
                .append(preset.title().equals(preset.group().title()) ? "" : " <gray>· " + preset.group().title()).append("\n<gray>About <white>").append(literal(report.subjectName()))
                .append("</white> <dark_gray>(").append(aboutThem.size()).append(" report(s) about them)")
                .append("\n<gray>Filed by <white>").append(report.reporterId().isPresent()
                        ? literal(report.reporterName()) : "the server (a check)").append("</white>");
        StringBuilder when = new StringBuilder("<gray>Filed " + TO_THE_SECOND.format(report.at()) + " (Berlin), "
                + ReportListMenu.ago(report.at()) + ".\n<gray>" + report.state().describe()
                + (report.handlerId().isPresent() ? " by <white>" + literal(report.handlerName()) + "</white>" : "") + ".");
        if (report.isClosed()) {
            when.append("\n<gray>Closed ").append(TO_THE_SECOND.format(report.closedAt())).append(": <white>“")
                    .append(literal(report.outcome())).append("”");
        }
        DialogPage page = titled("Reports", name()).columns(2)
                .face(report.subject(), () -> Icons.head(report.subject(), "<white>" + report.subjectName()), head.toString())
                .text("<yellow>What they wrote:\n<white>" + literal(report.text()))
                .text(when.toString());

        page.button(allowed, "<aqua>Their page", "<gray>Everything about " + literal(report.subjectName())
                        + ": record, punish, practical.", "For whoever may read reports",
                answers -> new PlayerDialog(services(), viewer, this::open, report.subject(), report.subjectName()).open());
        page.button(may(ModerationPermission.CHATLOG), (preset.chat() ? "<gold>★ " : "<aqua>") + "Chat around it",
                "<gray>Everybody's lines from ten minutes before to three minutes after it was filed."
                        + (preset.chat() ? "\n<gold>This report is about chat." : ""),
                "For whoever may read the chat log",
                answers -> new ReportChatMenu(services(), viewer, here(), report).open());
        page.button(allowed, "<aqua>Other reports about them", "<gray>" + (aboutThem.size() - 1)
                        + " besides this one, closed ones too.", "For whoever may read reports",
                answers -> new ReportListMenu(services(), viewer, here(), ReportListMenu.Filter.INVOLVING,
                        report.subject(), report.subjectName()).open());
        evidence(page, report, preset, allowed);
        boolean teleport = viewer.hasPermission(PlayerRoomDialog.TELEPORT);
        place(page, report.reporterAt(), "Where it was reported", teleport);
        place(page, report.subjectAt(), "Where they were", teleport);
        boolean online = services().server().getPlayer(report.subject()) != null;
        page.button(online && teleport, "<green>Go to them now", online ? "<green>Online." : "<gray>Not online.",
                online ? "For whoever may teleport" : "They are not online", answers -> goToThem(report));

        if (report.isClosed()) {
            return page;
        }
        page.area(OUTCOME, "<white>What did you do, or why is there nothing to do? <dark_gray>("
                        + literal(report.reporterName()) + " is told)", "", 200, 2)
                .button(allowed, report.isWaiting() ? "<yellow>Pick it up" : "<yellow>Hand it back",
                        report.isWaiting() ? "<gray>Tells the other staff you are on it."
                                : "<gray>" + literal(report.handlerName()) + " has it. Puts it back in the queue.",
                        "For whoever may read reports", answers -> pickUp(report))
                .button(allowed, "<red>Punish them", "<gray>Warn, kick, mute, ban " + literal(report.subjectName())
                                + ".\n<dark_gray>Close the report afterwards.", "For whoever may read reports",
                        answers -> new PlayerPunishDialog(services(), viewer, this::open, report.subject(),
                                report.subjectName()).open())
                .button(allowed, "<green>✔ Dealt with", "<gray>Something was done about it — say what in the box.",
                        "For whoever may read reports", answers -> close(answers, true))
                .button(allowed, "<gray>✘ Nothing in it", "<gray>Looked at, nothing to do — say why in the box.",
                        "For whoever may read reports", answers -> close(answers, false));
        return page;
    }

    /** The evidence that fits what was reported: the mining record for x-ray, the anti-cheat for other cheating. */
    private void evidence(DialogPage page, Report report, ReportPreset preset, boolean allowed) {
        if (preset == ReportPreset.XRAY) {
            var ledger = services().xrayDetection().ledgerOf(report.subject());
            page.button(allowed && ledger != null, "<gold>★ Show their tunnels",
                    "<gray>Draws their digging for 30 seconds, only you see it: grey rock, <aqua>aqua</aqua> veins, "
                            + "<red>red</red> bait ores only an x-ray shows.",
                    ledger == null ? "Nothing of their digging is recorded on this server" : "For whoever may read reports",
                    answers -> XrayReplay.goAndShow(services().plugin(), viewer, ledger,
                            viewer.hasPermission(PlayerRoomDialog.TELEPORT), drawn -> tell(drawn > 0
                                    ? "moderation.xray.replay-shown" : "moderation.xray.replay-empty",
                                    "player", report.subjectName(), "count", drawn)));
            page.button(allowed, "<aqua>Mining evidence", "<gray>Veins and bait ores, where and when.",
                    "For whoever may read reports",
                    answers -> new XrayReviewMenu(services(), viewer, here(), report.subject(), report.subjectName()).open());
            return;
        }
        if (preset.group() == ReportGroup.CHEATING && CheatRecords.available()) {
            boolean mayUse = viewer.hasPermission(PlayerLookIntoDialog.ANTICHEAT);
            page.button(mayUse, "<aqua>Anti-cheat", mayUse ? PlayerLookIntoDialog.antiCheatSummary(report.subject())
                            : "<gray>What the checks saw of them.", "For whoever may use the anti-cheat",
                    answers -> PlayerLookIntoDialog.openAntiCheat(viewer, here(), report.subject(), report.subjectName()));
            return;
        }
        page.button(may(ModerationPermission.HISTORY), "<aqua>Their record", "<gray>Every warning, mute and ban.",
                "For whoever may read a record",
                answers -> new HistoryMenu(services(), viewer, here(), report.subject(), report.subjectName()).open());
    }

    private void place(DialogPage page, ReportPlace where, String title, boolean teleport) {
        if (where == null) {
            page.button(false, "<gray>" + title, "<gray>Not known for this report.", "Not known for this report",
                    answers -> { });
            return;
        }
        page.button(teleport, "<green>" + title, "<white>" + literal(where.describe()) + "\n<dark_gray>Takes you there.",
                "For whoever may teleport", answers -> {
                    World world = services().server().getWorld(where.world());
                    if (world == null) {
                        tell("moderation.report.world-gone", "world", where.world());
                        return;
                    }
                    viewer.teleportAsync(new Location(world, where.x() + 0.5, where.y(), where.z() + 0.5));
                });
    }

    private void goToThem(Report report) {
        Player target = services().server().getPlayer(report.subject());
        if (target == null) {
            tell("moderation.not-here", "player", report.subjectName());
            return;
        }
        Scheduling.entity(services().plugin(), target, () -> {
            Location at = target.getLocation();
            Scheduling.entity(services().plugin(), viewer, () -> viewer.teleportAsync(at));
        });
    }

    private void pickUp(Report report) {
        if (!may(ModerationPermission.REPORTS)) {
            tell("moderation.no-permission");
            return;
        }
        if (report.isWaiting()) {
            services().reportService().claim(reportId, viewer.getUniqueId(), viewer.getName());
        } else {
            services().reportService().release(reportId);
        }
        open();
    }

    /** Closes it with the typed sentence, which the reporter is shown. */
    private void close(DialogAnswers answers, boolean dealtWith) {
        if (!may(ModerationPermission.REPORTS)) {
            tell("moderation.no-permission");
            return;
        }
        String typed = answers.text(OUTCOME);
        if (typed.isEmpty()) {
            page().withNotice(dealtWith ? "Say in the box what you did — they are told."
                    : "Say in the box why there is nothing to do — they are told.").show(viewer);
            return;
        }
        boolean closed = dealtWith
                ? services().reportService().resolve(reportId, viewer.getUniqueId(), viewer.getName(), typed)
                : services().reportService().reject(reportId, viewer.getUniqueId(), viewer.getName(), typed);
        tell(closed ? "moderation.report.closed" : "moderation.report.already-closed", "id", reportId);
        if (back != null) {
            back.run();
        }
    }

    @Override
    protected java.util.UUID face() {
        return services().reports().byId(reportId).map(Report::subject).orElse(null);
    }

    @Override
    public String describe() {
        return "one report: how bad, what, when, by whom, the evidence, the places, and the decision";
    }
}
