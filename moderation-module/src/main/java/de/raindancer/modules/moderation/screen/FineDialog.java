package de.raindancer.modules.moderation.screen;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.dialog.DialogAnswers;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.service.FineService;
import de.raindancer.modules.moderation.util.FineTalk;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Fining somebody: how much and why in one window, then the question. What they owe already and this viewer's limit
 * are said above the fields, so a fine over the limit is not typed only to be refused.
 */
public final class FineDialog extends ModerationDialog {

    static final String AMOUNT = "amount";
    static final String WHY = "why";

    private final UUID subject;
    private final String subjectName;

    public FineDialog(ModerationServices services, Player viewer, Runnable back, UUID subject, String subjectName) {
        super(services, viewer, back);
        this.subject = subject;
        this.subjectName = subjectName;
    }

    @Override
    protected String name() {
        return "Fine";
    }

    @Override
    public DialogPage page() {
        return form("", "");
    }

    private DialogPage form(String amount, String why) {
        StringBuilder about = new StringBuilder("<white>Fine <bold>" + literal(subjectName)
                + "</bold>. <gray>Charged from their balance, offline or not; what they cannot pay becomes debt.");
        Money owed = services().fines().owed(subject);
        if (owed.isPositive()) {
            about.append("\n<gray>They owe <white>").append(Fees.format(owed)).append("</white> already.");
        }
        services().fines().limitFor(viewer.getUniqueId()).ifPresent(most ->
                about.append("\n<gray>You may fine up to <white>").append(Fees.format(most)).append("</white>."));
        return titled(subjectName, name()).text(about.toString())
                .field(AMOUNT, "<white>How much? <dark_gray>(e.g. 250)", amount, 16)
                .area(WHY, "<white>What for?", why, 200, 3)
                .button("<gold>Fine them", "<gray>Asks first.", this::fine);
    }

    private void fine(DialogAnswers answers) {
        String typedAmount = answers.text(AMOUNT);
        String why = answers.text(WHY);
        var verdict = canAct(subject, ModerationPermission.FINE);
        if (verdict.isRefused()) {
            tellRefusal(verdict);
            return;
        }
        if (!services().fines().hasEconomy()) {
            tell("moderation.fine.no-economy");
            return;
        }
        Money amount = Fees.amount(typedAmount);
        if (!amount.isPositive()) {
            form(typedAmount, why).withNotice(typedAmount.isEmpty() ? "Type how much first."
                    : "\"" + typedAmount + "\" is not an amount.").show(viewer);
            return;
        }
        var limit = services().fines().limitFor(viewer.getUniqueId());
        if (limit.isPresent() && amount.isMoreThan(limit.get())) {
            form(typedAmount, why).withNotice("You may fine up to " + Fees.format(limit.get()) + ".").show(viewer);
            return;
        }
        if (why.isEmpty()) {
            form(typedAmount, why).withNotice("Say what it is for — they are told.").show(viewer);
            return;
        }
        new ConfirmScreen(services(), viewer, () -> form(typedAmount, why).show(viewer),
                "<yellow>Fine " + literal(subjectName) + " " + Fees.format(amount) + "?",
                List.of("<gray>For: <white>" + literal(why), "<gray>Charged from their balance, offline or not."),
                () -> {
                    var again = canAct(subject, ModerationPermission.FINE);
                    if (again.isRefused()) {
                        tellRefusal(again);
                        return;
                    }
                    FineService.Result result = services().fines().fine(viewer.getUniqueId(), viewer.getName(),
                            subject, subjectName, amount, why, null, FineService.Kind.FINE);
                    FineTalk.tell(services().messages(), viewer, subjectName, result);
                }).afterwards(back).open();
    }

    @Override
    public String describe() {
        return "fining somebody: how much, what for, and asking first";
    }
}
