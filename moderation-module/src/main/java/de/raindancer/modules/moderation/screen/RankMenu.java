package de.raindancer.modules.moderation.screen;

import de.raindancer.core.platform.rule.Verdict;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.moderation.ModerationServices;
import de.raindancer.modules.moderation.model.StaffRank;
import de.raindancer.modules.moderation.rules.PromotionRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Somebody's staff ranks: their rung on the ladder, and the departments they are in.
 *
 * <p>What each rank may do is not here — it is the LuckPerms group of the same name, edited in
 * {@code /lp editor}.
 */
public final class RankMenu extends ModerationScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final UUID subject;
    private final String subjectName;

    public RankMenu(ModerationServices services, Player viewer, Menu parent, UUID subject,
                    String subjectName) {
        super(services, viewer, parent);
        this.subject = subject;
        this.subjectName = subjectName;
    }

    /** "Moderator, Builder" — or "Not staff". */
    public static String titles(Set<StaffRank> ranks) {
        return ranks.isEmpty() ? "Not staff"
                : ranks.stream().sorted().map(StaffRank::title).collect(Collectors.joining(", "));
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Rank — <white>" + subjectName);
    }

    @Override
    public String breadcrumb() {
        return "Rank";
    }

    @Override
    protected void render() {
        Set<StaffRank> held = services().roster().ranksOf(subject);
        Optional<StaffRank> rung = services().roster().rankOf(subject);
        band(MenuLayout.WHO, 4, Icons.head(subject, "<white>" + subjectName,
                "<gray>" + titles(held) + "."));

        int column = 2;
        for (StaffRank rank : StaffRank.ladder()) {
            boolean theirs = rung.filter(current -> current == rank).isPresent();
            Verdict mayGive = services().promotionRule().mayPromote(viewer.getUniqueId(), subject, rank);
            band(MenuLayout.RULES, column++, mayGive.isAllowed() && !theirs,
                    Icons.of(rank.icon(), "<" + rank.colour() + ">" + rank.title(), lore(rank, theirs,
                            theirs ? "<green>Their rung now." : "<dark_gray>Click to make them "
                                    + rank.title().toLowerCase(Locale.ROOT) + ".")),
                    theirs ? "Already their rung" : whyNot(mayGive),
                    click -> confirm(rank));
        }

        column = 2;
        for (StaffRank department : StaffRank.departments()) {
            boolean in = held.contains(department);
            Verdict may = in
                    ? services().promotionRule().mayTakeAway(viewer.getUniqueId(), subject, department)
                    : services().promotionRule().mayPromote(viewer.getUniqueId(), subject, department);
            band(MenuLayout.LAND, column++, may.isAllowed(),
                    Icons.of(department.icon(), (in ? "<green>✔ " : "<gray>✘ ") + "<" + department.colour() + ">"
                            + department.title(), lore(department, in,
                            in ? "<dark_gray>Click to take it away." : "<dark_gray>Click to add it.")),
                    whyNot(may),
                    click -> change(() -> {
                        if (in) {
                            services().staff().takeAway(viewer, subject, subjectName, department);
                        } else {
                            services().staff().promote(viewer, subject, subjectName, department);
                        }
                    }));
        }

        band(MenuLayout.LAND, 7, Icons.of(Material.WRITABLE_BOOK, "<yellow>What a rank may do",
                services().roster().editable()
                        ? List.of("<gray>Each rank is the LuckPerms group", "<gray>of the same name.",
                                "<white>/lp editor</white> <gray>to change it.")
                        : List.of("<gray>Each rank is a permission group.",
                                "<red>No LuckPerms on this server:", "<gray>the defaults apply, unchangeable.")));

        if (!held.isEmpty() && services().promotionRule().mayDemote(viewer.getUniqueId(), subject).isAllowed()) {
            danger(Icons.of(Material.BARRIER, "<red>Off the staff",
                            "<gray>Takes every rank, and every permission with it.",
                            "<dark_gray>Asks first."),
                    click -> new ConfirmScreen(services(), viewer, this,
                            "<red>Take " + subjectName + " off the staff?",
                            List.of("<gray>They lose every staff rank.",
                                    "<gray>Nothing on anybody's record changes.",
                                    "<dark_gray>They can be promoted again at any time."),
                            () -> change(() -> services().staff().demote(viewer, subject, subjectName))).open());
        }
    }

    private static List<String> lore(StaffRank rank, boolean held, String action) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + rank.describe());
        lore.add("");
        lore.add(action);
        return lore;
    }

    /** The groups are in LuckPerms' storage: changed off the server thread, the screen redrawn after. */
    private void change(Runnable doing) {
        Scheduling.async(services().plugin(), () -> {
            doing.run();
            Scheduling.entity(services().plugin(), viewer, this::open);
        });
    }

    private String whyNot(Verdict verdict) {
        return verdict.refusal()
                .map(key -> switch (key) {
                    case PromotionRule.ONLY_BELOW_YOU -> "Above what you may hand out";
                    case PromotionRule.NOT_ABOVE_YOU -> "They are not below your own rank";
                    case PromotionRule.YOURSELF -> "You cannot change your own rank";
                    case PromotionRule.HANDING_OUT_IS_OFF -> "Only the server owner may hand out ranks here";
                    case PromotionRule.DEPARTMENTS_ARE_ADMINS -> "Departments are handed out by admins";
                    default -> "Not yours to give";
                })
                .orElse("Not yours to give");
    }

    private void confirm(StaffRank rank) {
        Optional<StaffRank> current = services().roster().rankOf(subject);
        boolean down = current.isPresent() && !rank.isAtLeast(current.get());
        List<String> what = new ArrayList<>();
        what.add("<gray>" + rank.describe());
        if (down) {
            what.add("<yellow>This is a step down from " + current.get().title() + " — they lose what it had.");
        }
        if (rank == StaffRank.ADMIN) {
            what.add("<red>An admin can change the settings and cannot be acted on by moderators.");
        }
        new ConfirmScreen(services(), viewer, this,
                "<yellow>Make " + subjectName + " " + rank.title().toLowerCase(Locale.ROOT) + "?",
                what,
                () -> {
                    Verdict mayGive = services().promotionRule().mayPromote(viewer.getUniqueId(), subject, rank);
                    if (mayGive.isRefused()) {
                        mayGive.refusal().ifPresent(key -> tell(key, "rank",
                                mayGive.detail() == null ? "" : mayGive.detail(), "player", subjectName));
                        return;
                    }
                    change(() -> services().staff().promote(viewer, subject, subjectName, rank));
                }).open();
    }

    @Override
    protected List<String> helpLines() {
        return List.of("<gray>Top row: the ladder. Each rung has everything the one below has.",
                "<gray>Bottom row: departments, held on their own or on top of a rung.",
                "<gray>You appoint the rung below your own; admins hand out departments.",
                "<gray>What a rank may do is edited in LuckPerms: /lp editor.");
    }

    @Override
    public String describe() {
        return "somebody's staff ranks: the ladder and the departments";
    }
}
