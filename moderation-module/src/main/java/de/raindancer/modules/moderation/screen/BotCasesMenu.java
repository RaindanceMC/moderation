package de.raindancer.modules.moderation.screen;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.modules.moderation.AntiCheatServices;
import de.raindancer.modules.moderation.model.SuspectCase;
import de.raindancer.modules.moderation.util.CheatPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Suspected bot accounts and macro users on the whole network, newest first, and the lockdown switch. Opened from a
 * player's profile it shows only their cases, without the switch.
 */
public final class BotCasesMenu extends PaginatedMenu<SuspectCase> implements IModerationScreen {

    private static final int SHOWN = 200;

    private final AntiCheatServices services;
    private final Menu back;
    private final List<SuspectCase> cases;
    /** Whose cases these are; null for everybody's. */
    private final UUID subject;
    private final String subjectName;

    public BotCasesMenu(AntiCheatServices services, Player viewer, Menu parent, List<SuspectCase> cases) {
        this(services, viewer, parent, cases, null, null);
    }

    private BotCasesMenu(AntiCheatServices services, Player viewer, Menu parent, List<SuspectCase> cases,
                         UUID subject, String subjectName) {
        super(viewer, services.chat().brand(), parent);
        this.services = services;
        this.back = parent;
        this.cases = List.copyOf(cases);
        this.subject = subject;
        this.subjectName = subjectName;
    }

    /** Reads the cases off the server's threads, then hands them over on the viewer's own. */
    public static void load(AntiCheatServices services, Player viewer, Consumer<List<SuspectCase>> then) {
        load(services, viewer, null, then);
    }

    private static void load(AntiCheatServices services, Player viewer, UUID subject, Consumer<List<SuspectCase>> then) {
        Scheduling.async(services.plugin(), () -> {
            List<SuspectCase> found = subject == null ? services.bots().cases().newest(SHOWN)
                    : services.bots().cases().about(subject, SHOWN);
            Scheduling.entity(services.plugin(), viewer, () -> then.accept(found));
        });
    }

    /** Everybody's cases. */
    public static void open(AntiCheatServices services, Player viewer, Menu parent) {
        load(services, viewer, null, found -> new BotCasesMenu(services, viewer, parent, found).open());
    }

    /** One player's cases, from their profile. */
    public static void openFor(AntiCheatServices services, Player viewer, Menu parent, UUID subject, String name) {
        load(services, viewer, subject, found -> new BotCasesMenu(services, viewer, parent, found, subject, name).open());
    }

    private void reload() {
        load(services, viewer, subject, fresh -> new BotCasesMenu(services, viewer, back, fresh, subject, subjectName).open());
    }

    public static String kindWord(SuspectCase.Kind kind) {
        return kind == SuspectCase.Kind.MACRO ? "macro" : "bot";
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize(subject == null
                ? "<dark_gray>Anti-cheat · <white>bots and macros</white>"
                : "<dark_gray>Bots and macros · <white>" + MiniMessage.miniMessage().escapeTags(subjectName) + "</white>");
    }

    @Override
    public String breadcrumb() {
        return "Bots and macros";
    }

    @Override
    protected List<SuspectCase> entries() {
        return cases;
    }

    @Override
    protected ItemStack emptyIcon() {
        return subject == null
                ? Icons.of(Material.LIME_DYE, "<gray>No cases", "<gray>No bot account or macro was suspected lately.")
                : Icons.of(Material.LIME_DYE, "<gray>No cases", "<gray>They were never suspected of being a bot",
                "<gray>or of using a macro, as far as cases are kept.");
    }

    @Override
    protected ItemStack icon(SuspectCase found) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Suspected " + (found.kind() == SuspectCase.Kind.MACRO ? "macro" : "bot account"));
        lore.add("<gray>" + new SimpleDateFormat("dd.MM. HH:mm", Locale.ROOT).format(new Date(found.at()))
                + (found.server().isBlank() ? "" : " <dark_gray>on <gray>" + found.server()));
        for (String part : found.detail().split("; ")) {
            lore.add("<dark_gray> · <white>" + part);
        }
        lore.add("");
        lore.add("<dark_gray>Click for their checks (when online here).");
        lore.add("<dark_gray>Dismiss the case with a right click.");
        return Icons.head(found.player(), "<yellow>" + found.name(), lore);
    }

    @Override
    protected void onClick(SuspectCase found, InventoryClickEvent event) {
        if (event.isRightClick()) {
            if (!viewer.hasPermission(CheatPermissions.MANAGE)) {
                services.messages().send(viewer, "anticheat.no-permission");
                return;
            }
            Scheduling.async(services.plugin(), () -> {
                services.bots().cases().dismiss(found.id());
                services.messages().send(viewer, "anticheat.bots-dismissed", "player", found.name());
                reload();
            });
            return;
        }
        Player online = services.server().getPlayer(found.player());
        if (online == null) {
            services.messages().send(viewer, "anticheat.bots-offline", "player", found.name());
            return;
        }
        new PlayerChecksMenu(services, viewer, this, services.tracks().of(online)).open();
    }

    @Override
    protected void render() {
        super.render();
        if (subject != null) {
            return;
        }
        var guard = services.bots().guard();
        boolean locked = guard.lockedDown();
        int minutes = services.bots().config().lockdownMinutes();
        ItemStack lockdown = locked
                ? Icons.of(Material.IRON_DOOR, "<red>Lockdown is on",
                "<gray>New accounts cannot join, on every server.",
                "<gray>" + Math.max(1, guard.lockdownLeftMillis() / 60_000) + " min left.",
                "<dark_gray>Click to end it.")
                : Icons.of(Material.OAK_DOOR, "<green>Lockdown is off",
                "<gray>Locks new accounts out of every server",
                "<gray>for " + minutes + " min. Players who played before still join.",
                "<dark_gray>Click to start it.");
        toolbar(4, viewer.hasPermission(CheatPermissions.MANAGE), lockdown, "Needs rainsanticheat.manage", click -> {
            if (!viewer.hasPermission(CheatPermissions.MANAGE)) {
                services.messages().send(viewer, "anticheat.no-permission");
                return;
            }
            if (guard.lockedDown()) {
                guard.lift(viewer.getName());
            } else {
                guard.lockdown(minutes, viewer.getName());
            }
            refresh();
        });
    }

    @Override
    protected List<String> helpLines() {
        return List.of("<gray>New accounts that acted like bots, and players",
                "<gray>whose clicks looked made by a macro, on every server.",
                "<gray>A case is a reason to look, not proof on its own.");
    }

    @Override
    public String describe() {
        return "suspected bot accounts and macro users, and the lockdown";
    }
}
