package de.raindancer.modules.moderation.screen;

import de.raindancer.core.moderation.invsee.Access;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.command.PromoteCommand;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.model.OverviewAccess;
import de.raindancer.modules.moderation.model.PlayerOverview;
import org.bukkit.entity.Player;

import java.util.UUID;

/** The practical things about a player, none of them a punishment: inventory, going to them, fines, tools, rank. */
public final class PlayerPracticalDialog extends PlayerRoomDialog {

    public PlayerPracticalDialog(ModerationServices services, Player viewer, Runnable back, UUID subject, String subjectName) {
        super(services, viewer, back, subject, subjectName);
    }

    @Override
    protected String name() {
        return "Practical";
    }

    @Override
    public DialogPage page() {
        PlayerOverview seen = seen();
        OverviewAccess access = access();
        boolean here = seen.where() != null;
        DialogPage page = room().columns(2);
        page.button(may(ModerationPermission.INVSEE), "<yellow>Inventory & ender chest",
                (may(ModerationPermission.INVSEE_EDIT) ? "<gray>You may change what is in it." : "<gray>Looking only.")
                        + "\n<dark_gray>Offline too.",
                "For whoever may look in inventories", answers -> lookInside());
        page.button(here && viewer.hasPermission(TELEPORT), "<yellow>Go to them",
                here && access.location() ? "<gray>" + seen.where().world() + " " + seen.where().x() + " "
                        + seen.where().y() + " " + seen.where().z()
                        : here ? "<gray>To where they stand." : "<gray>Only while they are online.",
                here ? "For whoever may teleport" : "They are not online", answers -> goTo());
        page.button(may(ModerationPermission.HISTORY), "<yellow>Fines", (access.record() ? "<gray>Owes <white>"
                        + Fees.format(services().fines().owed(subject)) + "</white>." : "<gray>What they were fined.")
                        + "\n<dark_gray>Revoke one, or forgive the debt.",
                "For whoever may read a record",
                answers -> new FinesMenu(services(), viewer, here(), subject, subjectName).open());
        // Flight, invulnerability, one-hit-kill: none is a punishment, so they live apart from them.
        page.button(may(ModerationPermission.FLY) || may(ModerationPermission.GOD) || may(ModerationPermission.INSTAKILL),
                "<yellow>Tools", "<gray>Flight, invulnerability, heal, feed, one-hit-kill.",
                "For whoever holds one of those",
                answers -> new ToolsMenu(services(), viewer, here(), subject, subjectName).open());
        // Drawn for everybody, greyed for anybody but the owner: ranks exist, and are not theirs to give.
        page.button(viewer.isOp() || viewer.hasPermission(PromoteCommand.USE), "<yellow>Staff rank",
                "<gray>" + RankMenu.titles(services().roster().ranksOf(subject)) + ".\n<dark_gray>Promote, demote, departments.",
                "Only the server owner may hand out ranks",
                answers -> new RankMenu(services(), viewer, here(), subject, subjectName).open());
        return page;
    }

    /** To where they stand, read on their thread and gone to on ours. */
    private void goTo() {
        Player target = services().server().getPlayer(subject);
        if (target == null) {
            tell("moderation.not-here", "player", subjectName);
            return;
        }
        if (!viewer.hasPermission(TELEPORT)) {
            tell("moderation.no-permission");
            return;
        }
        Scheduling.entity(services().plugin(), target, () -> {
            org.bukkit.Location at = target.getLocation();
            Scheduling.entity(services().plugin(), viewer, () -> viewer.teleportAsync(at));
        });
    }

    /** Editing needs the wider grant: taking somebody's helmet off by one slot too far is what Access prevents. */
    private void lookInside() {
        if (!may(ModerationPermission.INVSEE)) {
            tell("moderation.no-permission");
            return;
        }
        Access wanted = may(ModerationPermission.INVSEE_EDIT) ? Access.EDIT : Access.READ_ONLY;
        services().inventories().open(viewer, subject, subjectName, wanted, outcome -> {
            if (!outcome.opened()) {
                tell("moderation.invsee.refused", "reason", outcome.saying());
            }
        });
    }

    @Override
    public String describe() {
        return "the practical things about a player: inventory, going to them, fines, tools, staff rank";
    }
}
