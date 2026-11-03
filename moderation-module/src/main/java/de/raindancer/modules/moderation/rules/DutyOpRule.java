package de.raindancer.modules.moderation.rules;

/**
 * Whether somebody should be a server operator right now. With staff powers needing admin mode, op is a power like
 * any other: an operator — an admin opped for their rank, or one opped by hand before the switch — is op only while
 * in admin mode. With the switch off, admins are op if the server says so, operators still kept for duty get their op
 * back, and nobody else is touched. Nobody else is ever opped.
 */
public final class DutyOpRule implements IModerationRule {

    public enum Decision { OP, DEOP, DEOP_IF_OURS, LEAVE }

    /**
     * @param adminRank           they hold the admin rung
     * @param adminsAreOp         the server's {@code staff.admins-are-op}
     * @param onDutyOp            they are on this server's list of operators kept for duty
     * @param powersNeedAdminMode the server's {@code staff.powers-need-admin-mode}
     * @param inAdminMode         they are in admin mode now
     */
    public record Situation(boolean adminRank, boolean adminsAreOp, boolean onDutyOp, boolean powersNeedAdminMode,
                            boolean inAdminMode) {
    }

    public Decision decide(Situation now) {
        boolean byRank = now.adminsAreOp() && now.adminRank();
        if (now.powersNeedAdminMode()) {
            if (!byRank && !now.onDutyOp()) {
                return Decision.LEAVE;
            }
            return now.inAdminMode() ? Decision.OP : Decision.DEOP;
        }
        if (byRank || now.onDutyOp()) {
            // An operator kept for duty while the switch was on: the switch is off, so their op comes back.
            return Decision.OP;
        }
        return now.adminsAreOp() ? Decision.DEOP_IF_OURS : Decision.LEAVE;
    }

    @Override
    public String describe() {
        return "whether somebody should be a server operator right now, on duty or off";
    }
}
