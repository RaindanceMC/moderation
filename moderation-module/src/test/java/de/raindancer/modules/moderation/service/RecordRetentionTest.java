package de.raindancer.modules.moderation.service;

import de.raindancer.modules.moderation.ModerationSettings;
import de.raindancer.modules.moderation.model.Report;
import de.raindancer.modules.moderation.model.StaffNote;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RecordRetentionTest {

    private static final Instant NOW = Instant.parse("2028-11-13T12:00:00Z");
    private static final UUID AYLA = UUID.randomUUID();
    private static final UUID BRAM = UUID.randomUUID();

    private final List<String> droppedReports = new ArrayList<>();
    private final List<String> droppedNotes = new ArrayList<>();
    private final AtomicReference<Instant> askedCore = new AtomicReference<>();

    private RecordRetention retention(int days, List<Report> reports, List<StaffNote> notes) {
        return new RecordRetention(
                ModerationSettings.DEFAULTS.withRecordsKeptDays(days),
                () -> NOW,
                cutoff -> {
                    askedCore.set(cutoff);
                    return 3;
                },
                () -> reports, droppedReports::add,
                () -> notes, droppedNotes::add);
    }

    private static Report closedReport(String id, Instant filed, Instant closed) {
        return Report.filed(id, AYLA, "Ayla", BRAM, "Bram", "griefing", filed).resolved(AYLA, "Ayla", "done", closed);
    }

    private static StaffNote note(String id, Instant at) {
        return new StaffNote(id, BRAM, AYLA, "Ayla", "watch him", at);
    }

    @Test
    @DisplayName("0 days keeps everything and asks nobody")
    void zeroKeepsForEver() {
        RecordRetention.Swept swept = retention(0,
                List.of(closedReport("R1", NOW.minus(Duration.ofDays(3000)), NOW.minus(Duration.ofDays(2999)))),
                List.of(note("N1", NOW.minus(Duration.ofDays(3000))))).sweep();

        assertThat(swept).isEqualTo(new RecordRetention.Swept(0, 0, 0));
        assertThat(askedCore.get()).isNull();
        assertThat(droppedReports).isEmpty();
        assertThat(droppedNotes).isEmpty();
    }

    @Test
    @DisplayName("punishments, closed reports and notes older than the cutoff go; Core is asked with the cutoff")
    void sweepsWhatIsOld() {
        Instant old = NOW.minus(Duration.ofDays(800));
        Instant recent = NOW.minus(Duration.ofDays(10));
        RecordRetention.Swept swept = retention(730,
                List.of(closedReport("R1", old, old),
                        closedReport("R2", old, recent),
                        Report.filed("R3", AYLA, "Ayla", BRAM, "Bram", "still open", old)),
                List.of(note("N1", old), note("N2", recent))).sweep();

        assertThat(askedCore.get()).isEqualTo(NOW.minus(Duration.ofDays(730)));
        assertThat(droppedReports).containsExactly("R1");
        assertThat(droppedNotes).containsExactly("N1");
        assertThat(swept).isEqualTo(new RecordRetention.Swept(3, 1, 1));
    }

    @Test
    @DisplayName("a report nobody closed is never swept, however old")
    void openReportsStay() {
        retention(30, List.of(Report.filed("R1", AYLA, "Ayla", BRAM, "Bram", "x", NOW.minus(Duration.ofDays(400)))),
                List.of()).sweep();
        assertThat(droppedReports).isEmpty();
    }
}
