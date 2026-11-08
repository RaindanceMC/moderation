package de.raindancer.modules.moderation.screen;

import de.raindancer.core.moderation.punishment.Punishment;
import de.raindancer.core.moderation.punishment.PunishmentKind;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.model.Sentence;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * One kind of punishment and everything that can be done with it: hand one out, lift the one in force, everybody
 * serving one, their record. Handing out and lifting share a window — they are the two halves of one decision.
 */
public final class CategoryDialog extends ModerationDialog {

    private final UUID subject;
    private final String subjectName;
    private final PunishmentKind kind;

    public CategoryDialog(ModerationServices services, Player viewer, Runnable back, UUID subject, String subjectName,
                          PunishmentKind kind) {
        super(services, viewer, back);
        this.subject = subject;
        this.subjectName = subjectName;
        this.kind = kind;
    }

    @Override
    protected String name() {
        return title(kind);
    }

    @Override
    public DialogPage page() {
        Optional<Punishment> inForce = services().punishments().active(subject, kind);
        String state = inForce.map(one -> "<red>Currently " + kind.past() + ".\n<gray>" + literal(one.reason())
                + "\n<gray>" + PlayerPunishDialog.remaining(one)).orElse("<green>Not " + kind.past() + ".");
        DialogPage page = titled(subjectName, name())
                .face(subject, () -> Icons.head(subject, "<white>" + subjectName), "<white><bold>" + literal(subjectName)
                        + "</bold>\n" + state);

        page.button(may(permission(kind)), "<yellow>" + verb(kind) + " " + literal(subjectName),
                "<gray>Pick a reason from the list.\n<dark_gray>" + (kind.isLasting()
                        ? "Then how long, with the ladder's suggestion first." : "Asks before it happens."),
                "You may not " + verb(kind).toLowerCase(Locale.ROOT),
                answers -> new ReasonDialog(services(), viewer, this::open, subject, subjectName, kind).open());

        if (kind.isLasting()) {
            page.button(may(permission(kind)) && inForce.isPresent(), "<green>" + lift(kind),
                    inForce.isPresent() ? "<gray>" + afterLifting(kind) + "\n<dark_gray>It stays on the record either way."
                            : "<gray>Nothing in force.",
                    inForce.isEmpty() ? "Nothing to lift" : "You may not lift this",
                    answers -> new ConfirmScreen(services(), viewer, (Runnable) this::open,
                            "<green>" + lift(kind) + " — " + literal(subjectName) + "?",
                            List.of("<gray>" + afterLifting(kind), "<dark_gray>The punishment stays on their record."),
                            this::liftIt).open());
        }

        // A long list: stays a paged chest, with Back to here.
        page.button(may(permission(kind)), "<yellow>Everybody " + kind.past(),
                "<gray>The whole list, with what each one was for.\n<dark_gray>Lift any of them from there.",
                "For whoever may lift these",
                answers -> new LiftMenu(services(), viewer, here(), kind, permission(kind)).open());
        page.button(may(ModerationPermission.HISTORY), "<yellow>Their record",
                "<gray>" + countOnRecord() + " " + kind.name().toLowerCase(Locale.ROOT) + "(s) on record.",
                "For whoever may read a record",
                answers -> new HistoryMenu(services(), viewer, here(), subject, subjectName).open());
        return page;
    }

    private void liftIt() {
        var verdict = canAct(subject, permission(kind));
        if (verdict.isRefused()) {
            tellRefusal(verdict);
            return;
        }
        services().punishmentService().lift(viewer.getUniqueId(), viewer.getName(), subject, subjectName, kind,
                "lifted from the moderation screen");
        tell("moderation.lifted", "player", subjectName, "what", kind.past());
    }

    private long countOnRecord() {
        return services().punishmentService().history(subject).stream().filter(past -> past.kind() == kind).count();
    }

    /** The lower of the two ban nodes: a mod holds it, an admin both; BanLimitRule decides the length. */
    static ModerationPermission permission(PunishmentKind kind) {
        return switch (kind) {
            case BAN -> ModerationPermission.TEMPBAN;
            case MUTE -> ModerationPermission.MUTE;
            case KICK -> ModerationPermission.KICK;
            case WARNING -> ModerationPermission.WARN;
            case FREEZE -> ModerationPermission.FREEZE;
            case FINE -> ModerationPermission.FINE;
        };
    }

    static String title(PunishmentKind kind) {
        return switch (kind) {
            case BAN -> "Bans";
            case MUTE -> "Mutes";
            case KICK -> "Kicks";
            case WARNING -> "Warnings";
            case FREEZE -> "Freezes";
            case FINE -> "Fines";
        };
    }

    static String verb(PunishmentKind kind) {
        return switch (kind) {
            case BAN -> "Ban";
            case MUTE -> "Mute";
            case KICK -> "Kick";
            case WARNING -> "Warn";
            case FREEZE -> "Freeze";
            case FINE -> "Fine";
        };
    }

    private static String lift(PunishmentKind kind) {
        return switch (kind) {
            case BAN -> "Lift the ban";
            case MUTE -> "Lift the mute";
            case FREEZE -> "Unfreeze them";
            default -> "Lift it";
        };
    }

    private static String afterLifting(PunishmentKind kind) {
        return switch (kind) {
            case BAN -> "They may come back.";
            case MUTE -> "They may talk again.";
            case FREEZE -> "They may build again.";
            default -> "It stops applying.";
        };
    }

    @Override
    protected UUID face() {
        return subject;
    }

    @Override
    public String describe() {
        return "one kind of punishment: handing it out, ending it, and what is on the record";
    }
}
