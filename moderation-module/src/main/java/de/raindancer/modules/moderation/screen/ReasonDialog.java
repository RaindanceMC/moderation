package de.raindancer.modules.moderation.screen;

import de.raindancer.core.moderation.punishment.PunishmentKind;
import de.raindancer.core.ui.dialog.DialogAnswers;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.Reason;
import de.raindancer.modules.moderation.model.Sentence;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Choosing why. Not to save typing — to make the record countable: the same offence written eleven ways cannot be
 * counted, so nothing could tell a first offence from a fifth. Each reason says what it would cost <em>this</em>
 * player; the list runs cheapest first, so a misclick lands on the cheapest. A box underneath takes a reason of one's
 * own for anything the list does not cover — that one counts towards no ladder.
 */
public final class ReasonDialog extends ModerationDialog {

    static final String OWN = "own_reason";

    private final UUID subject;
    private final String subjectName;
    private final PunishmentKind kind;

    public ReasonDialog(ModerationServices services, Player viewer, Runnable back, UUID subject, String subjectName,
                        PunishmentKind kind) {
        super(services, viewer, back);
        this.subject = subject;
        this.subjectName = subjectName;
        this.kind = kind;
    }

    @Override
    protected String name() {
        return "Why";
    }

    @Override
    public DialogPage page() {
        List<Reason> reasons = services().reasons().forKind(kind);
        DialogPage page = titled(subjectName, CategoryDialog.verb(kind), name())
                .text("<white>Why is <bold>" + literal(subjectName) + "</bold> being "
                        + kind.past() + "? <gray>" + (reasons.isEmpty()
                        ? "Nothing in the catalogue hands out a " + kind.name().toLowerCase(Locale.ROOT) + " — type why below."
                        : "Pick a reason; each says what it would cost them.") )
                .field(OWN, "<white>…or your own reason <dark_gray>(counts towards no ladder)", "", 200);
        for (Reason reason : reasons) {
            page.button("<" + reason.severity().colour() + ">" + literal(DialogPage.shortened(reason.label(), 40)),
                    tooltip(reason), answers -> chose(reason));
        }
        page.button("<yellow>✎ Use my own reason", "<gray>What is typed in the box above.", this::typed);
        return page;
    }

    private String tooltip(Reason reason) {
        Sentence suggested = suggestion(reason);
        int priors = services().punishmentService().priorOffences(reason, subject);
        StringBuilder text = new StringBuilder("<" + reason.severity().colour() + ">" + reason.severity().describe());
        if (kind.isLasting()) {
            text.append("\n<gray>Would be <white>").append(suggested.describe()).append("</white>.");
        }
        if (reason.escalates()) {
            text.append("\n<dark_gray>").append(priors == 0 ? "Their first."
                    : "Their " + ordinal(priors + 1) + " — the ladder has been climbed.");
        }
        text.append("\n\n<dark_gray>").append(kind.isLasting() ? "Then choose how long." : "Asks first.");
        return text.toString();
    }

    /** Brought within what this viewer may hand out, so a mod sees the day they can actually give. */
    private Sentence suggestion(Reason reason) {
        Sentence suggested = services().punishmentService().suggest(reason, subject);
        return reason.kind() == PunishmentKind.BAN ? services().banLimitRule().clamp(viewer.getUniqueId(), suggested)
                : suggested;
    }

    private void typed(DialogAnswers answers) {
        Reason theirs;
        try {
            theirs = Reason.typedByHand(kind, answers.text(OWN));
        } catch (IllegalArgumentException nothingUsable) {
            page().withNotice("Type a reason in the box first.").show(viewer);
            return;
        }
        chose(theirs);
    }

    private void chose(Reason reason) {
        var verdict = canAct(subject, CategoryDialog.permission(kind));
        if (verdict.isRefused()) {
            tellRefusal(verdict);
            return;
        }
        if (kind.isLasting()) {
            new DurationDialog(services(), viewer, this::open, back, subject, subjectName, reason).open();
            return;
        }
        // A kick and a warning have no length, so the confirmation is the whole second step.
        new ConfirmScreen(services(), viewer, (Runnable) this::open, "<yellow>" + literal(reason.label()) + " — " + literal(subjectName) + "?",
                List.of("<gray>" + (kind == PunishmentKind.KICK
                        ? "They are thrown off now and may come straight back."
                        : "It goes on their record and stops nothing.")),
                () -> handOut(reason)).afterwards(back).open();
    }

    private void handOut(Reason reason) {
        var verdict = canAct(subject, CategoryDialog.permission(kind));
        if (verdict.isRefused()) {
            tellRefusal(verdict);
            return;
        }
        services().punishmentService().punish(viewer.getUniqueId(), viewer.getName(), subject, subjectName, kind,
                Sentence.forEver(), reason.label());
        tell("moderation.punished", "player", subjectName, "what", kind.past(), "length", reason.label());
    }

    /** "second", "third" — because "their 2th" is the sort of thing nobody fixes afterwards. */
    static String ordinal(int count) {
        return switch (count) {
            case 1 -> "first";
            case 2 -> "second";
            case 3 -> "third";
            case 4 -> "fourth";
            case 5 -> "fifth";
            default -> count + "th";
        };
    }

    @Override
    public String describe() {
        return "the reasons this server hands out a punishment for, cheapest first, or one's own";
    }
}
