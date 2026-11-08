package de.raindancer.modules.moderation.screen;

import de.raindancer.core.moderation.punishment.Punishment;
import de.raindancer.core.moderation.punishment.PunishmentKind;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.core.world.time.Times;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.model.Sentence;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What to do about a player: one button per kind of punishment, each saying what of that kind is in force, a fine,
 * a broken server rule, and — last, and only after asking — banning for good.
 */
public final class PlayerPunishDialog extends PlayerRoomDialog {

    public PlayerPunishDialog(ModerationServices services, Player viewer, Runnable back, UUID subject, String subjectName) {
        super(services, viewer, back, subject, subjectName);
    }

    @Override
    protected String name() {
        return "Punish";
    }

    @Override
    public DialogPage page() {
        DialogPage page = room().columns(2);
        category(page, PunishmentKind.WARNING, ModerationPermission.WARN);
        category(page, PunishmentKind.KICK, ModerationPermission.KICK);
        category(page, PunishmentKind.MUTE, ModerationPermission.MUTE);
        category(page, PunishmentKind.FREEZE, ModerationPermission.FREEZE);
        category(page, PunishmentKind.BAN, ModerationPermission.BAN);
        fine(page);
        boolean rules = !services().ruleBreaches().rules().isEmpty();
        page.button(may(ModerationPermission.WARN) && rules, "<yellow>Broke a rule",
                "<gray>Pick the rule; its next punishment is handed out.\n<dark_gray>The same everybody reads in /rules.",
                rules ? "For whoever may hand out punishments" : "This server has no rules written down",
                answers -> new RuleBreachDialog(services(), viewer, this::open, subject, subjectName).open());
        permanentBan(page);
        return page;
    }

    /** The lore carries what is in force, so "is this person already muted?" needs no click. */
    private void category(DialogPage page, PunishmentKind kind, ModerationPermission permission) {
        var inForce = services().punishments().active(subject, kind);
        String state = inForce.map(one -> "<red>Currently " + kind.past() + ": <gray>" + literal(one.reason())
                + " (" + remaining(one) + ")").orElse("<gray>Not " + kind.past() + ".");
        String label = (inForce.isPresent() ? "<red>● " : "<yellow>") + CategoryDialog.title(kind);
        page.button(may(permission), label, state + "\n\n<dark_gray>" + (kind.isLasting()
                        ? "Hand one out, or lift the one in force." : "Hand one out."),
                "You may not " + CategoryDialog.verb(kind).toLowerCase(Locale.ROOT),
                answers -> new CategoryDialog(services(), viewer, this::open, subject, subjectName, kind).open());
    }

    private void fine(DialogPage page) {
        boolean economy = services().fines().hasEconomy();
        StringBuilder tooltip = new StringBuilder("<gray>Takes money from their balance, offline or not.");
        services().fines().limitFor(viewer.getUniqueId()).ifPresent(most ->
                tooltip.append("\n<gray>You may fine up to <white>").append(Fees.format(most)).append("</white>."));
        page.button(may(ModerationPermission.FINE) && economy, "<yellow>Fine", tooltip.toString(),
                economy ? "You may not fine" : "This server has no economy",
                answers -> new FineDialog(services(), viewer, this::open, subject, subjectName).open());
    }

    /** Gone for good — greyed for a mod rather than absent, so the window says whose decision it is. */
    private void permanentBan(DialogPage page) {
        boolean allowed = may(ModerationPermission.BAN);
        page.button(allowed, "<red>Ban for ever", "<gray>Gone for good, straight away. Asks first.",
                "A permanent ban is an admin's decision — you can ban for up to "
                        + services().banLimitRule().capDescribed(),
                answers -> new ConfirmScreen(services(), viewer, (Runnable) this::open,
                        "<red>Ban " + literal(subjectName) + " for ever?",
                        List.of("<gray>They are thrown off now and cannot come back.",
                                "<gray>It goes on their record, and any admin can lift it."),
                        this::banForEver).open());
    }

    /** Asked again at the moment it happens: the window was drawn at least two clicks ago. */
    private void banForEver() {
        var verdict = canAct(subject, ModerationPermission.BAN);
        if (verdict.isRefused()) {
            tellRefusal(verdict);
            return;
        }
        var withinTheirLimit = services().banLimitRule().mayBanFor(viewer.getUniqueId(), Sentence.forEver());
        if (withinTheirLimit.isRefused()) {
            tellRefusal(withinTheirLimit);
            return;
        }
        services().punishmentService().punish(viewer.getUniqueId(), viewer.getName(), subject,
                subjectName, PunishmentKind.BAN, Sentence.forEver(), "banned permanently");
        tell("moderation.punished", "player", subjectName, "what", PunishmentKind.BAN.past(), "length", "for ever");
    }

    static String remaining(Punishment one) {
        return one.remainingAt(Instant.now()).map(left -> Times.brief(left) + " left")
                .orElse(one.isPermanent() ? "for ever" : "over");
    }

    @Override
    public String describe() {
        return "what to do about a player: warn, kick, mute, freeze, ban, fine, broke a rule, ban for ever";
    }
}
