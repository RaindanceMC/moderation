package de.raindancer.modules.moderation.service;

import de.raindancer.modules.moderation.ModerationSettings;
import de.raindancer.modules.moderation.model.Report;
import de.raindancer.modules.moderation.model.StaffNote;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * {@code records.keep-days}: what is older than that goes — punishments that stopped applying (Core decides which,
 * never one in force), closed reports, staff notes. Open reports stay whatever their age: somebody still has to
 * look at them. The records name real people, so under the GDPR they may only be kept while there is a reason.
 */
public final class RecordRetention {

    /** How many of each were dropped by one sweep. */
    public record Swept(int punishments, int reports, int notes) {

        public boolean any() {
            return punishments + reports + notes > 0;
        }
    }

    private volatile ModerationSettings settings;
    private final Supplier<Instant> clock;
    private final ToIntFunction<Instant> forgetPunishments;
    private final Supplier<? extends Collection<Report>> reports;
    private final Consumer<String> dropReport;
    private final Supplier<? extends Collection<StaffNote>> notes;
    private final Consumer<String> dropNote;

    public RecordRetention(ModerationSettings settings, Supplier<Instant> clock,
                           ToIntFunction<Instant> forgetPunishments,
                           Supplier<? extends Collection<Report>> reports, Consumer<String> dropReport,
                           Supplier<? extends Collection<StaffNote>> notes, Consumer<String> dropNote) {
        settings(settings);
        this.clock = clock;
        this.forgetPunishments = forgetPunishments;
        this.reports = reports;
        this.dropReport = dropReport;
        this.notes = notes;
        this.dropNote = dropNote;
    }

    public void settings(ModerationSettings settings) {
        this.settings = settings == null ? ModerationSettings.DEFAULTS : settings;
    }

    /** Drops what is past the retention. Off the server's threads: Core deletes from the database. */
    public Swept sweep() {
        Optional<Duration> keep = settings.recordsKeptFor();
        if (keep.isEmpty()) {
            return new Swept(0, 0, 0);
        }
        Instant cutoff = clock.get().minus(keep.get());
        int punishments = forgetPunishments.applyAsInt(cutoff);
        List<String> oldReports = reports.get().stream()
                .filter(report -> report.closedAt() != null && report.closedAt().isBefore(cutoff))
                .map(Report::id).toList();
        oldReports.forEach(dropReport);
        List<String> oldNotes = notes.get().stream()
                .filter(note -> note.at() != null && note.at().isBefore(cutoff))
                .map(StaffNote::id).toList();
        oldNotes.forEach(dropNote);
        return new Swept(punishments, oldReports.size(), oldNotes.size());
    }
}
