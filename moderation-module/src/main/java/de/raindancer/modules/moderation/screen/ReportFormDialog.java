package de.raindancer.modules.moderation.screen;

import de.raindancer.core.ui.dialog.DialogAnswers;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.ReportGroup;
import de.raindancer.modules.moderation.model.ReportPlace;
import de.raindancer.modules.moderation.model.ReportPreset;
import de.raindancer.modules.moderation.service.ReportService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The exact reason within one kind of thing, and what happened — one window, sent with one press. */
public final class ReportFormDialog extends ModerationDialog {

    static final String REASON = "reason";
    static final String WHAT = "what";

    private final UUID subject;
    private final String subjectName;
    private final ReportGroup group;

    public ReportFormDialog(ModerationServices services, Player viewer, Runnable back, UUID subject, String subjectName,
                            ReportGroup group) {
        super(services, viewer, back);
        this.subject = subject;
        this.subjectName = subjectName;
        this.group = group;
    }

    @Override
    protected String name() {
        return group.shortTitle();
    }

    @Override
    public DialogPage page() {
        return form(null, "");
    }

    DialogPage form(String picked, String what) {
        List<DialogPage.Choice> reasons = new ArrayList<>();
        for (ReportPreset preset : ReportPreset.in(group)) {
            reasons.add(new DialogPage.Choice(preset.name().toLowerCase(java.util.Locale.ROOT), preset.title(),
                    preset.name().equalsIgnoreCase(picked == null ? "" : picked)));
        }
        boolean chat = ReportPreset.in(group).stream().anyMatch(ReportPreset::chat);
        return titled("Report " + subjectName, name())
                .text("<white>Reporting <bold>" + literal(subjectName) + "</bold> for " + group.title().toLowerCase(java.util.Locale.ROOT)
                        + ". <gray>Say what happened, so the staff know where to look."
                        + (chat ? "\n<dark_gray>Staff can read the chat around it." : ""))
                .choice(REASON, "<white>Exactly what", reasons)
                .area(WHAT, "<white>What happened?", what, 200, 3)
                .button("<green>Send the report", "<gray>Off to the staff. You are told what they did.", this::send);
    }

    private void send(DialogAnswers answers) {
        ReportPreset preset = ReportPreset.in(group).stream()
                .filter(one -> one.name().equalsIgnoreCase(answers.choice(REASON))).findFirst()
                .orElse(ReportPreset.in(group).getFirst());
        String typed = answers.text(WHAT);
        // Where both stand is read now, on the reporter's own thread.
        ReportPlace reporterAt = ReportService.placeOf(viewer);
        ReportPlace subjectAt = ReportService.placeOf(services().server().getPlayer(subject));
        // The reason in front of what they typed, so the queue reads as a reason and the detail survives.
        String text = preset.title() + " — " + typed;
        services().reportService().file(viewer.getUniqueId(), viewer.getName(), subject, subjectName, text, preset,
                        reporterAt, subjectAt)
                .ifPresentOrElse(filed -> tell("moderation.report.filed", "id", filed.id(), "player", subjectName),
                        () -> tellWhyNot(text));
    }

    /** Says which rule refused it, rather than leaving a player with a window that did nothing. */
    private void tellWhyNot(String text) {
        var verdict = services().reportService().mayFile(viewer.getUniqueId(), subject, text);
        verdict.refusal().ifPresentOrElse(
                reason -> tell(reason, "detail", verdict.detail() == null ? "" : verdict.detail()),
                () -> tell("moderation.report.not-taken", "player", subjectName));
    }

    @Override
    public String describe() {
        return "the exact reason a player is reporting somebody for, and what happened";
    }
}
