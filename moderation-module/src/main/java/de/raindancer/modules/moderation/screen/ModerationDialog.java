package de.raindancer.modules.moderation.screen;

import de.raindancer.core.platform.rule.Verdict;
import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.core.ui.dialog.DialogReturn;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.ModerationPermission;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The base every moderation <em>dialog</em> sits on — the game's own window, like /help, for what is a decision
 * rather than a long list: what to do about somebody, why, how long, a fine, a note, a report.
 *
 * <p>The same grammar as the chest screens, kept by Core's {@link DialogPage}: a button somebody may not use is
 * shown greyed with the reason ({@code button(allowed, label, tooltip, reason, …)}) and says it when pressed; nothing
 * irreversible without {@link ConfirmScreen}; a refusal is told. The services decide, a dialog only shows and asks.
 *
 * <p>Every page is built when it opens, so coming back to one shows what changed. A list of many things stays a paged
 * chest; one opened from a dialog gets {@link #here} as its parent, so its Back comes back here.
 */
public abstract class ModerationDialog implements IModerationScreen {

    private final ModerationServices services;
    protected final Player viewer;
    /** Where Back goes; null when a command opened this and there is nowhere to go back to. */
    protected final Runnable back;

    protected ModerationDialog(ModerationServices services, Player viewer, Runnable back) {
        this.services = services;
        this.viewer = viewer;
        this.back = back;
    }

    /** This window as it is right now. */
    public abstract DialogPage page();

    @Override
    public void open() {
        UUID face = face();
        if (face == null) {
            page().show(viewer);
            return;
        }
        // The face is fetched first, so the window opens with it rather than redrawing a moment later.
        de.raindancer.core.ui.dialog.Faces.load(face).thenRun(() -> de.raindancer.core.platform.util.Scheduling
                .entity(services.plugin(), viewer, () -> {
                    if (viewer.isOnline()) {
                        page().show(viewer);
                    }
                }));
    }

    /** Whose face this window shows, if anybody's; fetched before it opens. */
    protected UUID face() {
        return null;
    }

    /** What this window is called — the last step of its title and the Back of a page opened from it. */
    protected abstract String name();

    protected ModerationServices services() {
        return services;
    }

    /** The title — the module's name in its colours, then {@code trail} — with Back when there is a way back. */
    protected DialogPage titled(String... trail) {
        DialogPage page = DialogPage.titled(heading(services.brand()), trail);
        return back == null ? page : page.back(back);
    }

    static String heading(de.raindancer.core.ui.chat.Brand brand) {
        return "<gradient:#F87171:#C084FC>" + Text.literal(brand == null ? "Moderation" : brand.tag()) + "</gradient>";
    }

    /** The parent for a chest page opened from here: its Back shows this window again. */
    protected Menu here() {
        return new DialogReturn(viewer, services.brand(), name(), this::open);
    }

    protected boolean may(ModerationPermission what) {
        return services.staffRule().may(viewer.getUniqueId(), what);
    }

    /** Whether the viewer may do this <em>to this person</em> — permission, self, and immunity. */
    protected Verdict canAct(UUID subject, ModerationPermission what) {
        return services.staffRule().canAct(viewer.getUniqueId(), subject, what);
    }

    protected void tell(String key, Object... values) {
        services.messages().send(viewer, key, values);
    }

    protected void tellRefusal(Verdict verdict) {
        verdict.refusal().ifPresent(reason -> tell(reason, "detail", verdict.detail() == null ? "" : verdict.detail()));
    }

    /** Somebody's name as text inside a MiniMessage line. */
    protected static String literal(String text) {
        return Text.literal(text);
    }
}
