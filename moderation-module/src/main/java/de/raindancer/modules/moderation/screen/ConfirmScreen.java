package de.raindancer.modules.moderation.screen;

import de.raindancer.core.ui.chat.Brand;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.modules.moderation.ModerationServices;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * "Are you sure?" — the game's own question window, Yes on the left and No on the right, the consequences above.
 *
 * <p>The arrangement has to be the same everywhere or it does not work at all: left and right are a habit people
 * build, and a dialog that swaps them is one they learn to click through and then get wrong exactly once — here, on
 * the button that bans somebody.
 *
 * <h2>The one thing this page says differently</h2>
 * The closing line. A punishment is not undone by saying no — it goes on the record either way, and that is the thing
 * a moderator needs reminding of. "This cannot be undone" would be the wrong sentence here.
 */
public final class ConfirmScreen implements IModerationScreen {

    private static final String ON_THE_RECORD_EITHER_WAY = "<dark_gray>It goes on their record either way.";

    private final Brand brand;
    private final Player viewer;
    private final Runnable back;
    private final String question;
    private final List<String> consequences;
    private final String closing;
    private final Runnable onYes;
    private Runnable afterYes;

    /** From a chest page: No, and Yes when it opened nothing else, go back to it. */
    public ConfirmScreen(ModerationServices services, Player viewer, Menu parent, String question,
                         List<String> consequences, Runnable onYes) {
        this(services.brand(), viewer, parent == null ? null : parent::open, question, consequences,
                ON_THE_RECORD_EITHER_WAY, onYes);
    }

    /** From a dialog. */
    public ConfirmScreen(ModerationServices services, Player viewer, Runnable back, String question,
                         List<String> consequences, Runnable onYes) {
        this(services.brand(), viewer, back, question, consequences, ON_THE_RECORD_EITHER_WAY, onYes);
    }

    /** The same page with a closing line of its own — for what is not a punishment, such as clearing a record. */
    public ConfirmScreen(Brand brand, Player viewer, Menu parent, String question, List<String> consequences,
                         String closing, Runnable onYes) {
        this(brand, viewer, parent == null ? null : parent::open, question, consequences, closing, onYes);
    }

    private ConfirmScreen(Brand brand, Player viewer, Runnable back, String question, List<String> consequences,
                          String closing, Runnable onYes) {
        this.brand = brand;
        this.viewer = viewer;
        this.back = back;
        this.question = question;
        this.consequences = consequences == null ? List.of() : List.copyOf(consequences);
        this.closing = closing;
        this.onYes = onYes;
        this.afterYes = back;
    }

    /** Where Yes goes once it is done, when that is not back — the page that shows what it did. */
    public ConfirmScreen afterwards(Runnable where) {
        this.afterYes = where;
        return this;
    }

    public DialogPage page() {
        DialogPage asking = DialogPage.titled(ModerationDialog.heading(brand), "Are you sure?")
                .text("<bold>" + question + "</bold>");
        if (!consequences.isEmpty()) {
            asking.text(String.join("\n", consequences));
        }
        if (closing != null && !closing.isBlank()) {
            asking.text(closing);
        }
        return asking.confirm("Yes, do it", onYes, "No, leave it alone", back, afterYes);
    }

    @Override
    public void open() {
        page().show(viewer);
    }

    @Override
    public String describe() {
        return "a confirmation, so a misclick costs a window rather than the thing";
    }
}
