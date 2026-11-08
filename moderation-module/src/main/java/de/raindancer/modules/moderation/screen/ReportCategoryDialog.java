package de.raindancer.modules.moderation.screen;

import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.ReportGroup;
import de.raindancer.modules.moderation.model.ReportPreset;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a player is reporting somebody for — the kind of thing first, then the exact reason and what happened. The
 * commonest useless report is the one-word one; asking for a reason turns it into a reason plus a sentence, and tells
 * staff how bad it looks before they read a word. These are reasons, not verdicts: a moderator decides.
 * {@code /report <player> <anything>} bypasses this entirely and always will.
 */
public final class ReportCategoryDialog extends ModerationDialog {

    private final UUID subject;
    private final String subjectName;

    public ReportCategoryDialog(ModerationServices services, Player viewer, Runnable back, UUID subject,
                                String subjectName) {
        super(services, viewer, back);
        this.subject = subject;
        this.subjectName = subjectName;
    }

    @Override
    protected String name() {
        return "Report " + subjectName;
    }

    @Override
    public DialogPage page() {
        DialogPage page = titled(name()).columns(2)
                .face(subject, () -> Icons.head(subject, "<white>" + subjectName), "<white>You are reporting <bold>"
                        + literal(subjectName) + "</bold>.\n<gray>Pick what fits best, then say what happened. "
                        + "A moderator decides what to do — a report is not a punishment.");
        for (ReportGroup group : ReportGroup.values()) {
            String inIt = ReportPreset.in(group).stream().map(preset -> "<gray>· " + preset.title())
                    .collect(Collectors.joining("\n"));
            page.button("<yellow>" + group.title(), inIt,
                    answers -> new ReportFormDialog(services(), viewer, this::open, subject, subjectName, group).open());
        }
        return page;
    }

    @Override
    protected UUID face() {
        return subject;
    }

    @Override
    public String describe() {
        return "what a player is reporting somebody for, first the kind of thing";
    }
}
