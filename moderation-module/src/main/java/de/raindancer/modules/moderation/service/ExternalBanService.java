package de.raindancer.modules.moderation.service;

import de.raindancer.core.moderation.punishment.ExternalBans;
import de.raindancer.core.moderation.punishment.PunishmentKind;
import de.raindancer.modules.moderation.ModerationSettings;
import de.raindancer.modules.moderation.model.Sentence;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Bans and unbans asked for from outside the game (Hush mirroring a Discord ban, through Core's
 * {@link ExternalBans}), handed out like the console's own: the record, the vanilla list, the kick, the audit line
 * and the announcement all come from {@link PunishmentService}. Bans from there are for good; Discord has no lengths.
 */
public final class ExternalBanService implements ExternalBans.Banner {

    private static final long WAIT_MILLIS = 10_000;

    private final PunishmentService punishments;
    private final Executor mainThread;
    private final long waitMillis;

    /** @param mainThread where punishments are handed out; callers come from the link's own threads */
    public ExternalBanService(PunishmentService punishments, Executor mainThread) {
        this(punishments, mainThread, WAIT_MILLIS);
    }

    ExternalBanService(PunishmentService punishments, Executor mainThread, long waitMillis) {
        this.punishments = punishments;
        this.mainThread = mainThread;
        this.waitMillis = waitMillis;
    }

    @Override
    public ExternalBans.Outcome ban(UUID subject, String name, String by, String reason) {
        return onMain(() -> {
            if (punishments.isActive(subject, PunishmentKind.BAN)) {
                return ExternalBans.Outcome.ALREADY;
            }
            punishments.punish(null, who(by), subject, nameOf(subject, name), PunishmentKind.BAN, Sentence.forEver(),
                    reason == null || reason.isBlank() ? "Banned on Discord" : reason.strip());
            return ExternalBans.Outcome.DONE;
        });
    }

    @Override
    public ExternalBans.Outcome unban(UUID subject, String name, String by, String reason) {
        return onMain(() -> punishments.lift(null, who(by), subject, nameOf(subject, name), PunishmentKind.BAN,
                reason == null || reason.isBlank() ? "Unbanned on Discord" : reason.strip())
                ? ExternalBans.Outcome.DONE : ExternalBans.Outcome.ALREADY);
    }

    /** Reads none yet: whether a ban mirrors to the vanilla list is {@link PunishmentService}'s setting. */
    public void settings(ModerationSettings settings) {
    }

    private ExternalBans.Outcome onMain(Supplier<ExternalBans.Outcome> work) {
        CompletableFuture<ExternalBans.Outcome> done = new CompletableFuture<>();
        mainThread.execute(() -> {
            try {
                done.complete(work.get());
            } catch (RuntimeException failed) {
                done.complete(ExternalBans.Outcome.FAILED);
            }
        });
        try {
            return done.get(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ExternalBans.Outcome.FAILED;
        } catch (Exception late) {
            return ExternalBans.Outcome.FAILED;
        }
    }

    private static String who(String by) {
        return by == null || by.isBlank() ? "Discord" : by.strip();
    }

    private static String nameOf(UUID subject, String name) {
        return name == null || name.isBlank() ? subject.toString() : name.strip();
    }
}
