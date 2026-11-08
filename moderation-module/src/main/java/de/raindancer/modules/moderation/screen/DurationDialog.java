package de.raindancer.modules.moderation.screen;

import de.raindancer.core.platform.rule.Verdict;
import de.raindancer.core.ui.dialog.DialogAnswers;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.Reason;
import de.raindancer.modules.moderation.model.Sentence;
import de.raindancer.modules.moderation.rules.BanLimitRule;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * How long. The ladder's suggestion first, marked as such, the usual lengths beside it and a box for any other — a
 * moderator who has just read the chat log knows something the ladder does not. Lengths beyond what this viewer may
 * ban for are greyed with their ceiling as the reason.
 */
public final class DurationDialog extends ModerationDialog {

    static final String TYPED = "length";

    /** The lengths somebody actually chooses, in the order they read. */
    private static final List<Duration> COMMON = List.of(
            Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(12),
            Duration.ofDays(1), Duration.ofDays(7), Duration.ofDays(30));

    private final Runnable done;
    private final UUID subject;
    private final String subjectName;
    private final Reason reason;

    /** @param done where to go once it is handed out — the page that shows it in force; null closes */
    public DurationDialog(ModerationServices services, Player viewer, Runnable back, Runnable done, UUID subject,
                          String subjectName, Reason reason) {
        super(services, viewer, back);
        this.done = done;
        this.subject = subject;
        this.subjectName = subjectName;
        this.reason = reason;
    }

    @Override
    protected String name() {
        return "How long";
    }

    @Override
    public DialogPage page() {
        Sentence suggested = services().punishmentService().suggest(reason, subject);
        DialogPage page = titled(subjectName, CategoryDialog.verb(reason.kind()), name()).columns(2)
                .text("<white>" + CategoryDialog.verb(reason.kind()) + " <bold>" + literal(subjectName)
                        + "</bold> for <" + reason.severity().colour() + ">" + literal(reason.label())
                        + "</" + reason.severity().colour() + "> — how long?")
                .field(TYPED, "<white>…or type a length <dark_gray>(90m, 3d, 2M — m minutes, M months)", "", 24);
        Verdict suggestedAllowed = allowedFor(suggested);
        page.button(suggestedAllowed.isAllowed(), "<green>★ " + suggested.describe(),
                "<gray>What this server's ladder says.\n<dark_gray>Their "
                        + (services().punishmentService().priorOffences(reason, subject) + 1) + " of this.",
                ceilingReason(), answers -> confirm(suggested));
        for (Duration length : COMMON) {
            Sentence sentence = Sentence.of(length);
            page.button(allowedFor(sentence).isAllowed(), "<yellow>" + sentence.describe(),
                    "<gray>Instead of the suggestion.", ceilingReason(), answers -> confirm(sentence));
        }
        page.button(allowedFor(Sentence.forEver()).isAllowed(), "<red>For ever", "<gray>Until somebody lifts it.",
                ceilingReason(), answers -> confirm(Sentence.forEver()));
        page.button("<yellow>✎ The length I typed", "<gray>What is in the box above.", this::typed);
        return page;
    }

    /** Only bans are capped; a mute or a freeze is the permission's business alone. */
    private Verdict allowedFor(Sentence sentence) {
        if (!BanLimitRule.appliesTo(reason.kind())) {
            return Verdict.allowed();
        }
        return services().banLimitRule().mayBanFor(viewer.getUniqueId(), sentence);
    }

    private String ceilingReason() {
        return services().banLimitRule().longestFor(viewer.getUniqueId())
                .map(most -> most.isZero() ? "You may not ban"
                        : "You may ban for up to " + services().banLimitRule().capDescribed())
                .orElse("Not allowed");
    }

    /** Empty or unreadable is a typo, never "for ever" — see Sentence. */
    private void typed(DialogAnswers answers) {
        String typed = answers.text(TYPED);
        var wanted = Sentence.parse(typed);
        if (typed.isEmpty() || wanted.isEmpty()) {
            page().withNotice(typed.isEmpty() ? "Type a length in the box first."
                    : "\"" + typed + "\" is not a length: try 90m, 3d or 2M.").show(viewer);
            return;
        }
        // Checked here too: a typed length bypasses every greyed button.
        Verdict allowed = allowedFor(wanted.get());
        if (allowed.isRefused()) {
            tellRefusal(allowed);
            return;
        }
        confirm(wanted.get());
    }

    private void confirm(Sentence sentence) {
        new ConfirmScreen(services(), viewer, (Runnable) this::open,
                "<yellow>" + literal(reason.label()) + " — " + sentence.describe() + "?",
                List.of("<gray>" + literal(subjectName) + " would be " + reason.kind().past()
                                + " <white>" + sentence.describe() + "</white>.",
                        "<gray>Reason: <white>" + literal(reason.label()) + "</white>."),
                () -> punish(sentence)).afterwards(done).open();
    }

    /** The last gate: a limit checked in one place is a limit that holds only on that path. */
    private void punish(Sentence sentence) {
        var verdict = canAct(subject, CategoryDialog.permission(reason.kind()));
        if (verdict.isRefused()) {
            tellRefusal(verdict);
            return;
        }
        var withinTheirLimit = allowedFor(sentence);
        if (withinTheirLimit.isRefused()) {
            tellRefusal(withinTheirLimit);
            return;
        }
        services().punishmentService().punish(viewer.getUniqueId(), viewer.getName(), subject, subjectName,
                reason.kind(), sentence, reason.label());
        tell("moderation.punished", "player", subjectName, "what", reason.kind().past(), "length", sentence.describe());
    }

    @Override
    public String describe() {
        return "how long a punishment is for, with the ladder's suggestion first";
    }
}
