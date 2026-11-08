package de.raindancer.modules.moderation.service;

import de.raindancer.core.moderation.punishment.BlockedAttempts;
import de.raindancer.core.moderation.punishment.PunishmentKind;
import de.raindancer.core.moderation.punishment.Punishments;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.modules.moderation.ModerationSettings;
import de.raindancer.modules.moderation.rules.AltRule;
import de.raindancer.modules.moderation.store.KnownAddresses;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A word to staff when somebody joins from an address a banned account used lately, or one a crowd of accounts
 * shares. Addresses are kept only as the network's keyed hash, a month, in the anti-cheat database; nobody is
 * refused by this — staff decide.
 */
public final class AltWatch implements IModerationService {

    private final KnownAddresses addresses;
    private final byte[] key;
    private final Punishments punishments;
    private final AlertService alerts;
    private final LogChannel log;
    private final AltRule rule = new AltRule();

    public AltWatch(KnownAddresses addresses, byte[] key, Punishments punishments, AlertService alerts, LogChannel log) {
        this.addresses = addresses;
        this.key = key == null ? new byte[0] : key.clone();
        this.punishments = punishments;
        this.alerts = alerts;
        this.log = log;
    }

    @Override
    public void settings(ModerationSettings fresh) {
        // Nothing of its own to read: whether staff are told is the alerts' switch.
    }

    @Override
    public String describe() {
        return "a word to staff about an address a banned account or a crowd of accounts shares";
    }

    /** Off the server's threads: the database is read and written. */
    public void joined(UUID player, String name, String address) {
        String hash = BlockedAttempts.hashAddress(key, address);
        if (hash == null) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            addresses.record(player, name, hash, now);
            List<AltRule.Seen> others = new ArrayList<>();
            for (AltRule.Seen seen : addresses.sharing(hash)) {
                boolean banned = punishments != null && !seen.player().equals(player)
                        && punishments.isActive(seen.player(), PunishmentKind.BAN);
                others.add(new AltRule.Seen(seen.player(), seen.name(), seen.lastSeenMillis(), banned));
            }
            AltRule.Suspicion suspicion = rule.judge(player, others, now);
            if (!suspicion.bannedNames().isEmpty()) {
                alerts.staff("anticheat.alt-banned", "player", name, "names", String.join(", ", suspicion.bannedNames()));
            } else if (suspicion.accounts() >= AltRule.CROWD) {
                alerts.staff("anticheat.alt-crowd", "player", name, "count", suspicion.accounts());
            }
        } catch (RuntimeException failed) {
            log.warn(failed, "The address of {} could not be checked against the others: {}", name, failed.getMessage());
        }
    }
}
