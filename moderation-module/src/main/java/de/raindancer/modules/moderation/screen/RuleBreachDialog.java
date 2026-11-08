package de.raindancer.modules.moderation.screen;

import de.raindancer.core.moderation.rules.RulePenalty;
import de.raindancer.core.moderation.rules.ServerRule;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.rules.RuleBreachRule;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Which of the server's rules somebody broke. Each says what it costs them now — the next rung of the rule's own
 * ladder, from how often they broke it before — and is greyed with the reason when it is not this moderator's to
 * hand out.
 */
public final class RuleBreachDialog extends ModerationDialog {

    private final UUID subject;
    private final String subjectName;

    public RuleBreachDialog(ModerationServices services, Player viewer, Runnable back, UUID subject, String subjectName) {
        super(services, viewer, back);
        this.subject = subject;
        this.subjectName = subjectName;
    }

    @Override
    protected String name() {
        return "Broke a rule";
    }

    @Override
    public DialogPage page() {
        List<ServerRule> rules = services().ruleBreaches().rules();
        DialogPage page = titled(subjectName, name()).text(rules.isEmpty()
                ? "<gray>This server has no rules yet. <dark_gray>They are written with /rules edit."
                : "<white>Which rule did <bold>" + literal(subjectName) + "</bold> break?");
        for (ServerRule rule : rules) {
            String label = "<white>" + rule.number() + ". " + literal(DialogPage.shortened(rule.title(), 36));
            Optional<RuleBreachRule.Breach> next = services().ruleBreaches().next(subject, rule, "");
            if (next.isEmpty()) {
                page.button(false, label, "<gray>" + literal(rule.text()),
                        "No fixed punishment: use the buttons on their page", answers -> { });
                continue;
            }
            int before = services().ruleBreaches().offencesAgainst(subject, rule);
            String tooltip = "<gray>" + literal(rule.text()) + "\n\n<gray>Broken before: <white>" + before + "×"
                    + "\n<red>Now: " + next.get().penalty().describe() + " <dark_gray>("
                    + RulePenalty.ordinal(next.get().offence()) + " time)\n<dark_gray>"
                    + literal(RulePenalty.describe(rule.ladder()));
            boolean allowed = services().ruleBreaches().mayHandOut(viewer.getUniqueId(), subject, next.get()).isAllowed();
            page.button(allowed, label + " <red>→ " + next.get().penalty().describe(), tooltip, "Not yours to hand out",
                    answers -> confirm(rule));
        }
        return page;
    }

    private void confirm(ServerRule rule) {
        Optional<RuleBreachRule.Breach> next = services().ruleBreaches().next(subject, rule, "");
        if (next.isEmpty() || services().ruleBreaches().mayHandOut(viewer.getUniqueId(), subject, next.get()).isRefused()) {
            tell("moderation.no-permission");
            return;
        }
        new ConfirmScreen(services(), viewer, (Runnable) this::open,
                "<red>" + next.get().penalty().describe() + " for " + literal(subjectName) + "?",
                List.of("<gray>For breaking rule " + rule.number() + ": " + literal(rule.title()) + ".",
                        "<gray>Their " + RulePenalty.ordinal(next.get().offence()) + " time."),
                () -> services().ruleBreaches().breakRule(viewer, viewer.getUniqueId(), viewer.getName(), subject,
                        subjectName, rule, "")).afterwards(back).open();
    }

    @Override
    public String describe() {
        return "punishing somebody for breaking one of the server's rules";
    }
}
