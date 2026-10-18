package de.raindancer.modules.moderation;

import de.raindancer.core.data.settings.Describe;
import de.raindancer.core.data.settings.In;
import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Settings;
import de.raindancer.core.data.settings.Title;

/** Whether staff are staff all the time, or only while they are on duty. */
@Settings(id = "moderation-duty")
public record StaffDutySettings(

        @In("moderation/staff") @Title("Staff powers only in admin mode")
        @Describe("On: support, mods and admins are ordinary players until they switch on /admin; their rank's "
                + "permissions (those given in LuckPerms with context rains:powers=on) only work in admin mode. "
                + "Staff chat and /admin itself always work. Needs LuckPerms.")
        @Key("staff.powers-need-admin-mode")
        boolean powersNeedAdminMode) {

    public static final StaffDutySettings DEFAULTS = new StaffDutySettings(false);
}
