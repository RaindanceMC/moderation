package de.raindancer.modules.moderation.screen;

import de.raindancer.core.ui.dialog.DialogAnswers;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.core.world.time.Times;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.ModerationPermission;
import de.raindancer.modules.moderation.model.StaffNote;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the staff have written about somebody, newest first, and a box to write the next. Never shown to the player
 * it is about — which is why a note can be honest. Removing one is a window of its own and asks first; the removal is
 * audited with what the note said, so nobody quietly deletes what was written about themselves.
 */
public final class NotesDialog extends ModerationDialog {

    static final String NOTE = "note";

    private final UUID subject;
    private final String subjectName;

    public NotesDialog(ModerationServices services, Player viewer, Runnable back, UUID subject, String subjectName) {
        super(services, viewer, back);
        this.subject = subject;
        this.subjectName = subjectName;
    }

    @Override
    protected String name() {
        return "Notes";
    }

    @Override
    public DialogPage page() {
        List<StaffNote> notes = services().noteService().about(subject);
        DialogPage page = titled(subjectName, name()).columns(2);
        if (notes.isEmpty()) {
            page.text("<gray>Nothing written about <white>" + literal(subjectName) + "</white> yet.");
        } else {
            StringBuilder all = new StringBuilder();
            for (StaffNote note : notes) {
                if (!all.isEmpty()) {
                    all.append("\n\n");
                }
                all.append("<yellow>").append(literal(note.authorName())).append(" <dark_gray>")
                        .append(Times.describe(Duration.between(note.at(), Instant.now()))).append(" ago\n<white>")
                        .append(literal(note.text()));
            }
            page.text(all.toString());
        }
        page.text("<dark_gray>" + literal(subjectName) + " never sees these.");
        boolean allowed = may(ModerationPermission.NOTES);
        page.area(NOTE, "<white>A new note", "", 200, 3)
                .button(allowed, "<green>Save the note", "<gray>About <white>" + literal(subjectName) + "</white>.",
                        "For whoever may write notes", this::write)
                .button(allowed && !notes.isEmpty(), "<red>Remove one…", "<gray>Pick it, then asks first.",
                        notes.isEmpty() ? "There is nothing to remove" : "For whoever may write notes",
                        answers -> removing());
        return page;
    }

    private void write(DialogAnswers answers) {
        if (!may(ModerationPermission.NOTES)) {
            tell("moderation.no-permission");
            return;
        }
        String typed = answers.text(NOTE);
        if (typed.isEmpty()) {
            page().withNotice("Type the note in the box first.").show(viewer);
            return;
        }
        services().noteService().add(subject, subjectName, viewer.getUniqueId(), viewer.getName(), typed);
        tell("moderation.note-written", "player", subjectName);
        open();
    }

    /** Which note to remove — each as a button, its whole text on hover. */
    private void removing() {
        DialogPage page = DialogPage.titled(heading(services().brand()), subjectName, name(), "Remove")
                .back(this::open).text("<white>Which note goes? <gray>The removal is recorded, with what it said.");
        for (StaffNote note : services().noteService().about(subject)) {
            page.button(may(ModerationPermission.NOTES), "<white>" + literal(DialogPage.shortened(note.text(), 40)),
                    "<gray>" + literal(note.text()) + "\n<dark_gray>by " + literal(note.authorName()),
                    "For whoever may write notes",
                    answers -> new ConfirmScreen(services(), viewer, (Runnable) this::open, "<red>Remove this note?",
                            List.of("<gray>" + literal(note.text()),
                                    "<gray>Written by <white>" + literal(note.authorName()) + "</white>.",
                                    "<dark_gray>The removal is recorded, with what it said."),
                            () -> remove(note)).open());
        }
        page.show(viewer);
    }

    private void remove(StaffNote note) {
        if (!may(ModerationPermission.NOTES)) {
            tell("moderation.no-permission");
            return;
        }
        services().noteService().remove(note.id(), viewer.getUniqueId(), viewer.getName());
        tell("moderation.note-removed");
    }

    @Override
    public String describe() {
        return "the staff notes about somebody, which they never see, and writing the next";
    }
}
